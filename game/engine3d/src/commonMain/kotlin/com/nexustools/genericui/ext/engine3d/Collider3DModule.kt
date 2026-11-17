package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.box3d.BoxShape3D
import com.nexustools.genericui.ext.box3d.CapsuleShape3D
import com.nexustools.genericui.ext.box3d.Collider3D
import com.nexustools.genericui.ext.box3d.ColliderDesc3D
import com.nexustools.genericui.ext.box3d.ColliderShape3D
import com.nexustools.genericui.ext.box3d.RigidBody3D
import com.nexustools.genericui.ext.box3d.SphereShape3D
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity

/**
 * Attaches a Box3D [ColliderShape3D] onto a sibling [RigidBody3DModule].
 */
class Collider3DModule(
    val shape: ColliderShape3D,
    var density: Float = 1f,
    var friction: Float = 0.5f,
    var restitution: Float = 0.1f,
    var isSensor: Boolean = false
) : EntityModule() {

    var collider: Collider3D? = null
        private set

    override fun onAttach(owner: SceneEntity) {
        val body = owner.getModule<RigidBody3DModule>()?.ensureBodyCreated() ?: return
        attachToBody(body)
    }

    override fun onDetach(owner: SceneEntity) {
        val existing = collider ?: return
        existing.body.detachCollider(existing)
        collider = null
    }

    internal fun attachToBody(body: RigidBody3D) {
        if (collider != null) return
        val desc = ColliderDesc3D(
            shape = shape,
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor,
            userData = entity
        )
        collider = body.attachCollider(desc)
    }

    companion object {
        fun box(
            halfWidth: Float,
            halfHeight: Float,
            halfDepth: Float,
            density: Float = 1f,
            friction: Float = 0.5f,
            restitution: Float = 0.1f,
            isSensor: Boolean = false
        ): Collider3DModule = Collider3DModule(
            shape = BoxShape3D(Vec3(halfWidth, halfHeight, halfDepth)),
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )

        fun sphere(
            radius: Float,
            density: Float = 1f,
            friction: Float = 0.5f,
            restitution: Float = 0.1f,
            isSensor: Boolean = false
        ): Collider3DModule = Collider3DModule(
            shape = SphereShape3D(radius),
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )

        fun capsule(
            radius: Float,
            halfHeight: Float,
            density: Float = 1f,
            friction: Float = 0.5f,
            restitution: Float = 0.1f,
            isSensor: Boolean = false
        ): Collider3DModule = Collider3DModule(
            shape = CapsuleShape3D(halfHeight = halfHeight, radius = radius),
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )
    }
}
