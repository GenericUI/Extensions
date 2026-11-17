package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.box3d.Box3DDebugRenderer
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.box3d.World3D
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.scene.WorldSubsystem
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * [WorldSubsystem] bridging `:extensions:physics:box3d` ([World3D]) into a 3D [GameScene],
 * stepping rigid bodies and character controllers on the fixed simulation step and updating an
 * optional [Box3DDebugRenderer].
 */
class PhysicsWorld3DSubsystem(
    gravity: Vec3 = Vec3(0f, -9.81f, 0f),
    val threeScene: Scene? = null
) : WorldSubsystem {
    val world: World3D = World3D(gravity = gravity)

    var debugRenderer: Box3DDebugRenderer? = threeScene?.let { Box3DDebugRenderer(world, it) }
        private set

    var debugDrawEnabled: Boolean = false
        set(value) {
            field = value
            debugRenderer?.debugGroup?.visible = value
        }

    private var attachedScene: GameScene? = null

    fun attachDebugScene(scene: Scene): Box3DDebugRenderer {
        val existing = debugRenderer
        if (existing != null && existing.scene === scene) {
            existing.debugGroup.visible = debugDrawEnabled
            return existing
        }
        val created = Box3DDebugRenderer(world, scene)
        created.debugGroup.visible = debugDrawEnabled
        debugRenderer = created
        return created
    }

    override fun onAttach(scene: GameScene) {
        attachedScene = scene
        if (debugRenderer == null) {
            scene.getSubsystem<SceneGraph3DSubsystem>()?.threeScene?.let { attachDebugScene(it) }
        }
    }

    override fun onDetach(scene: GameScene) {
        if (attachedScene === scene) {
            attachedScene = null
        }
    }

    override fun onFixedStep(fixedDeltaSeconds: Float) {
        val scene = attachedScene
        if (scene != null) {
            for (entity in scene.allEntities()) {
                if (!entity.isActive) continue
                entity.getModule<RigidBody3DModule>()?.syncFromEntityBeforeStep()
            }
        }
        world.step(fixedDeltaSeconds)
        if (scene != null) {
            for (entity in scene.allEntities()) {
                if (!entity.isActive) continue
                entity.getModule<RigidBody3DModule>()?.syncToEntityAfterStep()
            }
        }
    }

    fun updateDebugRenderer() {
        val dbg = debugRenderer ?: return
        dbg.debugGroup.visible = debugDrawEnabled
        if (debugDrawEnabled) {
            dbg.update()
        }
    }
}
