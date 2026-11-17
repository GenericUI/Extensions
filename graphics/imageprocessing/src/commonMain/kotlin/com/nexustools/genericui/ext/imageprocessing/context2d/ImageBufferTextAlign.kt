package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context horizontal text alignment (`textAlign`).
 */
enum class ImageBufferTextAlign(
    val gpuCode: Int,
    val webValue: String
) {
    START(0, "start"),
    END(1, "end"),
    LEFT(2, "left"),
    RIGHT(3, "right"),
    CENTER(4, "center");

    companion object {
        fun fromWebValue(value: String): ImageBufferTextAlign {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: START
        }
    }
}
