package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.box3d.Joint3D
import com.nexustools.genericui.ext.box3d.RigidBody3D
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity

/**
 * Connects this [SceneEntity]'s [RigidBody3DModule] with [otherEntity]'s body via a Box3D [Joint3D].
 */
class Joint3DModule(
    val otherEntity: SceneEntity,
    val builder: (bodyA: RigidBody3D, bodyB: RigidBody3D) -> Joint3D
) : EntityModule() {

    var joint: Joint3D? = null
        private set

    override fun onAttach(owner: SceneEntity) {
        val sub = owner.scene?.getSubsystem<PhysicsWorld3DSubsystem>() ?: return
        val bodyA = owner.getModule<RigidBody3DModule>()?.ensureBodyCreated() ?: return
        val bodyB = otherEntity.getModule<RigidBody3DModule>()?.ensureBodyCreated() ?: return
        val created = builder(bodyA, bodyB)
        sub.world.addJoint(created)
        joint = created
    }

    override fun onDetach(owner: SceneEntity) {
        val existing = joint ?: return
        owner.scene?.getSubsystem<PhysicsWorld3DSubsystem>()?.world?.removeJoint(existing)
        joint = null
    }
}
