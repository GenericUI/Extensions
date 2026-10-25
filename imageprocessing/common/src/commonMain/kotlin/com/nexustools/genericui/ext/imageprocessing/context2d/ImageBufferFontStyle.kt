package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context font posture (`normal`, `italic`, `oblique`).
 */
enum class ImageBufferFontStyle(
    val gpuCode: Int,
    val webValue: String
) {
    NORMAL(0, "normal"),
    ITALIC(1, "italic"),
    OBLIQUE(2, "oblique");

    companion object {
        fun fromWebValue(value: String): ImageBufferFontStyle {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: NORMAL
        }
    }
}
