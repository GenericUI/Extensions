package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing fill/stroke paint style kinds on an [ImageBuffer2DContext].
 */
enum class ImageBufferPaintStyleKind(val gpuCode: Int) {
    SOLID_COLOR(0),
    GRADIENT(1),
    PATTERN(2)
}
