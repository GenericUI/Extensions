package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.box3d.CharacterController3D
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.engine.math.Vec3f
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity

/**
 * Wraps Box3D's kinematic [CharacterController3D] on a [SceneEntity] for slope-aware collision
 * sliding and ground detection.
 */
class CharacterController3DModule(
    var maxSlopeAngleRad: Float = 0.7853982f,
    var maxStepHeight: Float = 0.35f,
    var skinWidth: Float = 0.02f
) : EntityModule() {

    var controller: CharacterController3D? = null
        private set

    val isGrounded: Boolean
        get() = controller?.isGrounded ?: false

    override fun onAttach(owner: SceneEntity) {
        ensureController()
    }

    fun ensureController(): CharacterController3D? {
        controller?.let { return it }
        val body = entity?.getModule<RigidBody3DModule>()?.ensureBodyCreated() ?: return null
        return CharacterController3D(
            body = body,
            maxSlopeAngleRad = maxSlopeAngleRad,
            maxStepHeight = maxStepHeight,
            skinWidth = skinWidth
        ).also { controller = it }
    }

    fun move(desiredTranslation: Vec3f, dtSeconds: Float): Vec3f {
        val ctrl = ensureController() ?: return Vec3f.ZERO
        val world = entity?.scene?.getSubsystem<PhysicsWorld3DSubsystem>()?.world ?: return Vec3f.ZERO
        val actual = ctrl.moveCharacter(
            world = world,
            desiredTranslation = Vec3(desiredTranslation.x, desiredTranslation.y, desiredTranslation.z),
            dt = dtSeconds
        )
        entity?.getModule<RigidBody3DModule>()?.syncToEntityAfterStep()
        return Vec3f(actual.x, actual.y, actual.z)
    }
}
