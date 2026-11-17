package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.engine.math.SpatialTransform

/**
 * Base attachable capability module mounted onto a [SceneEntity].
 */
abstract class EntityModule {
    var entity: SceneEntity? = null
        internal set

    var isEnabled: Boolean = true

    val transform: SpatialTransform?
        get() = entity?.transform

    val scene: GameScene?
        get() = entity?.scene

    internal fun attachTo(owner: SceneEntity) {
        entity = owner
        onAttach(owner)
    }

    internal fun detachFrom(owner: SceneEntity) {
        onDetach(owner)
        if (entity === owner) {
            entity = null
        }
    }

    open fun onAttach(owner: SceneEntity) {}

    open fun onDetach(owner: SceneEntity) {}

    open fun onDispose() {}
}
