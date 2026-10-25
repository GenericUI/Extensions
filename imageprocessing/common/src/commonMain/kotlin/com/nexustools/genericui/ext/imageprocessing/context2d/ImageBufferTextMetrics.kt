package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed `TextMetrics` equivalent returned by `ImageBuffer2DContext.measureText(text)`.
 */
data class ImageBufferTextMetrics(
    val width: Float,
    val actualBoundingBoxLeft: Float,
    val actualBoundingBoxRight: Float,
    val fontBoundingBoxAscent: Float,
    val fontBoundingBoxDescent: Float,
    val actualBoundingBoxAscent: Float,
    val actualBoundingBoxDescent: Float,
    val emHeightAscent: Float,
    val emHeightDescent: Float,
    val hangingBaseline: Float,
    val alphabeticBaseline: Float,
    val ideographicBaseline: Float
)
