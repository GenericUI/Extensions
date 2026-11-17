package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context font stretch (`fontStretch`).
 */
enum class ImageBufferFontStretch(
    val widthScale: Float,
    val webValue: String
) {
    ULTRA_CONDENSED(0.5f, "ultra-condensed"),
    EXTRA_CONDENSED(0.625f, "extra-condensed"),
    CONDENSED(0.75f, "condensed"),
    SEMI_CONDENSED(0.875f, "semi-condensed"),
    NORMAL(1.0f, "normal"),
    SEMI_EXPANDED(1.125f, "semi-expanded"),
    EXPANDED(1.25f, "expanded"),
    EXTRA_EXPANDED(1.5f, "extra-expanded"),
    ULTRA_EXPANDED(2.0f, "ultra-expanded");

    companion object {
        fun fromWebValue(value: String): ImageBufferFontStretch {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: NORMAL
        }
    }
}
