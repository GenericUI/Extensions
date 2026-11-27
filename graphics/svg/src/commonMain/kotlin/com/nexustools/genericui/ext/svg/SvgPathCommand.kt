package com.nexustools.genericui.ext.svg

/**
 * Typed W3C SVG `<path d="...">` command hierarchy.
 */
sealed class SvgPathCommand {
    abstract fun toSvgString(): String

    data class MoveTo(val x: Float, val y: Float, val isRelative: Boolean = false) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 'm' else 'M'} ${SvgPoint.formatSvgNumber(x)} ${SvgPoint.formatSvgNumber(y)}"
    }

    data class LineTo(val x: Float, val y: Float, val isRelative: Boolean = false) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 'l' else 'L'} ${SvgPoint.formatSvgNumber(x)} ${SvgPoint.formatSvgNumber(y)}"
    }

    data class HorizontalLineTo(val x: Float, val isRelative: Boolean = false) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 'h' else 'H'} ${SvgPoint.formatSvgNumber(x)}"
    }

    data class VerticalLineTo(val y: Float, val isRelative: Boolean = false) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 'v' else 'V'} ${SvgPoint.formatSvgNumber(y)}"
    }

    data class CubicBezierTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
        val isRelative: Boolean = false
    ) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 'c' else 'C'} ${SvgPoint.formatSvgNumber(x1)} ${SvgPoint.formatSvgNumber(y1)} " +
                "${SvgPoint.formatSvgNumber(x2)} ${SvgPoint.formatSvgNumber(y2)} " +
                "${SvgPoint.formatSvgNumber(x)} ${SvgPoint.formatSvgNumber(y)}"
    }

    data class SmoothCubicBezierTo(
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
        val isRelative: Boolean = false
    ) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 's' else 'S'} ${SvgPoint.formatSvgNumber(x2)} ${SvgPoint.formatSvgNumber(y2)} " +
                "${SvgPoint.formatSvgNumber(x)} ${SvgPoint.formatSvgNumber(y)}"
    }

    data class QuadraticBezierTo(
        val x1: Float,
        val y1: Float,
        val x: Float,
        val y: Float,
        val isRelative: Boolean = false
    ) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 'q' else 'Q'} ${SvgPoint.formatSvgNumber(x1)} ${SvgPoint.formatSvgNumber(y1)} " +
                "${SvgPoint.formatSvgNumber(x)} ${SvgPoint.formatSvgNumber(y)}"
    }

    data class SmoothQuadraticBezierTo(
        val x: Float,
        val y: Float,
        val isRelative: Boolean = false
    ) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 't' else 'T'} ${SvgPoint.formatSvgNumber(x)} ${SvgPoint.formatSvgNumber(y)}"
    }

    data class ArcTo(
        val rx: Float,
        val ry: Float,
        val xAxisRotation: Float,
        val largeArcFlag: Boolean,
        val sweepFlag: Boolean,
        val x: Float,
        val y: Float,
        val isRelative: Boolean = false
    ) : SvgPathCommand() {
        override fun toSvgString(): String =
            "${if (isRelative) 'a' else 'A'} ${SvgPoint.formatSvgNumber(rx)} ${SvgPoint.formatSvgNumber(ry)} " +
                "${SvgPoint.formatSvgNumber(xAxisRotation)} ${if (largeArcFlag) 1 else 0} ${if (sweepFlag) 1 else 0} " +
                "${SvgPoint.formatSvgNumber(x)} ${SvgPoint.formatSvgNumber(y)}"
    }

    data object ClosePath : SvgPathCommand() {
        override fun toSvgString(): String = "Z"
    }
}
