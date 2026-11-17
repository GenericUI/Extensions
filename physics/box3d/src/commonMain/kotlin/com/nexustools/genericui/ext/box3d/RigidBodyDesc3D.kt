package com.nexustools.genericui.ext.box3d

/**
 * Descriptor used to construct a [RigidBody3D] in a [World3D].
 */
data class RigidBodyDesc3D(
    var type: BodyType3D = BodyType3D.STATIC,
    var position: Vec3 = Vec3(),
    var rotation: Quat3D = Quat3D(),
    var linearVelocity: Vec3 = Vec3(),
    var angularVelocity: Vec3 = Vec3(),
    var linearDamping: Float = 0.01f,
    var angularDamping: Float = 0.05f,
    var gravityScale: Float = 1f,
    var canSleep: Boolean = true,
    var awake: Boolean = true,
    var ccdEnabled: Boolean = false,
    var lockTranslationsX: Boolean = false,
    var lockTranslationsY: Boolean = false,
    var lockTranslationsZ: Boolean = false,
    var lockRotationsX: Boolean = false,
    var lockRotationsY: Boolean = false,
    var lockRotationsZ: Boolean = false,
    var userData: Any? = null
) {
    companion object {
        fun dynamic(x: Float = 0f, y: Float = 0f, z: Float = 0f): RigidBodyDesc3D =
            RigidBodyDesc3D(type = BodyType3D.DYNAMIC, position = Vec3(x, y, z))

        fun fixed(x: Float = 0f, y: Float = 0f, z: Float = 0f): RigidBodyDesc3D =
            RigidBodyDesc3D(type = BodyType3D.STATIC, position = Vec3(x, y, z))

        fun kinematic(x: Float = 0f, y: Float = 0f, z: Float = 0f): RigidBodyDesc3D =
            RigidBodyDesc3D(type = BodyType3D.KINEMATIC, position = Vec3(x, y, z))
    }
}
