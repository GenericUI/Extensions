package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.threejs.core.Object3D

/**
 * Binds a Three.js [Object3D] scene node onto a [SceneEntity], keeping position, quaternion,
 * scale, and visibility synchronized with [SceneEntity.transform].
 */
open class Node3DBindingModule(
    val object3D: Object3D = Object3D(),
    var autoMountInScene: Boolean = true
) : EntityModule() {

    override fun onAttach(owner: SceneEntity) {
        if (object3D.name.isEmpty()) {
            object3D.name = owner.name
        }
        syncFromEntity()
        if (autoMountInScene) {
            owner.scene?.getSubsystem<SceneGraph3DSubsystem>()?.attachObject(object3D)
        }
    }

    override fun onDetach(owner: SceneEntity) {
        if (autoMountInScene) {
            owner.scene?.getSubsystem<SceneGraph3DSubsystem>()?.detachObject(object3D)
        }
    }

    fun syncFromEntity() {
        val owner = entity ?: return
        val tf = owner.transform
        object3D.position.set(tf.position.x, tf.position.y, tf.position.z)
        object3D.quaternion.set(tf.rotation.x, tf.rotation.y, tf.rotation.z, tf.rotation.w)
        object3D.syncEulerFromQuaternion()
        object3D.scale.set(tf.scale.x, tf.scale.y, tf.scale.z)
        object3D.visible = owner.isActive && isEnabled
        object3D.matrixWorldNeedsUpdate = true
    }

    fun syncToEntity() {
        val owner = entity ?: return
        owner.transform.position.set(object3D.position.x, object3D.position.y, object3D.position.z)
        owner.transform.rotation.set(
            object3D.quaternion.x,
            object3D.quaternion.y,
            object3D.quaternion.z,
            object3D.quaternion.w
        )
        owner.transform.scale.set(object3D.scale.x, object3D.scale.y, object3D.scale.z)
    }
}
