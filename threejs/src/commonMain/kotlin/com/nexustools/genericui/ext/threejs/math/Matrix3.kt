package com.nexustools.genericui.ext.threejs.math

/**
 * 3x3 column-major matrix (`THREE.Matrix3`).
 */
class Matrix3 {
    val elements: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    fun set(
        n11: Float, n12: Float, n13: Float,
        n21: Float, n22: Float, n23: Float,
        n31: Float, n32: Float, n33: Float
    ): Matrix3 {
        val te = elements
        te[0] = n11; te[3] = n12; te[6] = n13
        te[1] = n21; te[4] = n22; te[7] = n23
        te[2] = n31; te[5] = n32; te[8] = n33
        return this
    }

    fun identity(): Matrix3 = set(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    fun copy(m: Matrix3): Matrix3 {
        m.elements.copyInto(elements)
        return this
    }

    fun clone(): Matrix3 = Matrix3().copy(this)

    fun setFromMatrix4(m: Matrix4): Matrix3 {
        val me = m.elements
        return set(
            me[0], me[4], me[8],
            me[1], me[5], me[9],
            me[2], me[6], me[10]
        )
    }

    fun determinant(): Float {
        val te = elements
        val a = te[0]; val b = te[1]; val c = te[2]
        val d = te[3]; val e = te[4]; val f = te[5]
        val g = te[6]; val h = te[7]; val i = te[8]
        return a * e * i - a * f * h - b * d * i + b * f * g + c * d * h - c * e * g
    }

    fun invert(): Matrix3 {
        val te = elements
        val n11 = te[0]; val n21 = te[1]; val n31 = te[2]
        val n12 = te[3]; val n22 = te[4]; val n32 = te[5]
        val n13 = te[6]; val n23 = te[7]; val n33 = te[8]

        val t11 = n33 * n22 - n32 * n23
        val t12 = n32 * n13 - n33 * n12
        val t13 = n23 * n12 - n22 * n13

        val det = n11 * t11 + n21 * t12 + n31 * t13
        if (det == 0f) return set(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

        val detInv = 1f / det
        te[0] = t11 * detInv
        te[1] = (n31 * n23 - n33 * n21) * detInv
        te[2] = (n32 * n21 - n31 * n22) * detInv
        te[3] = t12 * detInv
        te[4] = (n33 * n11 - n31 * n13) * detInv
        te[5] = (n31 * n12 - n32 * n11) * detInv
        te[6] = t13 * detInv
        te[7] = (n21 * n13 - n23 * n11) * detInv
        te[8] = (n22 * n11 - n21 * n12) * detInv
        return this
    }

    fun transpose(): Matrix3 {
        var tmp: Float
        val m = elements
        tmp = m[1]; m[1] = m[3]; m[3] = tmp
        tmp = m[2]; m[2] = m[6]; m[6] = tmp
        tmp = m[5]; m[5] = m[7]; m[7] = tmp
        return this
    }

    fun getNormalMatrix(matrix4: Matrix4): Matrix3 =
        setFromMatrix4(matrix4).invert().transpose()
}
