package com.nexustools.genericui.ext.box3d

/**
 * 3D rigid body transform combining translation (`position`) and orientation (`rotation`).
 */
data class Transform3D(
    val position: Vec3 = Vec3(),
    val rotation: Quat3D = Quat3D()
) {
    fun setIdentity(): Transform3D {
        position.set(0f, 0f, 0f)
        rotation.set(0f, 0f, 0f, 1f)
        return this
    }

    fun copy(other: Transform3D): Transform3D {
        position.copy(other.position)
        rotation.copy(other.rotation)
        return this
    }

    fun clone(): Transform3D = Transform3D(position.clone(), rotation.clone())

    fun transformPoint(localPoint: Vec3): Vec3 =
        rotation.rotateVec3(localPoint).addLocal(position)

    fun inverseTransformPoint(worldPoint: Vec3): Vec3 =
        rotation.inverseRotateVec3(worldPoint - position)

    fun transformVector(localVector: Vec3): Vec3 =
        rotation.rotateVec3(localVector)

    fun inverseTransformVector(worldVector: Vec3): Vec3 =
        rotation.inverseRotateVec3(worldVector)
}
