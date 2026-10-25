package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context gradient types on an [ImageBuffer2DContext].
 */
enum class ImageBufferGradientKind(val gpuCode: Int) {
    LINEAR(0),
    RADIAL(1),
    CONIC(2)
}
