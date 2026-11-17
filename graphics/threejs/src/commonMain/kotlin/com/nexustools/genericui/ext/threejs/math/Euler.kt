package com.nexustools.genericui.ext.threejs.math

import kotlin.math.asin
import kotlin.math.atan2

/**
 * Euler angles (`THREE.Euler`) in radians with rotation order.
 */
data class Euler(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var order: String = DEFAULT_ORDER
) {
    fun set(x: Float, y: Float, z: Float, order: String = this.order): Euler {
        this.x = x
        this.y = y
        this.z = z
        this.order = order
        return this
    }

    fun copy(euler: Euler): Euler = set(euler.x, euler.y, euler.z, euler.order)

    fun clone(): Euler = Euler(x, y, z, order)

    fun setFromRotationMatrix(m: Matrix4, order: String = this.order): Euler {
        val te = m.elements
        val m11 = te[0]; val m12 = te[4]; val m13 = te[8]
        val m21 = te[1]; val m22 = te[5]; val m23 = te[9]
        val m31 = te[2]; val m32 = te[6]; val m33 = te[10]

        this.order = order
        y = asin(MathUtils.clamp(m13, -1f, 1f))
        if (kotlin.math.abs(m13) < 0.9999999f) {
            x = atan2(-m23, m33)
            z = atan2(-m12, m11)
        } else {
            x = atan2(m32, m22)
            z = 0f
        }
        return this
    }

    fun setFromQuaternion(q: Quaternion, order: String = this.order): Euler {
        val matrix = Matrix4().compose(Vector3(0f, 0f, 0f), q, Vector3(1f, 1f, 1f))
        return setFromRotationMatrix(matrix, order)
    }

    companion object {
        const val DEFAULT_ORDER = "XYZ"
    }
}
