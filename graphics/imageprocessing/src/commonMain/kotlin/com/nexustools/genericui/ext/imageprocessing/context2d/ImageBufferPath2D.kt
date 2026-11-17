package com.nexustools.genericui.ext.imageprocessing.context2d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Reusable 2D vector path (`Path2D` equivalent) for [ImageBuffer2DContext] supporting subpaths,
 * Bezier curves, arcs, ellipses, rounded rectangles, SVG path `d` string parsing, polyline
 * flattening, and point-in-path / point-in-stroke hit-testing.
 */
class ImageBufferPath2D() {

    private class PathCommand(
        val type: ImageBufferPathCommandType,
        val coords: FloatArray = FloatArray(0)
    )

    private val commands = mutableListOf<PathCommand>()
    private var currentX: Float = 0f
    private var currentY: Float = 0f
    private var subpathStartX: Float = 0f
    private var subpathStartY: Float = 0f
    private var hasSubpath: Boolean = false
    private var cachedPolylines: List<FloatArray>? = null
    private var cachedFlatness: Float = -1f

    /**
     * When this path consists of a single axis-aligned `rect` or `roundRect` (`[x0, y0, width, height, tl, tr, br, bl]`),
     * stores its exact geometry so `fill()` can use an analytic SDF/span fast path without polygon tessellation.
     */
    var singleRoundRectSpec: FloatArray? = null
        private set

    constructor(other: ImageBufferPath2D) : this() {
        addPath(other)
    }

    constructor(svgPathData: String) : this() {
        parseSvgPathData(svgPathData)
    }

    val commandCount: Int
        get() = commands.size

    fun isEmpty(): Boolean = commands.isEmpty()

    private fun invalidateCache() {
        cachedPolylines = null
        cachedFlatness = -1f
        singleRoundRectSpec = null
    }

    fun reset() {
        commands.clear()
        currentX = 0f
        currentY = 0f
        subpathStartX = 0f
        subpathStartY = 0f
        hasSubpath = false
        invalidateCache()
    }

    fun moveTo(x: Float, y: Float) {
        invalidateCache()
        commands.add(PathCommand(ImageBufferPathCommandType.MOVE_TO, floatArrayOf(x, y)))
        currentX = x
        currentY = y
        subpathStartX = x
        subpathStartY = y
        hasSubpath = true
    }

    fun lineTo(x: Float, y: Float) {
        if (!hasSubpath) {
            moveTo(x, y)
            return
        }
        invalidateCache()
        commands.add(PathCommand(ImageBufferPathCommandType.LINE_TO, floatArrayOf(x, y)))
        currentX = x
        currentY = y
    }

    fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        if (!hasSubpath) {
            moveTo(cpx, cpy)
        }
        invalidateCache()
        commands.add(PathCommand(ImageBufferPathCommandType.QUADRATIC_TO, floatArrayOf(cpx, cpy, x, y)))
        currentX = x
        currentY = y
    }

    fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        if (!hasSubpath) {
            moveTo(cp1x, cp1y)
        }
        invalidateCache()
        commands.add(PathCommand(ImageBufferPathCommandType.BEZIER_TO, floatArrayOf(cp1x, cp1y, cp2x, cp2y, x, y)))
        currentX = x
        currentY = y
    }

    fun closePath() {
        if (!hasSubpath) return
        invalidateCache()
        commands.add(PathCommand(ImageBufferPathCommandType.CLOSE))
        currentX = subpathStartX
        currentY = subpathStartY
    }

    fun rect(x: Float, y: Float, width: Float, height: Float) {
        val wasEmpty = commands.isEmpty()
        moveTo(x, y)
        lineTo(x + width, y)
        lineTo(x + width, y + height)
        lineTo(x, y + height)
        closePath()
        if (wasEmpty) {
            singleRoundRectSpec = floatArrayOf(
                min(x, x + width),
                min(y, y + height),
                abs(width),
                abs(height),
                0f,
                0f,
                0f,
                0f
            )
        }
    }

    fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        roundRect(x, y, width, height, radius, radius, radius, radius)
    }

    fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomRightRadius: Float,
        bottomLeftRadius: Float
    ) {
        val wasEmpty = commands.isEmpty()
        val w = abs(width)
        val h = abs(height)
        val x0 = min(x, x + width)
        val y0 = min(y, y + height)
        val maxR = min(w, h) * 0.5f
        val tl = topLeftRadius.coerceIn(0f, maxR)
        val tr = topRightRadius.coerceIn(0f, maxR)
        val br = bottomRightRadius.coerceIn(0f, maxR)
        val bl = bottomLeftRadius.coerceIn(0f, maxR)

        val halfPi = (PI * 0.5).toFloat()
        val pi = PI.toFloat()

        moveTo(x0 + tl, y0)
        lineTo(x0 + w - tr, y0)
        if (tr > 0f) arc(x0 + w - tr, y0 + tr, tr, -halfPi, 0f, false)
        lineTo(x0 + w, y0 + h - br)
        if (br > 0f) arc(x0 + w - br, y0 + h - br, br, 0f, halfPi, false)
        lineTo(x0 + bl, y0 + h)
        if (bl > 0f) arc(x0 + bl, y0 + h - bl, bl, halfPi, pi, false)
        lineTo(x0, y0 + tl)
        if (tl > 0f) arc(x0 + tl, y0 + tl, tl, pi, pi + halfPi, false)
        closePath()
        if (wasEmpty) {
            singleRoundRectSpec = floatArrayOf(x0, y0, w, h, tl, tr, br, bl)
        }
    }

    fun arc(
        x: Float,
        y: Float,
        radius: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean = false
    ) {
        ellipse(x, y, radius, radius, 0f, startAngle, endAngle, counterClockwise)
    }

    fun ellipse(
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        rotation: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean = false
    ) {
        val rx = max(0f, radiusX)
        val ry = max(0f, radiusY)
        val twoPi = (PI * 2.0).toFloat()
        var sweep = endAngle - startAngle
        if (!counterClockwise) {
            if (sweep < 0f) {
                sweep = (sweep % twoPi) + twoPi
            } else if (sweep > twoPi) {
                sweep = twoPi
            }
        } else {
            if (sweep > 0f) {
                sweep = (sweep % twoPi) - twoPi
            } else if (sweep < -twoPi) {
                sweep = -twoPi
            }
        }

        val cosRot = cos(rotation)
        val sinRot = sin(rotation)

        fun pointOnEllipse(theta: Float): Pair<Float, Float> {
            val lx = rx * cos(theta)
            val ly = ry * sin(theta)
            val gx = x + lx * cosRot - ly * sinRot
            val gy = y + lx * sinRot + ly * cosRot
            return gx to gy
        }

        val (sx, sy) = pointOnEllipse(startAngle)
        if (!hasSubpath) {
            moveTo(sx, sy)
        } else {
            lineTo(sx, sy)
        }

        val steps = max(16, (abs(sweep) * max(rx, ry) / 2.2f).toInt().coerceAtMost(96))
        for (i in 1..steps) {
            val theta = startAngle + sweep * (i.toFloat() / steps.toFloat())
            val (px, py) = pointOnEllipse(theta)
            lineTo(px, py)
        }
    }

    fun arcTo(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float) {
        if (!hasSubpath) {
            moveTo(x1, y1)
            return
        }
        val r = max(0f, radius)
        val x0 = currentX
        val y0 = currentY
        val dx0 = x0 - x1
        val dy0 = y0 - y1
        val len0 = hypot(dx0, dy0)
        val dx2 = x2 - x1
        val dy2 = y2 - y1
        val len2 = hypot(dx2, dy2)
        if (r <= 1e-5f || len0 <= 1e-5f || len2 <= 1e-5f) {
            lineTo(x1, y1)
            return
        }
        val u0x = dx0 / len0
        val u0y = dy0 / len0
        val u2x = dx2 / len2
        val u2y = dy2 / len2
        val cross = u0x * u2y - u0y * u2x
        if (abs(cross) <= 1e-5f) {
            lineTo(x1, y1)
            return
        }
        val cosAngle = (u0x * u2x + u0y * u2y).coerceIn(-1f, 1f)
        val halfAngle = acos(cosAngle) * 0.5f
        val distToTangent = r / max(1e-5f, tan(halfAngle))
        val t0x = x1 + u0x * distToTangent
        val t0y = y1 + u0y * distToTangent
        val t2x = x1 + u2x * distToTangent
        val t2y = y1 + u2y * distToTangent

        val bisectorX = u0x + u2x
        val bisectorY = u0y + u2y
        val bisectorLen = hypot(bisectorX, bisectorY)
        if (bisectorLen <= 1e-5f) {
            lineTo(x1, y1)
            return
        }
        val centerDist = r / max(1e-5f, sin(halfAngle))
        val cx = x1 + (bisectorX / bisectorLen) * centerDist
        val cy = y1 + (bisectorY / bisectorLen) * centerDist
        val startA = atan2(t0y - cy, t0x - cx)
        val endA = atan2(t2y - cy, t2x - cx)
        lineTo(t0x, t0y)
        arc(cx, cy, r, startA, endA, counterClockwise = cross > 0f)
    }

    /**
     * Appends all commands from [other] transformed by [transform].
     */
    fun addPath(other: ImageBufferPath2D, transform: ImageBufferMatrix2D = ImageBufferMatrix2D.IDENTITY) {
        for (cmd in other.commands) {
            val c = cmd.coords
            when (cmd.type) {
                ImageBufferPathCommandType.MOVE_TO -> {
                    moveTo(transform.transformX(c[0], c[1]), transform.transformY(c[0], c[1]))
                }
                ImageBufferPathCommandType.LINE_TO -> {
                    lineTo(transform.transformX(c[0], c[1]), transform.transformY(c[0], c[1]))
                }
                ImageBufferPathCommandType.QUADRATIC_TO -> {
                    quadraticCurveTo(
                        transform.transformX(c[0], c[1]),
                        transform.transformY(c[0], c[1]),
                        transform.transformX(c[2], c[3]),
                        transform.transformY(c[2], c[3])
                    )
                }
                ImageBufferPathCommandType.BEZIER_TO -> {
                    bezierCurveTo(
                        transform.transformX(c[0], c[1]),
                        transform.transformY(c[0], c[1]),
                        transform.transformX(c[2], c[3]),
                        transform.transformY(c[2], c[3]),
                        transform.transformX(c[4], c[5]),
                        transform.transformY(c[4], c[5])
                    )
                }
                ImageBufferPathCommandType.CLOSE -> closePath()
            }
        }
    }

    /**
     * Flattens this path into a list of polyline subpaths (each represented as interleaved `[x0, y0, x1, y1, ...]`).
     */
    fun toPolylines(flatness: Float = 0.5f): List<FloatArray> {
        if (commands.isEmpty()) return emptyList()
        val stepScale = max(0.15f, flatness)
        val existing = cachedPolylines
        if (existing != null && abs(cachedFlatness - stepScale) <= 1e-4f) {
            return existing
        }
        val result = mutableListOf<FloatArray>()
        val currentSubpath = mutableListOf<Float>()
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f

        fun flushSubpath() {
            if (currentSubpath.size >= 4) {
                result.add(currentSubpath.toFloatArray())
            }
            currentSubpath.clear()
        }

        for (cmd in commands) {
            val c = cmd.coords
            when (cmd.type) {
                ImageBufferPathCommandType.MOVE_TO -> {
                    flushSubpath()
                    curX = c[0]
                    curY = c[1]
                    startX = curX
                    startY = curY
                    currentSubpath.add(curX)
                    currentSubpath.add(curY)
                }
                ImageBufferPathCommandType.LINE_TO -> {
                    if (currentSubpath.isEmpty()) {
                        currentSubpath.add(curX)
                        currentSubpath.add(curY)
                    }
                    curX = c[0]
                    curY = c[1]
                    currentSubpath.add(curX)
                    currentSubpath.add(curY)
                }
                ImageBufferPathCommandType.QUADRATIC_TO -> {
                    if (currentSubpath.isEmpty()) {
                        currentSubpath.add(curX)
                        currentSubpath.add(curY)
                    }
                    val cpx = c[0]
                    val cpy = c[1]
                    val ex = c[2]
                    val ey = c[3]
                    val approxLen = hypot(cpx - curX, cpy - curY) + hypot(ex - cpx, ey - cpy)
                    val steps = (approxLen / (2.5f * stepScale)).toInt().coerceIn(8, 64)
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps.toFloat()
                        val u = 1f - t
                        val px = u * u * curX + 2f * u * t * cpx + t * t * ex
                        val py = u * u * curY + 2f * u * t * cpy + t * t * ey
                        currentSubpath.add(px)
                        currentSubpath.add(py)
                    }
                    curX = ex
                    curY = ey
                }
                ImageBufferPathCommandType.BEZIER_TO -> {
                    if (currentSubpath.isEmpty()) {
                        currentSubpath.add(curX)
                        currentSubpath.add(curY)
                    }
                    val cp1x = c[0]
                    val cp1y = c[1]
                    val cp2x = c[2]
                    val cp2y = c[3]
                    val ex = c[4]
                    val ey = c[5]
                    val approxLen = hypot(cp1x - curX, cp1y - curY) +
                        hypot(cp2x - cp1x, cp2y - cp1y) +
                        hypot(ex - cp2x, ey - cp2y)
                    val steps = (approxLen / (2.5f * stepScale)).toInt().coerceIn(10, 72)
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps.toFloat()
                        val u = 1f - t
                        val px = u * u * u * curX +
                            3f * u * u * t * cp1x +
                            3f * u * t * t * cp2x +
                            t * t * t * ex
                        val py = u * u * u * curY +
                            3f * u * u * t * cp1y +
                            3f * u * t * t * cp2y +
                            t * t * t * ey
                        currentSubpath.add(px)
                        currentSubpath.add(py)
                    }
                    curX = ex
                    curY = ey
                }
                ImageBufferPathCommandType.CLOSE -> {
                    if (currentSubpath.size >= 2) {
                        currentSubpath.add(startX)
                        currentSubpath.add(startY)
                        curX = startX
                        curY = startY
                    }
                }
            }
        }
        flushSubpath()
        cachedPolylines = result
        cachedFlatness = stepScale
        return result
    }

    /**
     * Flattens this path into a list of polyline subpaths transformed by [matrix].
     */
    fun flattenSubpaths(
        matrix: ImageBufferMatrix2D = ImageBufferMatrix2D.IDENTITY,
        flatness: Float = 0.5f
    ): List<FloatArray> {
        val raw = toPolylines(flatness)
        if (matrix.isIdentity) return raw
        return raw.map { poly ->
            val out = FloatArray(poly.size)
            var i = 0
            while (i + 1 < poly.size) {
                val x = poly[i]
                val y = poly[i + 1]
                out[i] = matrix.transformX(x, y)
                out[i + 1] = matrix.transformY(x, y)
                i += 2
            }
            out
        }
    }

    /**
     * Evaluates whether `(px, py)` lies inside the filled area of this path using [fillRule].
     */
    fun containsPoint(
        px: Float,
        py: Float,
        fillRule: ImageBufferFillRule = ImageBufferFillRule.NONZERO
    ): Boolean {
        val polylines = toPolylines()
        if (polylines.isEmpty()) return false
        var winding = 0
        var crossings = 0

        for (poly in polylines) {
            val pointCount = poly.size / 2
            if (pointCount < 3) continue
            for (i in 0 until pointCount) {
                val j = (i + 1) % pointCount
                val x0 = poly[i * 2]
                val y0 = poly[i * 2 + 1]
                val x1 = poly[j * 2]
                val y1 = poly[j * 2 + 1]
                if ((y0 <= py && y1 > py) || (y1 <= py && y0 > py)) {
                    val vt = (py - y0) / (y1 - y0)
                    val intersectX = x0 + vt * (x1 - x0)
                    if (px < intersectX) {
                        crossings++
                        if (y1 > y0) winding++ else winding--
                    }
                }
            }
        }
        return when (fillRule) {
            ImageBufferFillRule.NONZERO -> winding != 0
            ImageBufferFillRule.EVENODD -> (crossings and 1) == 1
        }
    }

    /**
     * Evaluates whether `(px, py)` lies within the stroked outline of this path.
     */
    fun containsStrokePoint(
        px: Float,
        py: Float,
        lineWidth: Float,
        lineCap: ImageBufferLineCap = ImageBufferLineCap.BUTT,
        lineJoin: ImageBufferLineJoin = ImageBufferLineJoin.MITER,
        miterLimit: Float = 10f
    ): Boolean {
        val polylines = toPolylines()
        if (polylines.isEmpty()) return false
        val halfW = max(0.5f, lineWidth * 0.5f)
        val halfWSq = halfW * halfW

        for (poly in polylines) {
            val pointCount = poly.size / 2
            for (i in 0 until pointCount - 1) {
                val ax = poly[i * 2]
                val ay = poly[i * 2 + 1]
                val bx = poly[(i + 1) * 2]
                val by = poly[(i + 1) * 2 + 1]
                val baX = bx - ax
                val baY = by - ay
                val lenSq = baX * baX + baY * baY
                if (lenSq <= 1e-6f) {
                    val dx = px - ax
                    val dy = py - ay
                    if (dx * dx + dy * dy <= halfWSq) return true
                    continue
                }
                val proj = ((px - ax) * baX + (py - ay) * baY) / lenSq
                val clamped = when (lineCap) {
                    ImageBufferLineCap.SQUARE -> {
                        val ext = halfW / sqrt(lenSq)
                        proj.coerceIn(-ext, 1f + ext)
                    }
                    else -> proj.coerceIn(0f, 1f)
                }
                val cx = ax + baX * clamped
                val cy = ay + baY * clamped
                val dx = px - cx
                val dy = py - cy
                if (dx * dx + dy * dy <= halfWSq) return true
            }
        }
        return false
    }

    private fun parseSvgPathData(svg: String) {
        var idx = 0
        val n = svg.length

        fun skipSeparators() {
            while (idx < n && (svg[idx].isWhitespace() || svg[idx] == ',')) idx++
        }

        fun readFloat(): Float? {
            skipSeparators()
            if (idx >= n) return null
            val start = idx
            if (svg[idx] == '+' || svg[idx] == '-') idx++
            var hasDigits = false
            while (idx < n && svg[idx].isDigit()) {
                idx++
                hasDigits = true
            }
            if (idx < n && svg[idx] == '.') {
                idx++
                while (idx < n && svg[idx].isDigit()) {
                    idx++
                    hasDigits = true
                }
            }
            if (!hasDigits) {
                idx = start
                return null
            }
            if (idx < n && (svg[idx] == 'e' || svg[idx] == 'E')) {
                idx++
                if (idx < n && (svg[idx] == '+' || svg[idx] == '-')) idx++
                while (idx < n && svg[idx].isDigit()) idx++
            }
            return svg.substring(start, idx).toFloatOrNull()
        }

        var cmd = ' '
        while (idx < n) {
            skipSeparators()
            if (idx >= n) break
            val ch = svg[idx]
            if (ch.isLetter()) {
                cmd = ch
                idx++
            }
            when (cmd) {
                'M', 'm' -> {
                    val rx = readFloat() ?: break
                    val ry = readFloat() ?: break
                    val x = if (cmd == 'm') currentX + rx else rx
                    val y = if (cmd == 'm') currentY + ry else ry
                    moveTo(x, y)
                    cmd = if (cmd == 'm') 'l' else 'L'
                }
                'L', 'l' -> {
                    val rx = readFloat() ?: break
                    val ry = readFloat() ?: break
                    val x = if (cmd == 'l') currentX + rx else rx
                    val y = if (cmd == 'l') currentY + ry else ry
                    lineTo(x, y)
                }
                'H', 'h' -> {
                    val rx = readFloat() ?: break
                    val x = if (cmd == 'h') currentX + rx else rx
                    lineTo(x, currentY)
                }
                'V', 'v' -> {
                    val ry = readFloat() ?: break
                    val y = if (cmd == 'v') currentY + ry else ry
                    lineTo(currentX, y)
                }
                'Q', 'q' -> {
                    val rcpx = readFloat() ?: break
                    val rcpy = readFloat() ?: break
                    val rx = readFloat() ?: break
                    val ry = readFloat() ?: break
                    val ox = if (cmd == 'q') currentX else 0f
                    val oy = if (cmd == 'q') currentY else 0f
                    quadraticCurveTo(ox + rcpx, oy + rcpy, ox + rx, oy + ry)
                }
                'C', 'c' -> {
                    val r1x = readFloat() ?: break
                    val r1y = readFloat() ?: break
                    val r2x = readFloat() ?: break
                    val r2y = readFloat() ?: break
                    val rx = readFloat() ?: break
                    val ry = readFloat() ?: break
                    val ox = if (cmd == 'c') currentX else 0f
                    val oy = if (cmd == 'c') currentY else 0f
                    bezierCurveTo(ox + r1x, oy + r1y, ox + r2x, oy + r2y, ox + rx, oy + ry)
                }
                'Z', 'z' -> {
                    closePath()
                    cmd = ' '
                }
                else -> idx++
            }
        }
    }
}
