package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context line cap styles (`lineCap`) on an [ImageBuffer2DContext].
 */
enum class ImageBufferLineCap(
    val gpuCode: Int,
    val webValue: String
) {
    BUTT(0, "butt"),
    ROUND(1, "round"),
    SQUARE(2, "square");

    companion object {
        fun fromWebValue(value: String): ImageBufferLineCap {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: BUTT
        }
    }
}
