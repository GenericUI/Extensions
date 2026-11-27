package com.nexustools.genericui.ext.svg

/**
 * 2D point in SVG user-space coordinates.
 */
data class SvgPoint(val x: Float, val y: Float) {
    companion object {
        /**
         * Parses an SVG `points` attribute string (e.g. `"0,10 20,30 40,10"`) into [SvgPoint]s.
         */
        fun parsePoints(raw: String?): List<SvgPoint> {
            if (raw.isNullOrBlank()) return emptyList()
            val numbers = parseNumberList(raw)
            val result = ArrayList<SvgPoint>(numbers.size / 2)
            var i = 0
            while (i + 1 < numbers.size) {
                result.add(SvgPoint(numbers[i], numbers[i + 1]))
                i += 2
            }
            return result
        }

        /**
         * Formats a list of [SvgPoint]s as an SVG `points` attribute string.
         */
        fun formatPoints(points: List<SvgPoint>): String =
            points.joinToString(" ") { "${formatSvgNumber(it.x)},${formatSvgNumber(it.y)}" }

        internal fun parseNumberList(raw: String): List<Float> {
            val out = ArrayList<Float>()
            val len = raw.length
            var idx = 0
            while (idx < len) {
                while (idx < len && (raw[idx].isWhitespace() || raw[idx] == ',')) {
                    idx++
                }
                if (idx >= len) break
                val start = idx
                if (raw[idx] == '+' || raw[idx] == '-') {
                    idx++
                }
                var hasDigits = false
                while (idx < len && raw[idx] in '0'..'9') {
                    hasDigits = true
                    idx++
                }
                if (idx < len && raw[idx] == '.') {
                    idx++
                    while (idx < len && raw[idx] in '0'..'9') {
                        hasDigits = true
                        idx++
                    }
                }
                if (hasDigits && idx < len && (raw[idx] == 'e' || raw[idx] == 'E')) {
                    val expStart = idx
                    idx++
                    if (idx < len && (raw[idx] == '+' || raw[idx] == '-')) {
                        idx++
                    }
                    var expDigits = false
                    while (idx < len && raw[idx] in '0'..'9') {
                        expDigits = true
                        idx++
                    }
                    if (!expDigits) {
                        idx = expStart
                    }
                }
                if (hasDigits && idx > start) {
                    raw.substring(start, idx).toFloatOrNull()?.let { out.add(it) }
                } else {
                    idx++
                }
            }
            return out
        }

        internal fun formatSvgNumber(value: Float): String {
            if (!value.isFinite()) return "0"
            val rounded = kotlin.math.round(value)
            if (kotlin.math.abs(value - rounded) < 1e-5f) {
                return rounded.toInt().toString()
            }
            val scaled = kotlin.math.round(value * 10000f) / 10000f
            val str = scaled.toString()
            return if ('.' in str) str.trimEnd('0').trimEnd('.') else str
        }
    }
}
