package com.nexustools.genericui.ext.engine.math

import kotlin.math.atan2

/**
 * Unified 2D/3D spatial placement state for a scene entity, keeping 2D planar properties
 * ([position2D], [rotation2DRad], [scale2D]) cleanly synchronized with full 3D placement
 * ([position], [rotation], [scale]) and matrix builders.
 */
class SpatialTransform(
    position: Vec3f = Vec3f.ZERO,
    rotation: Quat4f = Quat4f.IDENTITY,
    scale: Vec3f = Vec3f.ONE
) {
    val position: Vec3f = position.copy()
    val rotation: Quat4f = rotation.copy()
    val scale: Vec3f = scale.copy()

    var position2D: Vec2f
        get() = Vec2f(position.x, position.y)
        set(value) {
            position.x = value.x
            position.y = value.y
        }

    var rotation2DRad: Float
        get() = 2f * atan2(rotation.z, rotation.w)
        set(value) {
            rotation.copyFrom(Quat4f.fromAxisAngle(Vec3f.UNIT_Z, value))
        }

    var scale2D: Vec2f
        get() = Vec2f(scale.x, scale.y)
        set(value) {
            scale.x = value.x
            scale.y = value.y
        }

    var eulerAnglesRad: Vec3f
        get() = rotation.toEulerRadians()
        set(value) {
            rotation.copyFrom(Quat4f.fromEulerRadians(value.x, value.y, value.z))
        }

    fun setPosition(x: Float, y: Float, z: Float = position.z): SpatialTransform {
        position.set(x, y, z)
        return this
    }

    fun setPosition2D(x: Float, y: Float): SpatialTransform {
        position.x = x
        position.y = y
        return this
    }

    fun translate(dx: Float, dy: Float, dz: Float = 0f): SpatialTransform {
        position.x += dx
        position.y += dy
        position.z += dz
        return this
    }

    fun translate2D(dx: Float, dy: Float): SpatialTransform {
        position.x += dx
        position.y += dy
        return this
    }

    fun setRotation2D(radians: Float): SpatialTransform {
        rotation2DRad = radians
        return this
    }

    fun setEulerRadians(xRad: Float, yRad: Float, zRad: Float): SpatialTransform {
        rotation.copyFrom(Quat4f.fromEulerRadians(xRad, yRad, zRad))
        return this
    }

    fun setScale(sx: Float, sy: Float, sz: Float = scale.z): SpatialTransform {
        scale.set(sx, sy, sz)
        return this
    }

    fun setScale2D(sx: Float, sy: Float): SpatialTransform {
        scale.x = sx
        scale.y = sy
        return this
    }

    fun copyFrom(other: SpatialTransform): SpatialTransform {
        position.copyFrom(other.position)
        rotation.copyFrom(other.rotation)
        scale.copyFrom(other.scale)
        return this
    }

    fun toLocalMatrix4(): Mat4f = Mat4f.compose(position, rotation, scale)

    fun toLocalAffine2D(): AffineMatrix2D = AffineMatrix2D.fromTRS(
        x = position.x,
        y = position.y,
        rotationRad = rotation2DRad,
        scaleX = scale.x,
        scaleY = scale.y
    )
}
