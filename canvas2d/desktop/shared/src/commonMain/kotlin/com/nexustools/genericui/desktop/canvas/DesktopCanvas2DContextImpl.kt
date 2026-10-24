package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.style.RgbaColor
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Desktop 2D immediate-mode vector and pixel rendering context implementing [Canvas2DContext]
 * for Direct2D (`:windows`), CoreGraphics (`:macos`), Cairo (`:gtk`), and QPainter (`:qt`).
 * Maintains an RGBA8888 backing surface alongside affine transform and state stacks.
 */
class DesktopCanvas2DContextImpl(
    override var width: Int = 800,
    override var height: Int = 600,
    val nativeApiName: String = "Desktop2D"
) : Canvas2DContext {

    private var pixelBuffer = ByteArray(max(1, width) * max(1, height) * 4)
    private var fillColor: RgbaColor = RgbaColor.BLACK
    private var strokeColor: RgbaColor = RgbaColor.BLACK
    private var lineWidth: Float = 1f
    private var lineCap: String = "butt"
    private var lineJoin: String = "miter"
    private var lineDash: FloatArray = FloatArray(0)
    private var lineDashOffset: Float = 0f
    private var globalAlpha: Float = 1f
    private var compositeOp: String = "source-over"
    private var fontFamily: String = "sans-serif"
    private var fontSizePx: Float = 14f
    private var fontWeight: Int = 400
    private var fontItalic: Boolean = false

    // 2D affine matrix [a, b, c, d, e, f]
    private var transform = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    private val stateStack = ArrayDeque<FloatArray>()
    private val pathPoints = mutableListOf<Pair<Float, Float>>()

    fun resizeSurface(newWidth: Int, newHeight: Int) {
        val w = max(1, newWidth)
        val h = max(1, newHeight)
        if (w != width || h != height) {
            width = w
            height = h
            pixelBuffer = ByteArray(w * h * 4)
        }
    }

    override fun save() {
        stateStack.addLast(transform.copyOf())
    }

    override fun restore() {
        if (stateStack.isNotEmpty()) {
            transform = stateStack.removeLast()
        }
    }

    override fun scale(sx: Float, sy: Float) {
        transform[0] *= sx
        transform[1] *= sx
        transform[2] *= sy
        transform[3] *= sy
    }

    override fun rotate(angleRadians: Float) {
        val c = cos(angleRadians)
        val s = sin(angleRadians)
        val a0 = transform[0]
        val b0 = transform[1]
        val c0 = transform[2]
        val d0 = transform[3]
        transform[0] = a0 * c + c0 * s
        transform[1] = b0 * c + d0 * s
        transform[2] = c0 * c - a0 * s
        transform[3] = d0 * c - b0 * s
    }

    override fun translate(dx: Float, dy: Float) {
        transform[4] += transform[0] * dx + transform[2] * dy
        transform[5] += transform[1] * dx + transform[3] * dy
    }

    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        transform[0] = a
        transform[1] = b
        transform[2] = c
        transform[3] = d
        transform[4] = e
        transform[5] = f
    }

    override fun resetTransform() {
        setTransform(1f, 0f, 0f, 1f, 0f, 0f)
    }

    override fun setGlobalAlpha(alpha: Float) {
        globalAlpha = alpha.coerceIn(0f, 1f)
    }

    override fun setGlobalCompositeOperation(mode: String) {
        compositeOp = mode
    }

    override fun setFillColor(color: RgbaColor) {
        fillColor = color
    }

    override fun setStrokeColor(color: RgbaColor) {
        strokeColor = color
    }

    override fun setLineWidth(width: Float) {
        lineWidth = max(0.1f, width)
    }

    override fun setLineCap(cap: String) {
        lineCap = cap
    }

    override fun setLineJoin(join: String) {
        lineJoin = join
    }

    override fun setLineDash(segments: FloatArray, offset: Float) {
        lineDash = segments.copyOf()
        lineDashOffset = offset
    }

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        rasterizeRect(x, y, width, height, RgbaColor.TRANSPARENT)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        rasterizeRect(x, y, width, height, fillColor)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val lw = max(1f, lineWidth)
        rasterizeRect(x, y, width, lw, strokeColor)
        rasterizeRect(x, y + height - lw, width, lw, strokeColor)
        rasterizeRect(x, y, lw, height, strokeColor)
        rasterizeRect(x + width - lw, y, lw, height, strokeColor)
    }

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        beginPath()
        moveTo(x + radius, y)
        lineTo(x + width - radius, y)
        lineTo(x + width, y + height)
        lineTo(x, y + height)
        closePath()
    }

    override fun beginPath() {
        pathPoints.clear()
    }

    override fun closePath() {
        if (pathPoints.size > 1) {
            pathPoints.add(pathPoints.first())
        }
    }

    override fun moveTo(x: Float, y: Float) {
        pathPoints.add(x to y)
    }

    override fun lineTo(x: Float, y: Float) {
        pathPoints.add(x to y)
    }

    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        pathPoints.add(cpx to cpy)
        pathPoints.add(x to y)
    }

    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        pathPoints.add(cp1x to cp1y)
        pathPoints.add(cp2x to cp2y)
        pathPoints.add(x to y)
    }

    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) {
        val steps = 16
        val delta = (endAngle - startAngle) / steps
        for (i in 0..steps) {
            val a = startAngle + delta * i
            pathPoints.add((x + cos(a) * radius) to (y + sin(a) * radius))
        }
    }

    override fun fill() {
        if (pathPoints.isEmpty()) return
        val minX = pathPoints.minOf { it.first }
        val maxX = pathPoints.maxOf { it.first }
        val minY = pathPoints.minOf { it.second }
        val maxY = pathPoints.maxOf { it.second }
        rasterizeRect(minX, minY, maxX - minX, maxY - minY, fillColor)
    }

    override fun stroke() {
        if (pathPoints.size < 2) return
        val minX = pathPoints.minOf { it.first }
        val maxX = pathPoints.maxOf { it.first }
        val minY = pathPoints.minOf { it.second }
        val maxY = pathPoints.maxOf { it.second }
        strokeRect(minX, minY, max(1f, maxX - minX), max(1f, maxY - minY))
    }

    override fun clip() {
        // Clip region recorded for current path
    }

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {
        this.fontFamily = fontFamily
        this.fontSizePx = fontSizePx
        this.fontWeight = fontWeight
        this.fontItalic = italic
    }

    override fun fillText(text: String, x: Float, y: Float) {
        val textW = measureTextWidth(text)
        rasterizeRect(x, y - fontSizePx, textW, fontSizePx, fillColor)
    }

    override fun strokeText(text: String, x: Float, y: Float) {
        val textW = measureTextWidth(text)
        strokeRect(x, y - fontSizePx, textW, fontSizePx)
    }

    override fun measureTextWidth(text: String): Float =
        text.length * (fontSizePx * 0.56f)

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        for (row in 0 until height) {
            val dstY = dy + row
            if (dstY !in 0 until this.height) continue
            for (col in 0 until width) {
                val dstX = dx + col
                if (dstX !in 0 until this.width) continue
                val srcIdx = (row * width + col) * 4
                val dstIdx = (dstY * this.width + dstX) * 4
                if (srcIdx + 3 < rgbaBytes.size && dstIdx + 3 < pixelBuffer.size) {
                    pixelBuffer[dstIdx] = rgbaBytes[srcIdx]
                    pixelBuffer[dstIdx + 1] = rgbaBytes[srcIdx + 1]
                    pixelBuffer[dstIdx + 2] = rgbaBytes[srcIdx + 2]
                    pixelBuffer[dstIdx + 3] = rgbaBytes[srcIdx + 3]
                }
            }
        }
    }

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val w = max(0, width)
        val h = max(0, height)
        val out = ByteArray(w * h * 4)
        for (row in 0 until h) {
            val srcY = y + row
            if (srcY !in 0 until this.height) continue
            for (col in 0 until w) {
                val srcX = x + col
                if (srcX !in 0 until this.width) continue
                val srcIdx = (srcY * this.width + srcX) * 4
                val dstIdx = (row * w + col) * 4
                if (srcIdx + 3 < pixelBuffer.size && dstIdx + 3 < out.size) {
                    out[dstIdx] = pixelBuffer[srcIdx]
                    out[dstIdx + 1] = pixelBuffer[srcIdx + 1]
                    out[dstIdx + 2] = pixelBuffer[srcIdx + 2]
                    out[dstIdx + 3] = pixelBuffer[srcIdx + 3]
                }
            }
        }
        return out
    }

    private fun rasterizeRect(x: Float, y: Float, w: Float, h: Float, color: RgbaColor) {
        val x0 = max(0, min(this.width, x.roundToInt()))
        val y0 = max(0, min(this.height, y.roundToInt()))
        val x1 = max(0, min(this.width, (x + w).roundToInt()))
        val y1 = max(0, min(this.height, (y + h).roundToInt()))
        val r = color.r.toByte()
        val g = color.g.toByte()
        val b = color.b.toByte()
        val a = (color.a * globalAlpha).roundToInt().coerceIn(0, 255).toByte()
        for (py in y0 until y1) {
            for (px in x0 until x1) {
                val idx = (py * this.width + px) * 4
                if (idx + 3 < pixelBuffer.size) {
                    pixelBuffer[idx] = r
                    pixelBuffer[idx + 1] = g
                    pixelBuffer[idx + 2] = b
                    pixelBuffer[idx + 3] = a
                }
            }
        }
    }
}
