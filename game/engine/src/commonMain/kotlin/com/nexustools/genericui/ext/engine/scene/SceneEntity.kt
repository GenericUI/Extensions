package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.engine.math.AffineMatrix2D
import com.nexustools.genericui.ext.engine.math.Mat4f
import com.nexustools.genericui.ext.engine.math.SpatialTransform
import kotlin.concurrent.Volatile

/**
 * Hierarchical scene node owning a [SpatialTransform], [TagAndLayerMask], child entities, and
 * attached [EntityModule] instances.
 */
class SceneEntity(
    var name: String = "Entity"
) {
    val id: Long = nextEntityId()
    val transform: SpatialTransform = SpatialTransform()
    val tagAndLayer: TagAndLayerMask = TagAndLayerMask()

    var isActive: Boolean = true

    var parent: SceneEntity? = null
        internal set

    var scene: GameScene? = null
        internal set

    private val _children = mutableListOf<SceneEntity>()
    @Volatile
    private var childrenSnapshot: List<SceneEntity> = emptyList()
    val children: List<SceneEntity>
        get() = childrenSnapshot

    private val _modules = mutableListOf<EntityModule>()
    @Volatile
    private var modulesSnapshot: List<EntityModule> = emptyList()
    val modules: List<EntityModule>
        get() = modulesSnapshot

    fun addChild(child: SceneEntity): SceneEntity {
        if (child === this) return child
        child.parent?.removeChild(child)
        child.parent = this
        _children.add(child)
        childrenSnapshot = _children.toList()
        scene?.let { child.assignSceneRecursive(it) }
        scene?.markHierarchyDirty()
        return child
    }

    fun removeChild(child: SceneEntity): Boolean {
        if (!_children.remove(child)) return false
        child.parent = null
        childrenSnapshot = _children.toList()
        child.assignSceneRecursive(null)
        scene?.markHierarchyDirty()
        return true
    }

    fun findChildByName(targetName: String, recursive: Boolean = true): SceneEntity? {
        for (c in childrenSnapshot) {
            if (c.name == targetName) return c
            if (recursive) {
                c.findChildByName(targetName, recursive = true)?.let { return it }
            }
        }
        return null
    }

    fun <T : EntityModule> addModule(module: T): T {
        if (module.entity === this) return module
        module.entity?.removeModule(module)
        _modules.add(module)
        modulesSnapshot = _modules.toList()
        module.attachTo(this)
        return module
    }

    fun removeModule(module: EntityModule): Boolean {
        if (!_modules.remove(module)) return false
        modulesSnapshot = _modules.toList()
        module.detachFrom(this)
        return true
    }

    inline fun <reified T : EntityModule> getModule(): T? {
        for (m in modules) {
            if (m is T) return m
        }
        return null
    }

    inline fun <reified T : EntityModule> findModules(): List<T> =
        modules.filterIsInstance<T>()

    inline fun <reified T : EntityModule> hasModule(): Boolean =
        getModule<T>() != null

    fun worldMatrix4(): Mat4f {
        val local = transform.toLocalMatrix4()
        val p = parent
        return if (p == null) local else p.worldMatrix4() * local
    }

    fun worldAffine2D(): AffineMatrix2D {
        val local = transform.toLocalAffine2D()
        val p = parent
        return if (p == null) local else p.worldAffine2D() * local
    }

    internal fun assignSceneRecursive(targetScene: GameScene?) {
        val oldScene = scene
        if (oldScene === targetScene) return
        scene = targetScene
        for (c in childrenSnapshot) {
            c.assignSceneRecursive(targetScene)
        }
    }

    fun dispose() {
        for (c in childrenSnapshot) {
            c.dispose()
        }
        _children.clear()
        childrenSnapshot = emptyList()
        for (m in modulesSnapshot) {
            m.detachFrom(this)
            m.onDispose()
        }
        _modules.clear()
        modulesSnapshot = emptyList()
        parent = null
        scene = null
    }

    companion object {
        private var idCounter: Long = 1L

        private fun nextEntityId(): Long = idCounter++
    }
}
