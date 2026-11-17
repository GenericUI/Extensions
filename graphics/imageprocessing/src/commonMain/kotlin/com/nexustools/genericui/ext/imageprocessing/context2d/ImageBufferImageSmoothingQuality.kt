package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context image smoothing quality (`imageSmoothingQuality`).
 */
enum class ImageBufferImageSmoothingQuality(
    val gpuCode: Int,
    val webValue: String
) {
    LOW(0, "low"),
    MEDIUM(1, "medium"),
    HIGH(2, "high");

    companion object {
        fun fromWebValue(value: String): ImageBufferImageSmoothingQuality {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: HIGH
        }
    }
}
