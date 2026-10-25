package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context path winding fill rules (`nonzero` / `evenodd`).
 */
enum class ImageBufferFillRule(
    val gpuCode: Int,
    val webValue: String
) {
    NONZERO(0, "nonzero"),
    EVENODD(1, "evenodd");

    companion object {
        fun fromWebValue(value: String): ImageBufferFillRule {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: NONZERO
        }
    }
}
