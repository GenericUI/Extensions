package com.nexustools.genericui.ext.threejs.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 4x4 column-major transformation matrix (`THREE.Matrix4`).
 */
class Matrix4 {
    val elements: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    fun set(
        n11: Float, n12: Float, n13: Float, n14: Float,
        n21: Float, n22: Float, n23: Float, n24: Float,
        n31: Float, n32: Float, n33: Float, n34: Float,
        n41: Float, n42: Float, n43: Float, n44: Float
    ): Matrix4 {
        val te = elements
        te[0] = n11; te[4] = n12; te[8] = n13; te[12] = n14
        te[1] = n21; te[5] = n22; te[9] = n23; te[13] = n24
        te[2] = n31; te[6] = n32; te[10] = n33; te[14] = n34
        te[3] = n41; te[7] = n42; te[11] = n43; te[15] = n44
        return this
    }

    fun identity(): Matrix4 = set(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    fun copy(m: Matrix4): Matrix4 {
        m.elements.copyInto(elements)
        return this
    }

    fun clone(): Matrix4 = Matrix4().copy(this)

    fun multiply(m: Matrix4): Matrix4 = multiplyMatrices(this, m)

    fun premultiply(m: Matrix4): Matrix4 = multiplyMatrices(m, this)

    fun multiplyMatrices(a: Matrix4, b: Matrix4): Matrix4 {
        val ae = a.elements
        val be = b.elements
        val te = elements

        val a11 = ae[0]; val a12 = ae[4]; val a13 = ae[8]; val a14 = ae[12]
        val a21 = ae[1]; val a22 = ae[5]; val a23 = ae[9]; val a24 = ae[13]
        val a31 = ae[2]; val a32 = ae[6]; val a33 = ae[10]; val a34 = ae[14]
        val a41 = ae[3]; val a42 = ae[7]; val a43 = ae[11]; val a44 = ae[15]

        val b11 = be[0]; val b12 = be[4]; val b13 = be[8]; val b14 = be[12]
        val b21 = be[1]; val b22 = be[5]; val b23 = be[9]; val b24 = be[13]
        val b31 = be[2]; val b32 = be[6]; val b33 = be[10]; val b34 = be[14]
        val b41 = be[3]; val b42 = be[7]; val b43 = be[11]; val b44 = be[15]

        te[0] = a11 * b11 + a12 * b21 + a13 * b31 + a14 * b41
        te[4] = a11 * b12 + a12 * b22 + a13 * b32 + a14 * b42
        te[8] = a11 * b13 + a12 * b23 + a13 * b33 + a14 * b43
        te[12] = a11 * b14 + a12 * b24 + a13 * b34 + a14 * b44

        te[1] = a21 * b11 + a22 * b21 + a23 * b31 + a24 * b41
        te[5] = a21 * b12 + a22 * b22 + a23 * b32 + a24 * b42
        te[9] = a21 * b13 + a22 * b23 + a23 * b33 + a24 * b43
        te[13] = a21 * b14 + a22 * b24 + a23 * b34 + a24 * b44

        te[2] = a31 * b11 + a32 * b21 + a33 * b31 + a34 * b41
        te[6] = a31 * b12 + a32 * b22 + a33 * b32 + a34 * b42
        te[10] = a31 * b13 + a32 * b23 + a33 * b33 + a34 * b43
        te[14] = a31 * b14 + a32 * b24 + a33 * b34 + a34 * b44

        te[3] = a41 * b11 + a42 * b21 + a43 * b31 + a44 * b41
        te[7] = a41 * b12 + a42 * b22 + a43 * b32 + a44 * b42
        te[11] = a41 * b13 + a42 * b23 + a43 * b33 + a44 * b43
        te[15] = a41 * b14 + a42 * b24 + a43 * b34 + a44 * b44

        return this
    }

    fun makeTranslation(x: Float, y: Float, z: Float): Matrix4 = set(
        1f, 0f, 0f, x,
        0f, 1f, 0f, y,
        0f, 0f, 1f, z,
        0f, 0f, 0f, 1f
    )

    fun makeRotationX(theta: Float): Matrix4 {
        val c = cos(theta)
        val s = sin(theta)
        return set(
            1f, 0f, 0f, 0f,
            0f, c, -s, 0f,
            0f, s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    fun makeRotationY(theta: Float): Matrix4 {
        val c = cos(theta)
        val s = sin(theta)
        return set(
            c, 0f, s, 0f,
            0f, 1f, 0f, 0f,
            -s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    fun makeRotationZ(theta: Float): Matrix4 {
        val c = cos(theta)
        val s = sin(theta)
        return set(
            c, -s, 0f, 0f,
            s, c, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }

    fun makeScale(x: Float, y: Float, z: Float): Matrix4 = set(
        x, 0f, 0f, 0f,
        0f, y, 0f, 0f,
        0f, 0f, z, 0f,
        0f, 0f, 0f, 1f
    )

    fun compose(position: Vector3, quaternion: Quaternion, scale: Vector3): Matrix4 {
        val te = elements
        val x = quaternion.x; val y = quaternion.y; val z = quaternion.z; val w = quaternion.w
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2
        val yy = y * y2; val yz = y * z2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2

        val sx = scale.x; val sy = scale.y; val sz = scale.z

        te[0] = (1f - (yy + zz)) * sx
        te[1] = (xy + wz) * sx
        te[2] = (xz - wy) * sx
        te[3] = 0f

        te[4] = (xy - wz) * sy
        te[5] = (1f - (xx + zz)) * sy
        te[6] = (yz + wx) * sy
        te[7] = 0f

        te[8] = (xz + wy) * sz
        te[9] = (yz - wx) * sz
        te[10] = (1f - (xx + yy)) * sz
        te[11] = 0f

        te[12] = position.x
        te[13] = position.y
        te[14] = position.z
        te[15] = 1f

        return this
    }

    fun decompose(position: Vector3, quaternion: Quaternion, scale: Vector3): Matrix4 {
        val te = elements
        var sx = Vector3(te[0], te[1], te[2]).length()
        val sy = Vector3(te[4], te[5], te[6]).length()
        val sz = Vector3(te[8], te[9], te[10]).length()

        if (determinant() < 0f) sx = -sx

        position.set(te[12], te[13], te[14])

        val m1 = clone()
        val invSX = if (sx != 0f) 1f / sx else 0f
        val invSY = if (sy != 0f) 1f / sy else 0f
        val invSZ = if (sz != 0f) 1f / sz else 0f

        m1.elements[0] *= invSX; m1.elements[1] *= invSX; m1.elements[2] *= invSX
        m1.elements[4] *= invSY; m1.elements[5] *= invSY; m1.elements[6] *= invSY
        m1.elements[8] *= invSZ; m1.elements[9] *= invSZ; m1.elements[10] *= invSZ

        quaternion.setFromRotationMatrix(m1)
        scale.set(sx, sy, sz)
        return this
    }

    fun makePerspective(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Matrix4 {
        val te = elements
        val x = 2f * near / (right - left)
        val y = 2f * near / (top - bottom)
        val a = (right + left) / (right - left)
        val b = (top + bottom) / (top - bottom)
        val c = -(far + near) / (far - near)
        val d = (-2f * far * near) / (far - near)

        te[0] = x; te[4] = 0f; te[8] = a; te[12] = 0f
        te[1] = 0f; te[5] = y; te[9] = b; te[13] = 0f
        te[2] = 0f; te[6] = 0f; te[10] = c; te[14] = d
        te[3] = 0f; te[7] = 0f; te[11] = -1f; te[15] = 0f
        return this
    }

    fun makePerspectiveFov(fovDegrees: Float, aspect: Float, near: Float, far: Float): Matrix4 {
        val top = near * tan(MathUtils.degToRad(fovDegrees * 0.5f))
        val height = 2f * top
        val width = aspect * height
        val left = -0.5f * width
        return makePerspective(left, left + width, top, top - height, near, far)
    }

    fun makeOrthographic(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Matrix4 {
        val te = elements
        val w = 1f / (right - left)
        val h = 1f / (top - bottom)
        val p = 1f / (far - near)

        val x = (right + left) * w
        val y = (top + bottom) * h
        val z = (far + near) * p

        te[0] = 2f * w; te[4] = 0f; te[8] = 0f; te[12] = -x
        te[1] = 0f; te[5] = 2f * h; te[9] = 0f; te[13] = -y
        te[2] = 0f; te[6] = 0f; te[10] = -2f * p; te[14] = -z
        te[3] = 0f; te[7] = 0f; te[11] = 0f; te[15] = 1f
        return this
    }

    fun lookAt(eye: Vector3, target: Vector3, up: Vector3): Matrix4 {
        val te = elements
        val z = Vector3().subVectors(eye, target)
        if (z.lengthSq() == 0f) {
            z.z = 1f
        } else {
            z.normalize()
        }
        val x = Vector3().crossVectors(up, z)
        if (x.lengthSq() == 0f) {
            if (kotlin.math.abs(up.z) == 1f) z.x += 0.0001f else z.z += 0.0001f
            z.normalize()
            x.crossVectors(up, z)
        }
        x.normalize()
        val y = Vector3().crossVectors(z, x)

        te[0] = x.x; te[4] = y.x; te[8] = z.x
        te[1] = x.y; te[5] = y.y; te[9] = z.y
        te[2] = x.z; te[6] = y.z; te[10] = z.z
        return this
    }

    fun determinant(): Float {
        val te = elements
        val n11 = te[0]; val n12 = te[4]; val n13 = te[8]; val n14 = te[12]
        val n21 = te[1]; val n22 = te[5]; val n23 = te[9]; val n24 = te[13]
        val n31 = te[2]; val n32 = te[6]; val n33 = te[10]; val n34 = te[14]
        val n41 = te[3]; val n42 = te[7]; val n43 = te[11]; val n44 = te[15]

        return (
            n41 * (+n14 * n23 * n32 - n13 * n24 * n32 - n14 * n22 * n33 + n12 * n24 * n33 + n13 * n22 * n34 - n12 * n23 * n34) +
            n42 * (+n11 * n23 * n34 - n11 * n24 * n33 + n14 * n21 * n33 - n13 * n21 * n34 + n13 * n24 * n31 - n14 * n23 * n31) +
            n43 * (+n11 * n24 * n32 - n11 * n22 * n34 - n14 * n21 * n32 + n12 * n21 * n34 + n14 * n22 * n31 - n12 * n24 * n31) +
            n44 * (-n13 * n22 * n31 - n11 * n23 * n32 + n11 * n22 * n33 + n13 * n21 * n32 - n12 * n21 * n33 + n12 * n23 * n31)
        )
    }

    fun invert(): Matrix4 {
        val te = elements
        val n11 = te[0]; val n21 = te[1]; val n31 = te[2]; val n41 = te[3]
        val n12 = te[4]; val n22 = te[5]; val n32 = te[6]; val n42 = te[7]
        val n13 = te[8]; val n23 = te[9]; val n33 = te[10]; val n43 = te[11]
        val n14 = te[12]; val n24 = te[13]; val n34 = te[14]; val n44 = te[15]

        val t11 = n23 * n34 * n42 - n24 * n33 * n42 + n24 * n32 * n43 - n22 * n34 * n43 - n23 * n32 * n44 + n22 * n33 * n44
        val t12 = n14 * n33 * n42 - n13 * n34 * n42 - n14 * n32 * n43 + n12 * n34 * n43 + n13 * n32 * n44 - n12 * n33 * n44
        val t13 = n13 * n24 * n42 - n14 * n23 * n42 + n14 * n22 * n43 - n12 * n24 * n43 - n13 * n22 * n44 + n12 * n23 * n44
        val t14 = n14 * n23 * n32 - n13 * n24 * n32 - n14 * n22 * n33 + n12 * n24 * n33 + n13 * n22 * n34 - n12 * n23 * n34

        val det = n11 * t11 + n21 * t12 + n31 * t13 + n41 * t14
        if (det == 0f) return set(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

        val detInv = 1f / det

        te[0] = t11 * detInv
        te[1] = (n24 * n33 * n41 - n23 * n34 * n41 - n24 * n31 * n43 + n21 * n34 * n43 + n23 * n31 * n44 - n21 * n33 * n44) * detInv
        te[2] = (n22 * n34 * n41 - n24 * n32 * n41 + n24 * n31 * n42 - n21 * n34 * n42 - n22 * n31 * n44 + n21 * n32 * n44) * detInv
        te[3] = (n23 * n32 * n41 - n22 * n33 * n41 - n23 * n31 * n42 + n21 * n33 * n42 + n22 * n31 * n43 - n21 * n32 * n43) * detInv

        te[4] = t12 * detInv
        te[5] = (n13 * n34 * n41 - n14 * n33 * n41 + n14 * n31 * n43 - n11 * n34 * n43 - n13 * n31 * n44 + n11 * n33 * n44) * detInv
        te[6] = (n14 * n32 * n41 - n12 * n34 * n41 - n14 * n31 * n42 + n11 * n34 * n42 + n12 * n31 * n44 - n11 * n32 * n44) * detInv
        te[7] = (n12 * n33 * n41 - n13 * n32 * n41 + n13 * n31 * n42 - n11 * n33 * n42 - n12 * n31 * n43 + n11 * n32 * n43) * detInv

        te[8] = t13 * detInv
        te[9] = (n14 * n23 * n41 - n13 * n24 * n41 - n14 * n21 * n43 + n11 * n24 * n43 + n13 * n21 * n44 - n11 * n23 * n44) * detInv
        te[10] = (n12 * n24 * n41 - n14 * n22 * n41 + n14 * n21 * n42 - n11 * n24 * n42 - n12 * n21 * n44 + n11 * n22 * n44) * detInv
        te[11] = (n13 * n22 * n41 - n12 * n23 * n41 - n13 * n21 * n42 + n11 * n23 * n42 + n12 * n21 * n43 - n11 * n22 * n43) * detInv

        te[12] = t14 * detInv
        te[13] = (n13 * n24 * n31 - n14 * n23 * n31 + n14 * n21 * n33 - n11 * n24 * n33 - n13 * n21 * n34 + n11 * n23 * n34) * detInv
        te[14] = (n14 * n22 * n31 - n12 * n24 * n31 - n14 * n21 * n32 + n11 * n24 * n32 + n12 * n21 * n34 - n11 * n22 * n34) * detInv
        te[15] = (n12 * n23 * n31 - n13 * n22 * n31 + n13 * n21 * n32 - n11 * n23 * n32 - n12 * n21 * n33 + n11 * n22 * n33) * detInv

        return this
    }

    fun transpose(): Matrix4 {
        val te = elements
        var tmp: Float
        tmp = te[1]; te[1] = te[4]; te[4] = tmp
        tmp = te[2]; te[2] = te[8]; te[8] = tmp
        tmp = te[6]; te[6] = te[9]; te[9] = tmp
        tmp = te[3]; te[3] = te[12]; te[12] = tmp
        tmp = te[7]; te[7] = te[13]; te[13] = tmp
        tmp = te[11]; te[11] = te[14]; te[14] = tmp
        return this
    }

    fun getMaxScaleOnAxis(): Float {
        val te = elements
        val scaleXSq = te[0] * te[0] + te[1] * te[1] + te[2] * te[2]
        val scaleYSq = te[4] * te[4] + te[5] * te[5] + te[6] * te[6]
        val scaleZSq = te[8] * te[8] + te[9] * te[9] + te[10] * te[10]
        return sqrt(maxOf(scaleXSq, scaleYSq, scaleZSq))
    }
}
