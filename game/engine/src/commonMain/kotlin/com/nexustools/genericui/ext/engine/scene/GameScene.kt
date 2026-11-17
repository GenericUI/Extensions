package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.engine.animation.AnimationScheduler
import com.nexustools.genericui.ext.engine.animation.VBlankAnimationDriver
import com.nexustools.genericui.ext.gamepad.GamepadManager
import kotlin.concurrent.Volatile

/**
 * Container for entities, subsystems, input maps, VBlank animations, and simulation clocks.
 */
class GameScene(
    val name: String = "GameScene"
) {
    val clock: StageClock = StageClock()
    val gamepadManager: GamepadManager = GamepadManager()
    val input: GameInputState = GameInputState(gamepadManager)
    val actionMap: InputActionMap = InputActionMap()
    val animations: AnimationScheduler = AnimationScheduler()
    val vblankDriver: VBlankAnimationDriver = VBlankAnimationDriver(animations)
    val events: EventChannel<Any> = EventChannel()

    private val _rootEntities = mutableListOf<SceneEntity>()
    @Volatile
    private var rootSnapshot: List<SceneEntity> = emptyList()
    val rootEntities: List<SceneEntity>
        get() = rootSnapshot

    private val _subsystems = mutableListOf<WorldSubsystem>()
    @Volatile
    private var subsystemSnapshot: List<WorldSubsystem> = emptyList()
    val subsystems: List<WorldSubsystem>
        get() = subsystemSnapshot

    @Volatile
    private var flatEntitySnapshot: List<SceneEntity> = emptyList()

    @Volatile
    private var hierarchyDirty: Boolean = false

    init {
        vblankDriver.addPostEvaluateListener { frameIndex, deltaSeconds, timestampMs ->
            dispatchRenderVBlankCallbacks(frameIndex, deltaSeconds, timestampMs)
        }
    }

    internal fun markHierarchyDirty() {
        hierarchyDirty = true
    }

    private fun refreshEntitySnapshotIfDirty(): List<SceneEntity> {
        if (!hierarchyDirty) return flatEntitySnapshot
        val list = mutableListOf<SceneEntity>()
        fun walk(entity: SceneEntity) {
            list.add(entity)
            for (child in entity.children) {
                walk(child)
            }
        }
        for (root in rootSnapshot) {
            walk(root)
        }
        flatEntitySnapshot = list
        hierarchyDirty = false
        return list
    }

    fun allEntities(): List<SceneEntity> = refreshEntitySnapshotIfDirty()

    fun createEntity(
        name: String = "Entity",
        tag: String = "Default",
        parent: SceneEntity? = null
    ): SceneEntity {
        val entity = SceneEntity(name = name).apply {
            tagAndLayer.tag = tag
        }
        if (parent != null) {
            parent.addChild(entity)
            if (parent.scene == null) {
                addEntity(parent)
            }
        } else {
            addEntity(entity)
        }
        return entity
    }

    fun addEntity(entity: SceneEntity): SceneEntity {
        if (entity.parent != null) {
            entity.parent?.removeChild(entity)
        }
        if (entity !in _rootEntities) {
            _rootEntities.add(entity)
            rootSnapshot = _rootEntities.toList()
        }
        entity.assignSceneRecursive(this)
        hierarchyDirty = true
        refreshEntitySnapshotIfDirty()
        return entity
    }

    fun removeEntity(entity: SceneEntity): Boolean {
        val parent = entity.parent
        val removed = if (parent != null) {
            parent.removeChild(entity)
        } else {
            val ok = _rootEntities.remove(entity)
            if (ok) {
                rootSnapshot = _rootEntities.toList()
                entity.assignSceneRecursive(null)
            }
            ok
        }
        if (removed) {
            hierarchyDirty = true
            refreshEntitySnapshotIfDirty()
        }
        return removed
    }

    fun findEntityByName(targetName: String): SceneEntity? =
        allEntities().firstOrNull { it.name == targetName }

    fun findEntitiesByTag(tag: String): List<SceneEntity> =
        allEntities().filter { it.tagAndLayer.tag == tag }

    fun findEntitiesByLayerMask(mask: Int): List<SceneEntity> =
        allEntities().filter { it.tagAndLayer.matchesMask(mask) }

    fun <T : WorldSubsystem> registerSubsystem(subsystem: T): T {
        if (subsystem !in _subsystems) {
            _subsystems.add(subsystem)
            subsystemSnapshot = _subsystems.toList()
            subsystem.onAttach(this)
        }
        return subsystem
    }

    fun unregisterSubsystem(subsystem: WorldSubsystem): Boolean {
        if (!_subsystems.remove(subsystem)) return false
        subsystemSnapshot = _subsystems.toList()
        subsystem.onDetach(this)
        return true
    }

    inline fun <reified T : WorldSubsystem> getSubsystem(): T? {
        for (sub in subsystems) {
            if (sub is T) return sub
        }
        return null
    }

    /**
     * Advances game logic by one tick on the dedicated Game Logic Tick Thread.
     */
    fun stepLogicTick(rawDeltaSeconds: Float, fixedAccumulator: FixedStepAccumulator) {
        if (clock.paused) {
            input.beginNextTick()
            return
        }

        gamepadManager.poll()

        val scaledDelta = (rawDeltaSeconds * clock.timeScale).coerceAtLeast(0f)
        clock.advanceTick(scaledDelta, fixedAccumulator.fixedStepSeconds)

        val entities = refreshEntitySnapshotIfDirty()
        val subs = subsystemSnapshot

        // 1. Initialize unstarted behaviors
        for (entity in entities) {
            if (!entity.isActive) continue
            for (module in entity.modules) {
                if (module is EntityBehavior && module.isEnabled && !module.hasStarted) {
                    module.hasStarted = true
                    module.onStart()
                }
            }
        }

        // 2. Fixed-step simulation ticks
        fixedAccumulator.accumulateAndStep(scaledDelta) { fixedDt ->
            clock.incrementFixedStep()
            for (sub in subs) {
                sub.onFixedStep(fixedDt)
            }
            for (entity in entities) {
                if (!entity.isActive) continue
                for (module in entity.modules) {
                    if (module is EntityBehavior && module.isEnabled) {
                        module.onFixedStep(fixedDt)
                    }
                }
            }
        }

        // 3. Per-tick variable updates
        for (sub in subs) {
            sub.onTick(scaledDelta)
        }
        for (entity in entities) {
            if (!entity.isActive) continue
            for (module in entity.modules) {
                if (module is EntityBehavior && module.isEnabled) {
                    module.onTick(scaledDelta)
                }
            }
        }

        // 4. Late tick pass
        for (sub in subs) {
            sub.onLateTick(scaledDelta)
        }
        for (entity in entities) {
            if (!entity.isActive) continue
            for (module in entity.modules) {
                if (module is EntityBehavior && module.isEnabled) {
                    module.onLateTick(scaledDelta)
                }
            }
        }

        input.beginNextTick()
    }

    /**
     * Triggers VBlank evaluation (`vblankDriver` -> `animations` -> `onRenderVBlank`) for tests
     * or direct hosts without a separate `FramePresentTarget` binding.
     */
    fun renderAtVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        vblankDriver.onBeforeFramePresent(frameNumber, timestampMs)
    }

    private fun dispatchRenderVBlankCallbacks(
        frameNumber: Long,
        deltaSeconds: Float,
        timestampMs: Double
    ) {
        clock.recordRenderFrame(frameNumber)
        val subs = subsystemSnapshot
        for (sub in subs) {
            sub.onRenderVBlank(frameNumber, deltaSeconds, timestampMs)
        }
        val entities = refreshEntitySnapshotIfDirty()
        for (entity in entities) {
            if (!entity.isActive) continue
            for (module in entity.modules) {
                if (module is EntityBehavior && module.isEnabled) {
                    module.onRenderVBlank(frameNumber, deltaSeconds, timestampMs)
                }
            }
        }
    }

    fun dispose() {
        vblankDriver.unbind()
        animations.stopAll()
        for (entity in rootSnapshot) {
            entity.dispose()
        }
        _rootEntities.clear()
        rootSnapshot = emptyList()
        flatEntitySnapshot = emptyList()
        for (sub in subsystemSnapshot) {
            sub.onDetach(this)
            sub.onDispose()
        }
        _subsystems.clear()
        subsystemSnapshot = emptyList()
        events.clear()
    }
}
