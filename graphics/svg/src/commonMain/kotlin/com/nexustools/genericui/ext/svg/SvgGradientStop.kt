package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.style.color.RgbaColor

/**
 * SVG `<stop>` gradient color stop (`offset`, `color`, `opacity`).
 */
class SvgGradientStop(
    offset: Float = 0f,
    color: RgbaColor = RgbaColor.BLACK,
    opacity: Float = 1f
) {
    internal var ownerGradient: SvgElement? = null

    var offset: Float = offset.coerceIn(0f, 1f)
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field == clamped) return
            field = clamped
            ownerGradient?.ownerSvg?.markVectorSceneDirty()
        }

    var color: RgbaColor = color
        set(value) {
            if (field == value) return
            field = value
            ownerGradient?.ownerSvg?.markVectorSceneDirty()
        }

    var opacity: Float = opacity.coerceIn(0f, 1f)
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field == clamped) return
            field = clamped
            ownerGradient?.ownerSvg?.markVectorSceneDirty()
        }

    fun copy(
        offset: Float = this.offset,
        color: RgbaColor = this.color,
        opacity: Float = this.opacity
    ): SvgGradientStop = SvgGradientStop(offset, color, opacity)

    override fun equals(other: Any?): Boolean =
        other is SvgGradientStop &&
            offset == other.offset &&
            color == other.color &&
            opacity == other.opacity

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + opacity.hashCode()
        return result
    }
}
