package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.box2d.Joint2D
import com.nexustools.genericui.ext.box2d.JointDef2D
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity

/**
 * Connects this [SceneEntity]'s [RigidBody2DModule] with [otherEntity]'s body via a Box2D [Joint2D].
 */
class Joint2DModule(
    val otherEntity: SceneEntity,
    val builder: (bodyA: com.nexustools.genericui.ext.box2d.Body2D, bodyB: com.nexustools.genericui.ext.box2d.Body2D) -> JointDef2D
) : EntityModule() {

    var joint: Joint2D? = null
        private set

    override fun onAttach(owner: SceneEntity) {
        val sub = owner.scene?.getSubsystem<PhysicsWorld2DSubsystem>() ?: return
        val bodyA = owner.getModule<RigidBody2DModule>()?.ensureBodyCreated() ?: return
        val bodyB = otherEntity.getModule<RigidBody2DModule>()?.ensureBodyCreated() ?: return
        joint = sub.world.createJoint(builder(bodyA, bodyB))
    }

    override fun onDetach(owner: SceneEntity) {
        val existing = joint ?: return
        owner.scene?.getSubsystem<PhysicsWorld2DSubsystem>()?.world?.destroyJoint(existing)
        joint = null
    }
}
