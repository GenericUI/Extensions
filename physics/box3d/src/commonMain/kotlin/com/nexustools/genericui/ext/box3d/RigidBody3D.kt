package com.nexustools.genericui.ext.box3d

import com.nexustools.genericui.ext.threejs.core.Object3D

/**
 * 3D Rigid Body holding transform, velocity, forces, colliders, and direct Three.js [Object3D] synchronization.
 */
class RigidBody3D(
    val id: Int,
    desc: RigidBodyDesc3D
) {
    var type: BodyType3D = desc.type
    val transform: Transform3D = Transform3D(desc.position.clone(), desc.rotation.clone())
    val linearVelocity: Vec3 = desc.linearVelocity.clone()
    val angularVelocity: Vec3 = desc.angularVelocity.clone()
    var linearDamping: Float = desc.linearDamping
    var angularDamping: Float = desc.angularDamping
    var gravityScale: Float = desc.gravityScale
    var canSleep: Boolean = desc.canSleep
    var isAwake: Boolean = desc.awake
    var ccdEnabled: Boolean = desc.ccdEnabled
    var lockTranslationsX: Boolean = desc.lockTranslationsX
    var lockTranslationsY: Boolean = desc.lockTranslationsY
    var lockTranslationsZ: Boolean = desc.lockTranslationsZ
    var lockRotationsX: Boolean = desc.lockRotationsX
    var lockRotationsY: Boolean = desc.lockRotationsY
    var lockRotationsZ: Boolean = desc.lockRotationsZ
    var userData: Any? = desc.userData

    val forceAccumulator: Vec3 = Vec3()
    val torqueAccumulator: Vec3 = Vec3()

    var mass: Float = 0f
        private set
    var invMass: Float = 0f
        private set
    val localCenterOfMass: Vec3 = Vec3()
    val invPrincipalInertia: Vec3 = Vec3()

    private val _colliders = mutableListOf<Collider3D>()
    val colliders: List<Collider3D> get() = _colliders

    /**
     * Optional bound Three.js [Object3D] that automatically syncs when [World3D.step] runs.
     */
    var boundObject3D: Object3D? = null

    var position: Vec3
        get() = transform.position
        set(value) {
            transform.position.copy(value)
        }

    var rotation: Quat3D
        get() = transform.rotation
        set(value) {
            transform.rotation.copy(value)
        }

    fun attachCollider(desc: ColliderDesc3D): Collider3D {
        val collider = Collider3D(this, desc)
        _colliders.add(collider)
        recomputeMassProperties()
        return collider
    }

    fun detachCollider(collider: Collider3D) {
        if (_colliders.remove(collider)) {
            recomputeMassProperties()
        }
    }

    fun recomputeMassProperties() {
        if (type != BodyType3D.DYNAMIC) {
            mass = 0f
            invMass = 0f
            invPrincipalInertia.set(0f, 0f, 0f)
            return
        }
        var totalMass = 0f
        val com = Vec3()
        val inertia = Vec3()
        for (c in _colliders) {
            if (c.isSensor || c.density <= 0f) continue
            val mp = c.shape.computeMassProperties(c.density)
            totalMass += mp.mass
            com.addLocal((mp.centerOfMass + c.localTransform.position) * mp.mass)
            inertia.addLocal(mp.principalInertia)
        }
        if (totalMass > 1e-6f) {
            mass = totalMass
            invMass = 1f / totalMass
            localCenterOfMass.copy(com / totalMass)
            invPrincipalInertia.set(
                if (inertia.x > 1e-6f) 1f / inertia.x else 0f,
                if (inertia.y > 1e-6f) 1f / inertia.y else 0f,
                if (inertia.z > 1e-6f) 1f / inertia.z else 0f
            )
        } else {
            mass = 1f
            invMass = 1f
            invPrincipalInertia.set(1f, 1f, 1f)
        }
    }

    fun applyForce(force: Vec3, wake: Boolean = true) {
        if (type != BodyType3D.DYNAMIC) return
        if (wake) isAwake = true
        if (isAwake) forceAccumulator.addLocal(force)
    }

    fun applyForceAtPoint(force: Vec3, worldPoint: Vec3, wake: Boolean = true) {
        if (type != BodyType3D.DYNAMIC) return
        if (wake) isAwake = true
        if (isAwake) {
            forceAccumulator.addLocal(force)
            val r = worldPoint - transform.position
            torqueAccumulator.addLocal(r.cross(force))
        }
    }

    fun applyTorque(torque: Vec3, wake: Boolean = true) {
        if (type != BodyType3D.DYNAMIC) return
        if (wake) isAwake = true
        if (isAwake) torqueAccumulator.addLocal(torque)
    }

    fun applyLinearImpulse(impulse: Vec3, wake: Boolean = true) {
        if (type != BodyType3D.DYNAMIC) return
        if (wake) isAwake = true
        if (isAwake) {
            if (!lockTranslationsX) linearVelocity.x += impulse.x * invMass
            if (!lockTranslationsY) linearVelocity.y += impulse.y * invMass
            if (!lockTranslationsZ) linearVelocity.z += impulse.z * invMass
        }
    }

    fun applyImpulseAtPoint(impulse: Vec3, worldPoint: Vec3, wake: Boolean = true) {
        if (type != BodyType3D.DYNAMIC) return
        applyLinearImpulse(impulse, wake)
        val r = worldPoint - transform.position
        val angularImpulse = r.cross(impulse)
        applyAngularImpulse(angularImpulse, wake)
    }

    fun applyAngularImpulse(impulse: Vec3, wake: Boolean = true) {
        if (type != BodyType3D.DYNAMIC) return
        if (wake) isAwake = true
        if (isAwake) {
            if (!lockRotationsX) angularVelocity.x += impulse.x * invPrincipalInertia.x
            if (!lockRotationsY) angularVelocity.y += impulse.y * invPrincipalInertia.y
            if (!lockRotationsZ) angularVelocity.z += impulse.z * invPrincipalInertia.z
        }
    }

    /**
     * Copies this rigid body's world position and orientation directly onto a Three.js [Object3D].
     */
    fun syncToThreeObject3D(object3D: Object3D) {
        object3D.position.set(transform.position.x, transform.position.y, transform.position.z)
        object3D.quaternion.set(
            transform.rotation.x,
            transform.rotation.y,
            transform.rotation.z,
            transform.rotation.w
        )
        object3D.matrixWorldNeedsUpdate = true
    }

    /**
     * Copies a Three.js [Object3D]'s position and quaternion into this rigid body's transform.
     */
    fun syncFromThreeObject3D(object3D: Object3D) {
        transform.position.set(object3D.position.x, object3D.position.y, object3D.position.z)
        transform.rotation.set(
            object3D.quaternion.x,
            object3D.quaternion.y,
            object3D.quaternion.z,
            object3D.quaternion.w
        )
    }
}
