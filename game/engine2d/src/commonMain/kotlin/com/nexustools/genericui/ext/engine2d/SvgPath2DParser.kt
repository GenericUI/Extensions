package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferPath2D
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
 * Full SVG path `d` command parser supporting `M/m`, `L/l`, `H/h`, `V/v`, `C/c`, `S/s`, `Q/q`,
 * `T/t`, `A/a` (W3C elliptical arc endpoint-to-center conversion & cubic Bezier approximation),
 * and `Z/z`, emitting cleanly onto [ImageBufferPath2D] or [ImageBuffer2DContext].
 */
object SvgPath2DParser {

    fun parseToPath(svgPathData: String): ImageBufferPath2D {
        val path = ImageBufferPath2D()
        parseCommands(
            svgPathData = svgPathData,
            onMoveTo = { x, y -> path.moveTo(x, y) },
            onLineTo = { x, y -> path.lineTo(x, y) },
            onQuadTo = { cx, cy, x, y -> path.quadraticCurveTo(cx, cy, x, y) },
            onBezierTo = { c1x, c1y, c2x, c2y, x, y -> path.bezierCurveTo(c1x, c1y, c2x, c2y, x, y) },
            onClose = { path.closePath() }
        )
        return path
    }

    fun traceOntoContext(
        svgPathData: String,
        ctx: ImageBuffer2DContext,
        beginNewPath: Boolean = true
    ) {
        if (beginNewPath) {
            ctx.beginPath()
        }
        parseCommands(
            svgPathData = svgPathData,
            onMoveTo = { x, y -> ctx.moveTo(x, y) },
            onLineTo = { x, y -> ctx.lineTo(x, y) },
            onQuadTo = { cx, cy, x, y -> ctx.quadraticCurveTo(cx, cy, x, y) },
            onBezierTo = { c1x, c1y, c2x, c2y, x, y -> ctx.bezierCurveTo(c1x, c1y, c2x, c2y, x, y) },
            onClose = { ctx.closePath() }
        )
    }

    private fun parseCommands(
        svgPathData: String,
        onMoveTo: (Float, Float) -> Unit,
        onLineTo: (Float, Float) -> Unit,
        onQuadTo: (Float, Float, Float, Float) -> Unit,
        onBezierTo: (Float, Float, Float, Float, Float, Float) -> Unit,
        onClose: () -> Unit
    ) {
        val n = svgPathData.length
        var idx = 0

        fun skipSeparators() {
            while (idx < n && (svgPathData[idx].isWhitespace() || svgPathData[idx] == ',')) {
                idx++
            }
        }

        fun hasMoreNumbers(): Boolean {
            skipSeparators()
            if (idx >= n) return false
            val ch = svgPathData[idx]
            return ch.isDigit() || ch == '+' || ch == '-' || ch == '.'
        }

        fun readFloat(): Float? {
            skipSeparators()
            if (idx >= n) return null
            val start = idx
            if (svgPathData[idx] == '+' || svgPathData[idx] == '-') idx++
            var hasDigits = false
            while (idx < n && svgPathData[idx].isDigit()) {
                idx++
                hasDigits = true
            }
            if (idx < n && svgPathData[idx] == '.') {
                idx++
                while (idx < n && svgPathData[idx].isDigit()) {
                    idx++
                    hasDigits = true
                }
            }
            if (!hasDigits) {
                idx = start
                return null
            }
            if (idx < n && (svgPathData[idx] == 'e' || svgPathData[idx] == 'E')) {
                val expStart = idx
                idx++
                if (idx < n && (svgPathData[idx] == '+' || svgPathData[idx] == '-')) idx++
                var expDigits = false
                while (idx < n && svgPathData[idx].isDigit()) {
                    idx++
                    expDigits = true
                }
                if (!expDigits) idx = expStart
            }
            return svgPathData.substring(start, idx).toFloatOrNull()
        }

        fun readFlag(): Boolean? {
            skipSeparators()
            if (idx >= n) return null
            val ch = svgPathData[idx]
            return when (ch) {
                '0' -> {
                    idx++
                    false
                }
                '1' -> {
                    idx++
                    true
                }
                else -> null
            }
        }

        var curX = 0f
        var curY = 0f
        var subX = 0f
        var subY = 0f
        var lastCubicCpX = 0f
        var lastCubicCpY = 0f
        var lastQuadCpX = 0f
        var lastQuadCpY = 0f
        var prevCmd = ' '
        var cmd = ' '

        while (idx < n) {
            skipSeparators()
            if (idx >= n) break
            val ch = svgPathData[idx]
            if (ch.isLetter()) {
                cmd = ch
                idx++
            } else if (cmd == ' ' || !hasMoreNumbers()) {
                idx++
                continue
            }

            when (cmd) {
                'M', 'm' -> {
                    val x = readFloat() ?: break
                    val y = readFloat() ?: break
                    val nx = if (cmd == 'm') curX + x else x
                    val ny = if (cmd == 'm') curY + y else y
                    onMoveTo(nx, ny)
                    curX = nx
                    curY = ny
                    subX = nx
                    subY = ny
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                    cmd = if (cmd == 'm') 'l' else 'L'
                }
                'L', 'l' -> {
                    val x = readFloat() ?: break
                    val y = readFloat() ?: break
                    val nx = if (cmd == 'l') curX + x else x
                    val ny = if (cmd == 'l') curY + y else y
                    onLineTo(nx, ny)
                    curX = nx
                    curY = ny
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                }
                'H', 'h' -> {
                    val x = readFloat() ?: break
                    val nx = if (cmd == 'h') curX + x else x
                    onLineTo(nx, curY)
                    curX = nx
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                }
                'V', 'v' -> {
                    val y = readFloat() ?: break
                    val ny = if (cmd == 'v') curY + y else y
                    onLineTo(curX, ny)
                    curY = ny
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                }
                'C', 'c' -> {
                    val x1 = readFloat() ?: break
                    val y1 = readFloat() ?: break
                    val x2 = readFloat() ?: break
                    val y2 = readFloat() ?: break
                    val x = readFloat() ?: break
                    val y = readFloat() ?: break
                    val ox = if (cmd == 'c') curX else 0f
                    val oy = if (cmd == 'c') curY else 0f
                    val c1x = ox + x1
                    val c1y = oy + y1
                    val c2x = ox + x2
                    val c2y = oy + y2
                    val nx = ox + x
                    val ny = oy + y
                    onBezierTo(c1x, c1y, c2x, c2y, nx, ny)
                    lastCubicCpX = c2x
                    lastCubicCpY = c2y
                    curX = nx
                    curY = ny
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                }
                'S', 's' -> {
                    val x2 = readFloat() ?: break
                    val y2 = readFloat() ?: break
                    val x = readFloat() ?: break
                    val y = readFloat() ?: break
                    val ox = if (cmd == 's') curX else 0f
                    val oy = if (cmd == 's') curY else 0f
                    val hasCubicPrev = prevCmd == 'C' || prevCmd == 'c' || prevCmd == 'S' || prevCmd == 's'
                    val c1x = if (hasCubicPrev) 2f * curX - lastCubicCpX else curX
                    val c1y = if (hasCubicPrev) 2f * curY - lastCubicCpY else curY
                    val c2x = ox + x2
                    val c2y = oy + y2
                    val nx = ox + x
                    val ny = oy + y
                    onBezierTo(c1x, c1y, c2x, c2y, nx, ny)
                    lastCubicCpX = c2x
                    lastCubicCpY = c2y
                    curX = nx
                    curY = ny
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                }
                'Q', 'q' -> {
                    val cx = readFloat() ?: break
                    val cy = readFloat() ?: break
                    val x = readFloat() ?: break
                    val y = readFloat() ?: break
                    val ox = if (cmd == 'q') curX else 0f
                    val oy = if (cmd == 'q') curY else 0f
                    val qx = ox + cx
                    val qy = oy + cy
                    val nx = ox + x
                    val ny = oy + y
                    onQuadTo(qx, qy, nx, ny)
                    lastQuadCpX = qx
                    lastQuadCpY = qy
                    curX = nx
                    curY = ny
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    prevCmd = cmd
                }
                'T', 't' -> {
                    val x = readFloat() ?: break
                    val y = readFloat() ?: break
                    val ox = if (cmd == 't') curX else 0f
                    val oy = if (cmd == 't') curY else 0f
                    val hasQuadPrev = prevCmd == 'Q' || prevCmd == 'q' || prevCmd == 'T' || prevCmd == 't'
                    val qx = if (hasQuadPrev) 2f * curX - lastQuadCpX else curX
                    val qy = if (hasQuadPrev) 2f * curY - lastQuadCpY else curY
                    val nx = ox + x
                    val ny = oy + y
                    onQuadTo(qx, qy, nx, ny)
                    lastQuadCpX = qx
                    lastQuadCpY = qy
                    curX = nx
                    curY = ny
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    prevCmd = cmd
                }
                'A', 'a' -> {
                    val rx = readFloat() ?: break
                    val ry = readFloat() ?: break
                    val xRotDeg = readFloat() ?: break
                    val largeArc = readFlag() ?: break
                    val sweep = readFlag() ?: break
                    val x = readFloat() ?: break
                    val y = readFloat() ?: break
                    val nx = if (cmd == 'a') curX + x else x
                    val ny = if (cmd == 'a') curY + y else y
                    emitEllipticalArcAsCubics(
                        x1 = curX,
                        y1 = curY,
                        rxIn = rx,
                        ryIn = ry,
                        xAxisRotationDeg = xRotDeg,
                        largeArcFlag = largeArc,
                        sweepFlag = sweep,
                        x2 = nx,
                        y2 = ny,
                        onLineTo = onLineTo,
                        onBezierTo = onBezierTo
                    )
                    curX = nx
                    curY = ny
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                }
                'Z', 'z' -> {
                    onClose()
                    curX = subX
                    curY = subY
                    lastCubicCpX = curX
                    lastCubicCpY = curY
                    lastQuadCpX = curX
                    lastQuadCpY = curY
                    prevCmd = cmd
                    cmd = ' '
                }
                else -> {
                    idx++
                    cmd = ' '
                }
            }
        }
    }

    private fun emitEllipticalArcAsCubics(
        x1: Float,
        y1: Float,
        rxIn: Float,
        ryIn: Float,
        xAxisRotationDeg: Float,
        largeArcFlag: Boolean,
        sweepFlag: Boolean,
        x2: Float,
        y2: Float,
        onLineTo: (Float, Float) -> Unit,
        onBezierTo: (Float, Float, Float, Float, Float, Float) -> Unit
    ) {
        if (x1 == x2 && y1 == y2) return
        if (abs(rxIn) <= 1e-6f || abs(ryIn) <= 1e-6f) {
            onLineTo(x2, y2)
            return
        }

        var rx = abs(rxIn.toDouble())
        var ry = abs(ryIn.toDouble())
        val phi = (xAxisRotationDeg.toDouble() % 360.0) * (PI / 180.0)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (x1 - x2) * 0.5
        val dy2 = (y1 - y2) * 0.5
        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p
        val lambda = x1pSq / (rx * rx) + y1pSq / (ry * ry)
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rx *= scale
            ry *= scale
        }

        val rxSq = rx * rx
        val rySq = ry * ry
        val denom = rxSq * y1pSq + rySq * x1pSq
        val num = max(0.0, rxSq * rySq - denom)
        val factor = if (denom > 1e-12) {
            sqrt(num / denom) * (if (largeArcFlag == sweepFlag) -1.0 else 1.0)
        } else {
            0.0
        }

        val cxp = factor * (rx * y1p / ry)
        val cyp = factor * (-ry * x1p / rx)
        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) * 0.5
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) * 0.5

        fun vecAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            if (len <= 1e-12) return 0.0
            val ang = acos((dot / len).coerceIn(-1.0, 1.0))
            return if (ux * vy - uy * vx < 0.0) -ang else ang
        }

        val ux = (x1p - cxp) / rx
        val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx
        val vy = (-y1p - cyp) / ry

        val theta1 = vecAngle(1.0, 0.0, ux, uy)
        var dTheta = vecAngle(ux, uy, vx, vy)
        if (!sweepFlag && dTheta > 0.0) {
            dTheta -= 2.0 * PI
        } else if (sweepFlag && dTheta < 0.0) {
            dTheta += 2.0 * PI
        }

        val segCount = max(1, ceil(abs(dTheta) / (PI * 0.5)).toInt())
        val delta = dTheta / segCount.toDouble()
        val alpha = (4.0 / 3.0) * tan(delta * 0.25)

        fun mapUnit(px: Double, py: Double): Pair<Float, Float> {
            val ex = rx * px
            val ey = ry * py
            return (
                (cx + cosPhi * ex - sinPhi * ey).toFloat() to
                    (cy + sinPhi * ex + cosPhi * ey).toFloat()
                )
        }

        for (i in 0 until segCount) {
            val t1 = theta1 + i * delta
            val t2 = t1 + delta
            val cos1 = cos(t1)
            val sin1 = sin(t1)
            val cos2 = cos(t2)
            val sin2 = sin(t2)

            val (c1x, c1y) = mapUnit(cos1 - alpha * sin1, sin1 + alpha * cos1)
            val (c2x, c2y) = mapUnit(cos2 + alpha * sin2, sin2 - alpha * cos2)
            val (ex, ey) = if (i == segCount - 1) (x2 to y2) else mapUnit(cos2, sin2)
            onBezierTo(c1x, c1y, c2x, c2y, ex, ey)
        }
    }
}
