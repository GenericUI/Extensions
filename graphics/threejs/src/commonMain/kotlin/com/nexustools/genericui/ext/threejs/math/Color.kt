package com.nexustools.genericui.ext.threejs.math

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Linear/sRGB floating-point color (`THREE.Color`).
 */
data class Color(
    var r: Float = 1f,
    var g: Float = 1f,
    var b: Float = 1f
) {
    constructor(hex: Int) : this() {
        setHex(hex)
    }

    constructor(hexString: String) : this() {
        setStyle(hexString)
    }

    fun set(r: Float, g: Float, b: Float): Color {
        this.r = r
        this.g = g
        this.b = b
        return this
    }

    fun setHex(hex: Int): Color {
        r = ((hex shr 16) and 255) / 255f
        g = ((hex shr 8) and 255) / 255f
        b = (hex and 255) / 255f
        return this
    }

    fun getHex(): Int {
        val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (ri shl 16) or (gi shl 8) or bi
    }

    fun setStyle(style: String): Color {
        val rgba = RgbaColor.fromHex(style)
        r = rgba.r / 255f
        g = rgba.g / 255f
        b = rgba.b / 255f
        return this
    }

    fun copy(color: Color): Color = set(color.r, color.g, color.b)

    fun clone(): Color = Color(r, g, b)

    fun multiplyScalar(s: Float): Color {
        r *= s
        g *= s
        b *= s
        return this
    }

    fun lerp(color: Color, alpha: Float): Color {
        r += (color.r - r) * alpha
        g += (color.g - g) * alpha
        b += (color.b - b) * alpha
        return this
    }

    fun toRgbaColor(alpha: Float = 1f): RgbaColor = RgbaColor(
        r = (r.coerceIn(0f, 1f) * 255f).toInt(),
        g = (g.coerceIn(0f, 1f) * 255f).toInt(),
        b = (b.coerceIn(0f, 1f) * 255f).toInt(),
        a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    )
}
