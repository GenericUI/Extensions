package com.nexustools.genericui.ext.box3d

/**
 * Listener interface receiving 3D collision and sensor trigger callbacks from [World3D].
 */
interface CollisionEventListener3D {
    fun onCollisionEnter(manifold: ContactManifold3D) {}
    fun onCollisionStay(manifold: ContactManifold3D) {}
    fun onCollisionExit(colliderA: Collider3D, colliderB: Collider3D) {}
    fun onSensorEnter(sensor: Collider3D, other: Collider3D) {}
    fun onSensorExit(sensor: Collider3D, other: Collider3D) {}
}
