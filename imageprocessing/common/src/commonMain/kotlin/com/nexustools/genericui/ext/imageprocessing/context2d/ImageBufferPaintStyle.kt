package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor

/**
 * Strongly-typed fill or stroke paint descriptor for [ImageBuffer2DContext] (`fillStyle` / `strokeStyle`),
 * representing a [RgbaColor], an [ImageBufferGradient2D], or an [ImageBufferPattern2D].
 */
class ImageBufferPaintStyle(
    val kind: ImageBufferPaintStyleKind,
    val color: RgbaColor = RgbaColor.BLACK,
    val gradient: ImageBufferGradient2D? = null,
    val pattern: ImageBufferPattern2D? = null
) {
    /**
     * Evaluates this paint style at local coordinates `(px, py)`.
     */
    fun sampleAt(px: Float, py: Float, smoothing: Boolean = true): RgbaColor =
        when (kind) {
            ImageBufferPaintStyleKind.SOLID_COLOR -> color
            ImageBufferPaintStyleKind.GRADIENT -> gradient?.sampleAt(px, py) ?: color
            ImageBufferPaintStyleKind.PATTERN -> pattern?.sampleAt(px, py, smoothing) ?: color
        }

    companion object {
        val BLACK: ImageBufferPaintStyle = solid(RgbaColor.BLACK)

        fun solid(color: RgbaColor): ImageBufferPaintStyle =
            ImageBufferPaintStyle(kind = ImageBufferPaintStyleKind.SOLID_COLOR, color = color)

        fun gradient(gradient: ImageBufferGradient2D): ImageBufferPaintStyle =
            ImageBufferPaintStyle(
                kind = ImageBufferPaintStyleKind.GRADIENT,
                color = gradient.stopColors.firstOrNull() ?: RgbaColor.BLACK,
                gradient = gradient
            )

        fun pattern(pattern: ImageBufferPattern2D): ImageBufferPaintStyle =
            ImageBufferPaintStyle(
                kind = ImageBufferPaintStyleKind.PATTERN,
                color = RgbaColor.WHITE,
                pattern = pattern
            )
    }
}
