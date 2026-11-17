package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context font variant caps (`fontVariantCaps`).
 */
enum class ImageBufferFontVariantCaps(
    val gpuCode: Int,
    val webValue: String
) {
    NORMAL(0, "normal"),
    SMALL_CAPS(1, "small-caps"),
    ALL_SMALL_CAPS(2, "all-small-caps"),
    PETITE_CAPS(3, "petite-caps"),
    ALL_PETITE_CAPS(4, "all-petite-caps"),
    UNICASE(5, "unicase"),
    TITLING_CAPS(6, "titling-caps");

    companion object {
        fun fromWebValue(value: String): ImageBufferFontVariantCaps {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: NORMAL
        }
    }
}
