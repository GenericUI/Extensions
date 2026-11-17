package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context line join styles (`lineJoin`) on an [ImageBuffer2DContext].
 */
enum class ImageBufferLineJoin(
    val gpuCode: Int,
    val webValue: String
) {
    MITER(0, "miter"),
    ROUND(1, "round"),
    BEVEL(2, "bevel");

    companion object {
        fun fromWebValue(value: String): ImageBufferLineJoin {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: MITER
        }
    }
}
