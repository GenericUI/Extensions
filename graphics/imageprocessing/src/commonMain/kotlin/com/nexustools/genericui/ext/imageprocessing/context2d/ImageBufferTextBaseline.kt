package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context vertical text baseline alignment (`textBaseline`).
 */
enum class ImageBufferTextBaseline(
    val gpuCode: Int,
    val webValue: String
) {
    TOP(0, "top"),
    HANGING(1, "hanging"),
    MIDDLE(2, "middle"),
    ALPHABETIC(3, "alphabetic"),
    IDEOGRAPHIC(4, "ideographic"),
    BOTTOM(5, "bottom");

    companion object {
        fun fromWebValue(value: String): ImageBufferTextBaseline {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: ALPHABETIC
        }
    }
}
