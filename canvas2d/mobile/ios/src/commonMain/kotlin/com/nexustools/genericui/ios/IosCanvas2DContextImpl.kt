package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.style.RgbaColor

/**
 * iOS CoreGraphics (`CGContextRef`) implementation of [Canvas2DContext].
 */
class IosCanvas2DContextImpl(
    override var width: Int = 390,
    override var height: Int = 600
) : Canvas2DContext {
    private var framebuffer = ByteArray((width * height * 4).coerceAtLeast(4))

    override fun save() {}
    override fun restore() {}
    override fun scale(sx: Float, sy: Float) {}
    override fun rotate(angleRadians: Float) {}
    override fun translate(dx: Float, dy: Float) {}
    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {}
    override fun resetTransform() {}

    override fun setGlobalAlpha(alpha: Float) {}
    override fun setGlobalCompositeOperation(mode: String) {}
    override fun setFillColor(color: RgbaColor) {}
    override fun setStrokeColor(color: RgbaColor) {}
    override fun setLineWidth(width: Float) {}
    override fun setLineCap(cap: String) {}
    override fun setLineJoin(join: String) {}
    override fun setLineDash(segments: FloatArray, offset: Float) {}

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        framebuffer.fill(0)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {}
    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {}
    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {}

    override fun beginPath() {}
    override fun closePath() {}
    override fun moveTo(x: Float, y: Float) {}
    override fun lineTo(x: Float, y: Float) {}
    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {}
    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {}
    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) {}
    override fun fill() {}
    override fun stroke() {}
    override fun clip() {}

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {}
    override fun fillText(text: String, x: Float, y: Float) {}
    override fun strokeText(text: String, x: Float, y: Float) {}
    override fun measureTextWidth(text: String): Float = text.length * 7.5f

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        val copyLen = minOf(rgbaBytes.size, framebuffer.size)
        rgbaBytes.copyInto(framebuffer, 0, 0, copyLen)
    }

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val out = ByteArray((width * height * 4).coerceAtLeast(0))
        val copyLen = minOf(out.size, framebuffer.size)
        framebuffer.copyInto(out, 0, 0, copyLen)
        return out
    }
}
