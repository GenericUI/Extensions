package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context text rendering hints (`textRendering`).
 */
enum class ImageBufferTextRendering(
    val gpuCode: Int,
    val webValue: String
) {
    AUTO(0, "auto"),
    OPTIMIZE_SPEED(1, "optimizeSpeed"),
    OPTIMIZE_LEGIBILITY(2, "optimizeLegibility"),
    GEOMETRIC_PRECISION(3, "geometricPrecision");

    companion object {
        fun fromWebValue(value: String): ImageBufferTextRendering {
            val normalized = value.trim()
            return entries.firstOrNull { it.webValue.equals(normalized, ignoreCase = true) } ?: AUTO
        }
    }
}
