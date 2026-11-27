package com.nexustools.genericui.ext.svg

/**
 * SVG `viewBox` rectangle (`minX`, `minY`, `width`, `height`).
 */
data class SvgViewBox(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float
) {
    fun toSvgString(): String =
        "${SvgPoint.formatSvgNumber(minX)} ${SvgPoint.formatSvgNumber(minY)} " +
            "${SvgPoint.formatSvgNumber(width)} ${SvgPoint.formatSvgNumber(height)}"

    companion object {
        fun parse(raw: String?): SvgViewBox? {
            if (raw.isNullOrBlank()) return null
            val nums = SvgPoint.parseNumberList(raw)
            if (nums.size < 4) return null
            val w = nums[2]
            val h = nums[3]
            if (w <= 0f || h <= 0f) return null
            return SvgViewBox(nums[0], nums[1], w, h)
        }
    }
}
