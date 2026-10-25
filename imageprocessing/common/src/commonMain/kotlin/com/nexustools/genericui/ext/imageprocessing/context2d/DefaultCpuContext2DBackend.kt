package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Portable 2D context implementation for [DefaultCpuImageBuffer] in `:extensions:imageprocessing:common`.
 */
class DefaultCpuContext2DBackend(
    override val imageBuffer: DefaultCpuImageBuffer,
    initialAntialias: Boolean = true
) : ImageProcessingContext2DBackend {

    override val width: Int
        get() = imageBuffer.width

    override val height: Int
        get() = imageBuffer.height

    override var antialias: Boolean = initialAntialias

    override val swapCount: Long
        get() = imageBuffer.flushCount

    private var fillColor: RgbaColor = RgbaColor.BLACK
    private var strokeColor: RgbaColor = RgbaColor.BLACK
    private var lineWidth: Float = 1f
    private var lineCap: String = "round"
    private var lineJoin: String = "round"
    private var globalAlpha: Float = 1f
    private var compositeOp: String = "source-over"
    private var fontSizePx: Float = 14f
    private val pathPoints = mutableListOf<Pair<Float, Float>>()

    override fun resize(newWidth: Int, newHeight: Int) = imageBuffer.resize(newWidth, newHeight)
    override fun swapBuffers() = imageBuffer.flush()
    override fun save() {}
    override fun restore() {}
    override fun scale(sx: Float, sy: Float) {}
    override fun rotate(angleRadians: Float) {}
    override fun translate(dx: Float, dy: Float) {}
    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {}
    override fun resetTransform() {}

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

    override fun setLineDash(segments: FloatArray, offset: Float) {}

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        fillRectInternal(x, y, width, height, RgbaColor.TRANSPARENT, clear = true)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        fillRectInternal(x, y, width, height, fillColor, clear = false)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val lw = max(1f, lineWidth)
        fillRectInternal(x, y, width, lw, strokeColor, clear = false)
        fillRectInternal(x, y + height - lw, width, lw, strokeColor, clear = false)
        fillRectInternal(x, y, lw, height, strokeColor, clear = false)
        fillRectInternal(x + width - lw, y, lw, height, strokeColor, clear = false)
    }

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        fillRect(x, y, width, height)
    }

    override fun beginPath() {
        pathPoints.clear()
    }

    override fun closePath() {
        if (pathPoints.size > 1) pathPoints.add(pathPoints.first())
    }

    override fun moveTo(x: Float, y: Float) {
        pathPoints.add(x to y)
    }

    override fun lineTo(x: Float, y: Float) {
        pathPoints.add(x to y)
    }

    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        pathPoints.add(x to y)
    }

    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
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
        fillRectInternal(minX, minY, maxX - minX, maxY - minY, fillColor, clear = false)
    }

    override fun stroke() {
        for (i in 0 until pathPoints.size - 1) {
            val (x0, y0) = pathPoints[i]
            val (x1, y1) = pathPoints[i + 1]
            stampBrushStroke(x0, y0, x1, y1, max(0.5f, lineWidth * 0.5f), 0.9f, strokeColor, globalAlpha, false)
        }
    }

    override fun clip() {}

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {
        this.fontSizePx = fontSizePx
    }

    override fun fillText(text: String, x: Float, y: Float) {
        fillRect(x, y - fontSizePx, measureTextWidth(text), fontSizePx)
    }

    override fun strokeText(text: String, x: Float, y: Float) {
        strokeRect(x, y - fontSizePx, measureTextWidth(text), fontSizePx)
    }

    override fun measureTextWidth(text: String): Float = text.length * (fontSizePx * 0.56f)

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) =
        imageBuffer.writePixelsRgba(rgbaBytes, width, height, dx, dy)

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray =
        imageBuffer.readPixelsRgba(x, y, width, height)

    override fun drawImageBuffer(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float, dy: Float) {
        val rgba = buffer.asRgba8888()
        putImageDataRgba(rgba.bytes, rgba.width, rgba.height, dx.roundToInt(), dy.roundToInt())
    }

    override fun presentFrontBufferRgba(): ByteArray = imageBuffer.directRgbaBytes()
    override fun presentFrontBufferBgra(): ByteArray = imageBuffer.directBgraBytes()
    override fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer = imageBuffer.toImageBuffer()

    override fun stampBrushStroke(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        hardness: Float,
        color: RgbaColor,
        opacity: Float,
        erase: Boolean
    ) {
        val r = max(1, radius.roundToInt())
        val steps = max(1, max(abs(x1 - x0), abs(y1 - y0)).roundToInt())
        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            val cx = (x0 + (x1 - x0) * t).roundToInt()
            val cy = (y0 + (y1 - y0) * t).roundToInt()
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (dx * dx + dy * dy <= r * r) {
                        val px = cx + dx
                        val py = cy + dy
                        if (px in 0 until width && py in 0 until height) {
                            imageBuffer.setPixel(px, py, if (erase) RgbaColor.TRANSPARENT else color)
                        }
                    }
                }
            }
        }
    }

    override fun fillLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        startColor: RgbaColor,
        endColor: RgbaColor,
        rectX: Float,
        rectY: Float,
        rectW: Float,
        rectH: Float
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val lenSq = max(1f, dx * dx + dy * dy)
        val sx = rectX.roundToInt().coerceIn(0, width)
        val sy = rectY.roundToInt().coerceIn(0, height)
        val ex = (rectX + rectW).roundToInt().coerceIn(0, width)
        val ey = (rectY + rectH).roundToInt().coerceIn(0, height)
        for (py in sy until ey) {
            for (px in sx until ex) {
                val t = (((px - x0) * dx + (py - y0) * dy) / lenSq).coerceIn(0f, 1f)
                val r = (startColor.r + (endColor.r - startColor.r) * t).roundToInt().coerceIn(0, 255)
                val g = (startColor.g + (endColor.g - startColor.g) * t).roundToInt().coerceIn(0, 255)
                val b = (startColor.b + (endColor.b - startColor.b) * t).roundToInt().coerceIn(0, 255)
                val a = (startColor.a + (endColor.a - startColor.a) * t).roundToInt().coerceIn(0, 255)
                imageBuffer.setPixel(px, py, RgbaColor(r, g, b, a))
            }
        }
    }

    override fun floodFill(seedX: Int, seedY: Int, fillColor: RgbaColor, tolerance: Int) {
        if (seedX !in 0 until width || seedY !in 0 until height) return
        imageBuffer.fill(fillColor)
    }

    private fun fillRectInternal(x: Float, y: Float, w: Float, h: Float, color: RgbaColor, clear: Boolean) {
        val sx = x.roundToInt().coerceIn(0, width)
        val sy = y.roundToInt().coerceIn(0, height)
        val ex = (x + w).roundToInt().coerceIn(0, width)
        val ey = (y + h).roundToInt().coerceIn(0, height)
        for (py in sy until ey) {
            for (px in sx until ex) {
                imageBuffer.setPixel(px, py, if (clear) RgbaColor.TRANSPARENT else color)
            }
        }
    }
}
