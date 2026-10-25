package com.nexustools.genericui.ext.box3d.dsl

import com.nexustools.genericui.ext.box3d.BoxShape3D
import com.nexustools.genericui.ext.box3d.CapsuleShape3D
import com.nexustools.genericui.ext.box3d.Collider3D
import com.nexustools.genericui.ext.box3d.ColliderDesc3D
import com.nexustools.genericui.ext.box3d.ColliderShape3D
import com.nexustools.genericui.ext.box3d.RigidBody3D
import com.nexustools.genericui.ext.box3d.SphereShape3D
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker

/**
 * Fluent DSL scope for attaching [Collider3D] collision shapes to a [RigidBody3D].
 */
@GenericUiDslMarker
class Box3DRigidBodyDslScope(
    val body: RigidBody3D
) {
    /**
     * Attaches a [Collider3D] with the given [shape] to [body].
     */
    fun collider(
        shape: ColliderShape3D,
        density: Float = 1f,
        friction: Float = 0.5f,
        restitution: Float = 0.1f,
        isSensor: Boolean = false,
        block: (ColliderDesc3D.() -> Unit)? = null
    ): Collider3D {
        val desc = ColliderDesc3D(
            shape = shape,
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )
        if (block != null) {
            desc.block()
        }
        return body.attachCollider(desc)
    }

    /**
     * Attaches a [BoxShape3D] [Collider3D] to [body].
     */
    fun boxCollider(
        hx: Float = 0.5f,
        hy: Float = 0.5f,
        hz: Float = 0.5f,
        density: Float = 1f,
        friction: Float = 0.5f,
        restitution: Float = 0.1f,
        isSensor: Boolean = false
    ): Collider3D = collider(
        shape = BoxShape3D(hx, hy, hz),
        density = density,
        friction = friction,
        restitution = restitution,
        isSensor = isSensor
    )

    /**
     * Attaches a [SphereShape3D] [Collider3D] to [body].
     */
    fun sphereCollider(
        radius: Float = 0.5f,
        density: Float = 1f,
        friction: Float = 0.5f,
        restitution: Float = 0.1f,
        isSensor: Boolean = false
    ): Collider3D = collider(
        shape = SphereShape3D(radius = radius),
        density = density,
        friction = friction,
        restitution = restitution,
        isSensor = isSensor
    )

    /**
     * Attaches a [CapsuleShape3D] [Collider3D] to [body].
     */
    fun capsuleCollider(
        halfHeight: Float = 0.5f,
        radius: Float = 0.25f,
        density: Float = 1f,
        friction: Float = 0.5f,
        restitution: Float = 0.1f,
        isSensor: Boolean = false
    ): Collider3D = collider(
        shape = CapsuleShape3D(halfHeight = halfHeight, radius = radius),
        density = density,
        friction = friction,
        restitution = restitution,
        isSensor = isSensor
    )
}
