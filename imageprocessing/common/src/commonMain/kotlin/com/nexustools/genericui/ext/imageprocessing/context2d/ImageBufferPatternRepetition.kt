package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Strongly-typed enum representing HTML5 2D Context pattern repetition modes (`createPattern`).
 */
enum class ImageBufferPatternRepetition(
    val gpuCode: Int,
    val webValue: String
) {
    REPEAT(0, "repeat"),
    REPEAT_X(1, "repeat-x"),
    REPEAT_Y(2, "repeat-y"),
    NO_REPEAT(3, "no-repeat");

    companion object {
        fun fromWebValue(value: String): ImageBufferPatternRepetition {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: REPEAT
        }
    }
}
