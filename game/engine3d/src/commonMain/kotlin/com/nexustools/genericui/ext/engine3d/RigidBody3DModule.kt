package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.box3d.BodyType3D
import com.nexustools.genericui.ext.box3d.Quat3D
import com.nexustools.genericui.ext.box3d.RigidBody3D
import com.nexustools.genericui.ext.box3d.RigidBodyDesc3D
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity

/**
 * Attaches a Box3D [RigidBody3D] to a [SceneEntity], lazily registering with
 * [PhysicsWorld3DSubsystem] and synchronizing [SceneEntity.transform] and any sibling
 * [Node3DBindingModule] on every physics step.
 */
class RigidBody3DModule(
    val bodyType: BodyType3D = BodyType3D.DYNAMIC,
    var gravityScale: Float = 1f,
    var linearDamping: Float = 0.01f,
    var angularDamping: Float = 0.05f,
    var lockRotations: Boolean = false
) : EntityModule() {

    var body: RigidBody3D? = null
        private set

    override fun onAttach(owner: SceneEntity) {
        ensureBodyCreated()
    }

    override fun onDetach(owner: SceneEntity) {
        val existing = body ?: return
        owner.scene?.getSubsystem<PhysicsWorld3DSubsystem>()?.world?.removeRigidBody(existing)
        body = null
    }

    fun ensureBodyCreated(): RigidBody3D? {
        body?.let { return it }
        val owner = entity ?: return null
        val subsystem = owner.scene?.getSubsystem<PhysicsWorld3DSubsystem>() ?: return null
        val p = owner.transform.position
        val r = owner.transform.rotation
        val desc = RigidBodyDesc3D(
            type = bodyType,
            position = Vec3(p.x, p.y, p.z),
            rotation = Quat3D(r.x, r.y, r.z, r.w),
            linearDamping = linearDamping,
            angularDamping = angularDamping,
            gravityScale = gravityScale,
            lockRotationsX = lockRotations,
            lockRotationsY = lockRotations,
            lockRotationsZ = lockRotations,
            userData = owner
        )
        val boundObj = owner.getModule<Node3DBindingModule>()?.object3D
        val created = subsystem.world.createRigidBody(desc, boundObject3D = boundObj)
        body = created
        for (col in owner.findModules<Collider3DModule>()) {
            col.attachToBody(created)
        }
        return created
    }

    fun syncFromEntityBeforeStep() {
        val b = body ?: ensureBodyCreated() ?: return
        val owner = entity ?: return
        if (b.type == BodyType3D.KINEMATIC) {
            val p = owner.transform.position
            val r = owner.transform.rotation
            b.position.set(p.x, p.y, p.z)
            b.rotation.set(r.x, r.y, r.z, r.w)
        }
    }

    fun syncToEntityAfterStep() {
        val b = body ?: return
        val owner = entity ?: return
        owner.transform.position.set(b.position.x, b.position.y, b.position.z)
        owner.transform.rotation.set(b.rotation.x, b.rotation.y, b.rotation.z, b.rotation.w)
        owner.getModule<Node3DBindingModule>()?.syncFromEntity()
    }

    fun applyForce(fx: Float, fy: Float, fz: Float) {
        ensureBodyCreated()?.applyForce(Vec3(fx, fy, fz))
    }

    fun applyImpulse(ix: Float, iy: Float, iz: Float) {
        ensureBodyCreated()?.applyLinearImpulse(Vec3(ix, iy, iz))
    }
}
