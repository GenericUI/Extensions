package com.nexustools.genericui.ext.svg

import kotlin.math.max
import kotlin.math.min

/**
 * SVG `preserveAspectRatio` alignment and scaling policy (`xMidYMid meet`, `none`, etc.).
 */
data class SvgPreserveAspectRatio(
    val align: Align = Align.X_MID_Y_MID,
    val meetOrSlice: MeetOrSlice = MeetOrSlice.MEET
) {
    enum class Align(val xmlValue: String) {
        NONE("none"),
        X_MIN_Y_MIN("xMinYMin"),
        X_MID_Y_MIN("xMidYMin"),
        X_MAX_Y_MIN("xMaxYMin"),
        X_MIN_Y_MID("xMinYMid"),
        X_MID_Y_MID("xMidYMid"),
        X_MAX_Y_MID("xMaxYMid"),
        X_MIN_Y_MAX("xMinYMax"),
        X_MID_Y_MAX("xMidYMax"),
        X_MAX_Y_MAX("xMaxYMax");

        companion object {
            fun fromXml(raw: String): Align? =
                entries.firstOrNull { it.xmlValue.equals(raw, ignoreCase = true) }
        }
    }

    enum class MeetOrSlice(val xmlValue: String) {
        MEET("meet"),
        SLICE("slice");

        companion object {
            fun fromXml(raw: String): MeetOrSlice? =
                entries.firstOrNull { it.xmlValue.equals(raw, ignoreCase = true) }
        }
    }

    fun toSvgString(): String =
        if (align == Align.NONE) Align.NONE.xmlValue else "${align.xmlValue} ${meetOrSlice.xmlValue}"

    /**
     * Computes an [SvgTransform.AffineMatrix] mapping [viewBox] user coordinates into a viewport
     * of `(viewportWidth, viewportHeight)`.
     */
    fun computeViewportMatrix(
        viewBox: SvgViewBox,
        viewportWidth: Float,
        viewportHeight: Float
    ): SvgTransform.AffineMatrix {
        if (viewBox.width <= 0f || viewBox.height <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
            return SvgTransform.AffineMatrix.IDENTITY
        }
        val rawSx = viewportWidth / viewBox.width
        val rawSy = viewportHeight / viewBox.height
        if (align == Align.NONE) {
            return SvgTransform.AffineMatrix(
                a = rawSx,
                b = 0f,
                c = 0f,
                d = rawSy,
                e = -viewBox.minX * rawSx,
                f = -viewBox.minY * rawSy
            )
        }
        val scale = when (meetOrSlice) {
            MeetOrSlice.MEET -> min(rawSx, rawSy)
            MeetOrSlice.SLICE -> max(rawSx, rawSy)
        }
        val extraX = viewportWidth - viewBox.width * scale
        val extraY = viewportHeight - viewBox.height * scale
        val offsetX = when (align) {
            Align.X_MIN_Y_MIN, Align.X_MIN_Y_MID, Align.X_MIN_Y_MAX -> 0f
            Align.X_MID_Y_MIN, Align.X_MID_Y_MID, Align.X_MID_Y_MAX -> extraX * 0.5f
            Align.X_MAX_Y_MIN, Align.X_MAX_Y_MID, Align.X_MAX_Y_MAX -> extraX
            Align.NONE -> 0f
        }
        val offsetY = when (align) {
            Align.X_MIN_Y_MIN, Align.X_MID_Y_MIN, Align.X_MAX_Y_MIN -> 0f
            Align.X_MIN_Y_MID, Align.X_MID_Y_MID, Align.X_MAX_Y_MID -> extraY * 0.5f
            Align.X_MIN_Y_MAX, Align.X_MID_Y_MAX, Align.X_MAX_Y_MAX -> extraY
            Align.NONE -> 0f
        }
        return SvgTransform.AffineMatrix(
            a = scale,
            b = 0f,
            c = 0f,
            d = scale,
            e = offsetX - viewBox.minX * scale,
            f = offsetY - viewBox.minY * scale
        )
    }

    companion object {
        val DEFAULT = SvgPreserveAspectRatio(Align.X_MID_Y_MID, MeetOrSlice.MEET)
        val NONE = SvgPreserveAspectRatio(Align.NONE, MeetOrSlice.MEET)

        fun parse(raw: String?): SvgPreserveAspectRatio? {
            if (raw.isNullOrBlank()) return null
            val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            var idx = 0
            if (tokens[0].equals("defer", ignoreCase = true)) {
                idx++
            }
            if (idx >= tokens.size) return null
            val align = Align.fromXml(tokens[idx]) ?: return null
            if (align == Align.NONE) return NONE
            val mos = tokens.getOrNull(idx + 1)?.let { MeetOrSlice.fromXml(it) } ?: MeetOrSlice.MEET
            return SvgPreserveAspectRatio(align, mos)
        }
    }
}
