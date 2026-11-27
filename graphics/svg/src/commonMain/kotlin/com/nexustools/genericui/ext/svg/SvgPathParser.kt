package com.nexustools.genericui.ext.svg

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Full W3C SVG `<path d="...">` parser and curve-flattening tessellator supporting implicit
 * repeated coordinates, compact arc flags (`0`/`1`), `M/m/L/l/H/h/V/v/C/c/S/s/Q/q/T/t/A/a/Z/z`,
 * adaptive cubic/quadratic subdivision, and elliptical arc parameterization.
 */
object SvgPathParser {

    data class Subpath(
        val points: List<SvgPoint>,
        val closed: Boolean
    )

    fun parseCommands(d: String): List<SvgPathCommand> {
        if (d.isBlank()) return emptyList()
        val reader = PathTokenReader(d)
        val commands = ArrayList<SvgPathCommand>()
        var currentCmd = '\u0000'

        while (reader.hasMore()) {
            reader.skipSeparators()
            if (!reader.hasMore()) break
            val ch = reader.peek()
            if (isCommandLetter(ch)) {
                currentCmd = ch
                reader.advance()
                if (currentCmd == 'Z' || currentCmd == 'z') {
                    commands.add(SvgPathCommand.ClosePath)
                    currentCmd = '\u0000'
                }
                continue
            }

            if (currentCmd == '\u0000') {
                reader.advance()
                continue
            }

            val isRel = currentCmd.isLowerCase()
            when (currentCmd.uppercaseChar()) {
                'M' -> {
                    val x = reader.readNumber() ?: break
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.MoveTo(x, y, isRel))
                    currentCmd = if (isRel) 'l' else 'L'
                }
                'L' -> {
                    val x = reader.readNumber() ?: break
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.LineTo(x, y, isRel))
                }
                'H' -> {
                    val x = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.HorizontalLineTo(x, isRel))
                }
                'V' -> {
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.VerticalLineTo(y, isRel))
                }
                'C' -> {
                    val x1 = reader.readNumber() ?: break
                    val y1 = reader.readNumber() ?: break
                    val x2 = reader.readNumber() ?: break
                    val y2 = reader.readNumber() ?: break
                    val x = reader.readNumber() ?: break
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.CubicBezierTo(x1, y1, x2, y2, x, y, isRel))
                }
                'S' -> {
                    val x2 = reader.readNumber() ?: break
                    val y2 = reader.readNumber() ?: break
                    val x = reader.readNumber() ?: break
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.SmoothCubicBezierTo(x2, y2, x, y, isRel))
                }
                'Q' -> {
                    val x1 = reader.readNumber() ?: break
                    val y1 = reader.readNumber() ?: break
                    val x = reader.readNumber() ?: break
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.QuadraticBezierTo(x1, y1, x, y, isRel))
                }
                'T' -> {
                    val x = reader.readNumber() ?: break
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.SmoothQuadraticBezierTo(x, y, isRel))
                }
                'A' -> {
                    val rx = reader.readNumber() ?: break
                    val ry = reader.readNumber() ?: break
                    val rot = reader.readNumber() ?: break
                    val largeArc = reader.readFlag() ?: break
                    val sweep = reader.readFlag() ?: break
                    val x = reader.readNumber() ?: break
                    val y = reader.readNumber() ?: break
                    commands.add(SvgPathCommand.ArcTo(rx, ry, rot, largeArc, sweep, x, y, isRel))
                }
                else -> break
            }
        }
        return commands
    }

    fun formatCommands(commands: List<SvgPathCommand>): String =
        commands.joinToString(" ") { it.toSvgString() }

    fun flattenSubpaths(
        commands: List<SvgPathCommand>,
        tolerance: Float = 0.25f
    ): List<Subpath> {
        if (commands.isEmpty()) return emptyList()
        val safeTol = tolerance.coerceAtLeast(1e-4f)
        val subpaths = ArrayList<Subpath>()
        var currentPoints = ArrayList<SvgPoint>()
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var lastCtrlX = 0f
        var lastCtrlY = 0f
        var lastCmdKind = '\u0000'

        fun flushOpen() {
            if (currentPoints.isNotEmpty()) {
                subpaths.add(Subpath(currentPoints, closed = false))
                currentPoints = ArrayList()
            }
        }

        fun ensureSubpathStarted() {
            if (currentPoints.isEmpty()) {
                currentPoints.add(SvgPoint(curX, curY))
                startX = curX
                startY = curY
            }
        }

        for (cmd in commands) {
            when (cmd) {
                is SvgPathCommand.MoveTo -> {
                    flushOpen()
                    val nx = if (cmd.isRelative) curX + cmd.x else cmd.x
                    val ny = if (cmd.isRelative) curY + cmd.y else cmd.y
                    curX = nx
                    curY = ny
                    startX = nx
                    startY = ny
                    currentPoints.add(SvgPoint(nx, ny))
                    lastCmdKind = 'M'
                }
                is SvgPathCommand.LineTo -> {
                    ensureSubpathStarted()
                    val nx = if (cmd.isRelative) curX + cmd.x else cmd.x
                    val ny = if (cmd.isRelative) curY + cmd.y else cmd.y
                    curX = nx
                    curY = ny
                    currentPoints.add(SvgPoint(nx, ny))
                    lastCmdKind = 'L'
                }
                is SvgPathCommand.HorizontalLineTo -> {
                    ensureSubpathStarted()
                    val nx = if (cmd.isRelative) curX + cmd.x else cmd.x
                    curX = nx
                    currentPoints.add(SvgPoint(nx, curY))
                    lastCmdKind = 'H'
                }
                is SvgPathCommand.VerticalLineTo -> {
                    ensureSubpathStarted()
                    val ny = if (cmd.isRelative) curY + cmd.y else cmd.y
                    curY = ny
                    currentPoints.add(SvgPoint(curX, ny))
                    lastCmdKind = 'V'
                }
                is SvgPathCommand.CubicBezierTo -> {
                    ensureSubpathStarted()
                    val x1 = if (cmd.isRelative) curX + cmd.x1 else cmd.x1
                    val y1 = if (cmd.isRelative) curY + cmd.y1 else cmd.y1
                    val x2 = if (cmd.isRelative) curX + cmd.x2 else cmd.x2
                    val y2 = if (cmd.isRelative) curY + cmd.y2 else cmd.y2
                    val x = if (cmd.isRelative) curX + cmd.x else cmd.x
                    val y = if (cmd.isRelative) curY + cmd.y else cmd.y
                    flattenCubic(curX, curY, x1, y1, x2, y2, x, y, safeTol, currentPoints)
                    curX = x
                    curY = y
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmdKind = 'C'
                }
                is SvgPathCommand.SmoothCubicBezierTo -> {
                    ensureSubpathStarted()
                    val x1 = if (lastCmdKind == 'C' || lastCmdKind == 'S') 2f * curX - lastCtrlX else curX
                    val y1 = if (lastCmdKind == 'C' || lastCmdKind == 'S') 2f * curY - lastCtrlY else curY
                    val x2 = if (cmd.isRelative) curX + cmd.x2 else cmd.x2
                    val y2 = if (cmd.isRelative) curY + cmd.y2 else cmd.y2
                    val x = if (cmd.isRelative) curX + cmd.x else cmd.x
                    val y = if (cmd.isRelative) curY + cmd.y else cmd.y
                    flattenCubic(curX, curY, x1, y1, x2, y2, x, y, safeTol, currentPoints)
                    curX = x
                    curY = y
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmdKind = 'S'
                }
                is SvgPathCommand.QuadraticBezierTo -> {
                    ensureSubpathStarted()
                    val x1 = if (cmd.isRelative) curX + cmd.x1 else cmd.x1
                    val y1 = if (cmd.isRelative) curY + cmd.y1 else cmd.y1
                    val x = if (cmd.isRelative) curX + cmd.x else cmd.x
                    val y = if (cmd.isRelative) curY + cmd.y else cmd.y
                    flattenQuadratic(curX, curY, x1, y1, x, y, safeTol, currentPoints)
                    curX = x
                    curY = y
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmdKind = 'Q'
                }
                is SvgPathCommand.SmoothQuadraticBezierTo -> {
                    ensureSubpathStarted()
                    val x1 = if (lastCmdKind == 'Q' || lastCmdKind == 'T') 2f * curX - lastCtrlX else curX
                    val y1 = if (lastCmdKind == 'Q' || lastCmdKind == 'T') 2f * curY - lastCtrlY else curY
                    val x = if (cmd.isRelative) curX + cmd.x else cmd.x
                    val y = if (cmd.isRelative) curY + cmd.y else cmd.y
                    flattenQuadratic(curX, curY, x1, y1, x, y, safeTol, currentPoints)
                    curX = x
                    curY = y
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmdKind = 'T'
                }
                is SvgPathCommand.ArcTo -> {
                    ensureSubpathStarted()
                    val x = if (cmd.isRelative) curX + cmd.x else cmd.x
                    val y = if (cmd.isRelative) curY + cmd.y else cmd.y
                    flattenArc(
                        x0 = curX,
                        y0 = curY,
                        rxIn = cmd.rx,
                        ryIn = cmd.ry,
                        angleDeg = cmd.xAxisRotation,
                        largeArc = cmd.largeArcFlag,
                        sweep = cmd.sweepFlag,
                        x = x,
                        y = y,
                        tolerance = safeTol,
                        out = currentPoints
                    )
                    curX = x
                    curY = y
                    lastCmdKind = 'A'
                }
                SvgPathCommand.ClosePath -> {
                    if (currentPoints.isNotEmpty()) {
                        subpaths.add(Subpath(currentPoints, closed = true))
                        currentPoints = ArrayList()
                    }
                    curX = startX
                    curY = startY
                    lastCmdKind = 'Z'
                }
            }
        }
        flushOpen()
        return subpaths
    }

    private fun flattenQuadratic(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        tolerance: Float,
        out: MutableList<SvgPoint>,
        depth: Int = 0
    ) {
        val dx = x2 - x0
        val dy = y2 - y0
        val d = abs((x1 - x2) * dy - (y1 - y2) * dx)
        val lenSq = dx * dx + dy * dy
        val flatEnough = if (lenSq < 1e-8f) {
            max(abs(x1 - x0), abs(y1 - y0)) <= tolerance
        } else {
            (d * d) <= tolerance * tolerance * lenSq * 4f
        }
        if (flatEnough || depth >= 12) {
            out.add(SvgPoint(x2, y2))
            return
        }
        val x01 = (x0 + x1) * 0.5f
        val y01 = (y0 + y1) * 0.5f
        val x12 = (x1 + x2) * 0.5f
        val y12 = (y1 + y2) * 0.5f
        val xm = (x01 + x12) * 0.5f
        val ym = (y01 + y12) * 0.5f
        flattenQuadratic(x0, y0, x01, y01, xm, ym, tolerance, out, depth + 1)
        flattenQuadratic(xm, ym, x12, y12, x2, y2, tolerance, out, depth + 1)
    }

    internal fun flattenCubic(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        tolerance: Float,
        out: MutableList<SvgPoint>,
        depth: Int = 0
    ) {
        val dx = x3 - x0
        val dy = y3 - y0
        val d1 = abs((x1 - x3) * dy - (y1 - y3) * dx)
        val d2 = abs((x2 - x3) * dy - (y2 - y3) * dx)
        val lenSq = dx * dx + dy * dy
        val flatEnough = if (lenSq < 1e-8f) {
            max(max(abs(x1 - x0), abs(y1 - y0)), max(abs(x2 - x0), abs(y2 - y0))) <= tolerance
        } else {
            val dMax = max(d1, d2)
            (dMax * dMax) <= tolerance * tolerance * lenSq * 16f
        }
        if (flatEnough || depth >= 12) {
            out.add(SvgPoint(x3, y3))
            return
        }
        val x01 = (x0 + x1) * 0.5f
        val y01 = (y0 + y1) * 0.5f
        val x12 = (x1 + x2) * 0.5f
        val y12 = (y1 + y2) * 0.5f
        val x23 = (x2 + x3) * 0.5f
        val y23 = (y2 + y3) * 0.5f
        val x012 = (x01 + x12) * 0.5f
        val y012 = (y01 + y12) * 0.5f
        val x123 = (x12 + x23) * 0.5f
        val y123 = (y12 + y23) * 0.5f
        val xm = (x012 + x123) * 0.5f
        val ym = (y012 + y123) * 0.5f
        flattenCubic(x0, y0, x01, y01, x012, y012, xm, ym, tolerance, out, depth + 1)
        flattenCubic(xm, ym, x123, y123, x23, y23, x3, y3, tolerance, out, depth + 1)
    }

    /**
     * Converts an SVG elliptical arc to cubic Bezier segments per W3C SVG 1.1 Implementation Notes F.6.5/F.6.6.
     */
    private fun flattenArc(
        x0: Float,
        y0: Float,
        rxIn: Float,
        ryIn: Float,
        angleDeg: Float,
        largeArc: Boolean,
        sweep: Boolean,
        x: Float,
        y: Float,
        tolerance: Float,
        out: MutableList<SvgPoint>
    ) {
        if (abs(x0 - x) < 1e-6f && abs(y0 - y) < 1e-6f) return
        var rx = abs(rxIn.toDouble())
        var ry = abs(ryIn.toDouble())
        if (rx < 1e-7 || ry < 1e-7) {
            out.add(SvgPoint(x, y))
            return
        }
        val phi = angleDeg.toDouble() * PI / 180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (x0 - x) * 0.5
        val dy2 = (y0 - y) * 0.5
        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        var rxSq = rx * rx
        var rySq = ry * ry
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        val lambda = x1pSq / rxSq + y1pSq / rySq
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rx *= scale
            ry *= scale
            rxSq = rx * rx
            rySq = ry * ry
        }

        val num = max(0.0, rxSq * rySq - rxSq * y1pSq - rySq * x1pSq)
        val den = rxSq * y1pSq + rySq * x1pSq
        val factor = if (den < 1e-12) 0.0 else sqrt(num / den) * if (largeArc == sweep) -1.0 else 1.0

        val cxp = factor * (rx * y1p / ry)
        val cyp = factor * (-ry * x1p / rx)

        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) * 0.5
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) * 0.5

        fun vecAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            if (len < 1e-12) return 0.0
            val cosVal = (dot / len).coerceIn(-1.0, 1.0)
            var ang = acos(cosVal)
            if (ux * vy - uy * vx < 0.0) ang = -ang
            return ang
        }

        val ux = (x1p - cxp) / rx
        val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx
        val vy = (-y1p - cyp) / ry

        val theta1 = vecAngle(1.0, 0.0, ux, uy)
        var dTheta = vecAngle(ux, uy, vx, vy)
        if (!sweep && dTheta > 0.0) {
            dTheta -= 2.0 * PI
        } else if (sweep && dTheta < 0.0) {
            dTheta += 2.0 * PI
        }

        val segments = max(1, ceil(abs(dTheta) / (PI * 0.5)).toInt())
        val segAngle = dTheta / segments
        val kappa = (4.0 / 3.0) * tan(segAngle * 0.25)

        var curAngle = theta1
        var prevX = x0
        var prevY = y0
        for (i in 0 until segments) {
            val nextAngle = curAngle + segAngle
            val cos1 = cos(curAngle)
            val sin1 = sin(curAngle)
            val cos2 = cos(nextAngle)
            val sin2 = sin(nextAngle)

            val e1x = cos1 - kappa * sin1
            val e1y = sin1 + kappa * cos1
            val e2x = cos2 + kappa * sin2
            val e2y = sin2 - kappa * cos2

            fun mapEllipse(ex: Double, ey: Double): Pair<Float, Float> {
                val px = rx * ex
                val py = ry * ey
                val gx = cosPhi * px - sinPhi * py + cx
                val gy = sinPhi * px + cosPhi * py + cy
                return gx.toFloat() to gy.toFloat()
            }

            val (c1x, c1y) = mapEllipse(e1x, e1y)
            val (c2x, c2y) = mapEllipse(e2x, e2y)
            val (endX, endY) = if (i == segments - 1) (x to y) else mapEllipse(cos2, sin2)

            flattenCubic(prevX, prevY, c1x, c1y, c2x, c2y, endX, endY, tolerance, out)
            prevX = endX
            prevY = endY
            curAngle = nextAngle
        }
    }

    private fun isCommandLetter(c: Char): Boolean = when (c) {
        'M', 'm', 'L', 'l', 'H', 'h', 'V', 'v',
        'C', 'c', 'S', 's', 'Q', 'q', 'T', 't',
        'A', 'a', 'Z', 'z' -> true
        else -> false
    }

    private class PathTokenReader(private val input: String) {
        private var pos = 0
        private val len = input.length

        fun hasMore(): Boolean = pos < len
        fun peek(): Char = input[pos]
        fun advance() {
            if (pos < len) pos++
        }

        fun skipSeparators() {
            while (pos < len && (input[pos].isWhitespace() || input[pos] == ',')) {
                pos++
            }
        }

        fun readFlag(): Boolean? {
            skipSeparators()
            if (pos >= len) return null
            val ch = input[pos]
            return when (ch) {
                '0' -> {
                    pos++
                    false
                }
                '1' -> {
                    pos++
                    true
                }
                else -> null
            }
        }

        fun readNumber(): Float? {
            skipSeparators()
            if (pos >= len) return null
            val start = pos
            if (input[pos] == '+' || input[pos] == '-') {
                pos++
            }
            var hasDigits = false
            while (pos < len && input[pos] in '0'..'9') {
                hasDigits = true
                pos++
            }
            if (pos < len && input[pos] == '.') {
                pos++
                while (pos < len && input[pos] in '0'..'9') {
                    hasDigits = true
                    pos++
                }
            }
            if (hasDigits && pos < len && (input[pos] == 'e' || input[pos] == 'E')) {
                val expSave = pos
                pos++
                if (pos < len && (input[pos] == '+' || input[pos] == '-')) {
                    pos++
                }
                var expDigits = false
                while (pos < len && input[pos] in '0'..'9') {
                    expDigits = true
                    pos++
                }
                if (!expDigits) {
                    pos = expSave
                }
            }
            if (!hasDigits) {
                pos = start
                return null
            }
            return input.substring(start, pos).toFloatOrNull()
        }
    }
}
