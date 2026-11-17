package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context text direction (`direction`).
 */
enum class ImageBufferTextDirection(
    val gpuCode: Int,
    val webValue: String
) {
    INHERIT(0, "inherit"),
    LTR(1, "ltr"),
    RTL(2, "rtl");

    companion object {
        fun fromWebValue(value: String): ImageBufferTextDirection {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: INHERIT
        }
    }
}
