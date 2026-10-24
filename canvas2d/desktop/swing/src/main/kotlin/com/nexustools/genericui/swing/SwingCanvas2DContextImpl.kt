package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.style.RgbaColor
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.GeneralPath
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.util.ArrayDeque

/**
 * Java2D (`BufferedImage` + `Graphics2D`) implementation of [Canvas2DContext] for [SwingCanvas2DImpl].
 */
class SwingCanvas2DContextImpl(
    override var width: Int = 800,
    override var height: Int = 600
) : Canvas2DContext {

    var backingImage: BufferedImage = BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        private set
    private var g2d: Graphics2D = createGraphics(backingImage)
    private val transformStack = ArrayDeque<AffineTransform>()

    private var fillColor: Color = Color.BLACK
    private var strokeColor: Color = Color.BLACK
    private var currentPath: GeneralPath = GeneralPath()

    fun resizeBackingBuffer(newWidth: Int, newHeight: Int) {
        val w = newWidth.coerceAtLeast(1)
        val h = newHeight.coerceAtLeast(1)
        if (w == width && h == height) return
        width = w
        height = h
        g2d.dispose()
        backingImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        g2d = createGraphics(backingImage)
    }

    override fun save() {
        transformStack.push(g2d.transform)
    }

    override fun restore() {
        if (transformStack.isNotEmpty()) {
            g2d.transform = transformStack.pop()
        }
    }

    override fun scale(sx: Float, sy: Float) {
        g2d.scale(sx.toDouble(), sy.toDouble())
    }

    override fun rotate(angleRadians: Float) {
        g2d.rotate(angleRadians.toDouble())
    }

    override fun translate(dx: Float, dy: Float) {
        g2d.translate(dx.toDouble(), dy.toDouble())
    }

    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        g2d.transform = AffineTransform(a, b, c, d, e, f)
    }

    override fun resetTransform() {
        g2d.transform = AffineTransform()
    }

    override fun setGlobalAlpha(alpha: Float) {
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
    }

    override fun setGlobalCompositeOperation(mode: String) {}

    override fun setFillColor(color: RgbaColor) {
        fillColor = Color(
            color.r.coerceIn(0, 255),
            color.g.coerceIn(0, 255),
            color.b.coerceIn(0, 255),
            (color.a * 255f + 0.5f).toInt().coerceIn(0, 255)
        )
    }

    override fun setStrokeColor(color: RgbaColor) {
        strokeColor = Color(
            color.r.coerceIn(0, 255),
            color.g.coerceIn(0, 255),
            color.b.coerceIn(0, 255),
            (color.a * 255f + 0.5f).toInt().coerceIn(0, 255)
        )
    }

    override fun setLineWidth(width: Float) {
        g2d.stroke = BasicStroke(width.coerceAtLeast(0.1f))
    }

    override fun setLineCap(cap: String) {}
    override fun setLineJoin(join: String) {}
    override fun setLineDash(segments: FloatArray, offset: Float) {}

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        val prev = g2d.composite
        g2d.composite = AlphaComposite.Clear
        g2d.fillRect(x.toInt(), y.toInt(), width.toInt(), height.toInt())
        g2d.composite = prev
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        g2d.color = fillColor
        g2d.fillRect(x.toInt(), y.toInt(), width.toInt(), height.toInt())
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        g2d.color = strokeColor
        g2d.drawRect(x.toInt(), y.toInt(), width.toInt(), height.toInt())
    }

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        currentPath.append(RoundRectangle2D.Float(x, y, width, height, radius * 2f, radius * 2f), false)
    }

    override fun beginPath() {
        currentPath = GeneralPath()
    }

    override fun closePath() {
        currentPath.closePath()
    }

    override fun moveTo(x: Float, y: Float) {
        currentPath.moveTo(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        currentPath.lineTo(x, y)
    }

    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        currentPath.quadTo(cpx, cpy, x, y)
    }

    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        currentPath.curveTo(cp1x, cp1y, cp2x, cp2y, x, y)
    }

    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) {
        val startDeg = -Math.toDegrees(startAngle.toDouble()).toFloat()
        val extentDeg = -Math.toDegrees((endAngle - startAngle).toDouble()).toFloat()
        currentPath.append(
            Arc2D.Float(x - radius, y - radius, radius * 2f, radius * 2f, startDeg, extentDeg, Arc2D.OPEN),
            true
        )
    }

    override fun fill() {
        g2d.color = fillColor
        g2d.fill(currentPath)
    }

    override fun stroke() {
        g2d.color = strokeColor
        g2d.draw(currentPath)
    }

    override fun clip() {
        g2d.clip(currentPath)
    }

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {
        var style = if (fontWeight >= 600) Font.BOLD else Font.PLAIN
        if (italic) style = style or Font.ITALIC
        g2d.font = Font(fontFamily, style, fontSizePx.toInt().coerceAtLeast(1))
    }

    override fun fillText(text: String, x: Float, y: Float) {
        g2d.color = fillColor
        g2d.drawString(text, x, y)
    }

    override fun strokeText(text: String, x: Float, y: Float) {
        g2d.color = strokeColor
        g2d.drawString(text, x, y)
    }

    override fun measureTextWidth(text: String): Float =
        g2d.fontMetrics.stringWidth(text).toFloat()

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        var i = 0
        for (py in 0 until height) {
            val ty = dy + py
            if (ty !in 0 until this.height) {
                i += width * 4
                continue
            }
            for (px in 0 until width) {
                val tx = dx + px
                if (tx in 0 until this.width && i + 3 < rgbaBytes.size) {
                    val r = rgbaBytes[i].toInt() and 0xFF
                    val g = rgbaBytes[i + 1].toInt() and 0xFF
                    val b = rgbaBytes[i + 2].toInt() and 0xFF
                    val a = rgbaBytes[i + 3].toInt() and 0xFF
                    backingImage.setRGB(tx, ty, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
                i += 4
            }
        }
    }

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val out = ByteArray((width * height * 4).coerceAtLeast(0))
        var i = 0
        for (py in 0 until height) {
            val sy = y + py
            for (px in 0 until width) {
                val sx = x + px
                val argb = if (sx in 0 until this.width && sy in 0 until this.height) {
                    backingImage.getRGB(sx, sy)
                } else 0
                if (i + 3 < out.size) {
                    out[i] = ((argb ushr 16) and 0xFF).toByte()
                    out[i + 1] = ((argb ushr 8) and 0xFF).toByte()
                    out[i + 2] = (argb and 0xFF).toByte()
                    out[i + 3] = ((argb ushr 24) and 0xFF).toByte()
                    i += 4
                }
            }
        }
        return out
    }

    private fun createGraphics(image: BufferedImage): Graphics2D =
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        }
}
