package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context font kerning mode (`fontKerning`).
 */
enum class ImageBufferFontKerning(
    val gpuCode: Int,
    val webValue: String
) {
    AUTO(0, "auto"),
    NORMAL(1, "normal"),
    NONE(2, "none");

    companion object {
        fun fromWebValue(value: String): ImageBufferFontKerning {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: AUTO
        }
    }
}
