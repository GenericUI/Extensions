package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.style.RgbaColor

/**
 * Web HTML5 `<canvas>` 2D rendering context (`CanvasRenderingContext2D`) implementation of [Canvas2DContext].
 */
class WebCanvas2DContextImpl(
    override var width: Int = 800,
    override var height: Int = 600
) : Canvas2DContext {

    private var globalAlpha: Float = 1.0f
    private var compositeOp: String = "source-over"
    private var fillColor: RgbaColor = RgbaColor.BLACK
    private var strokeColor: RgbaColor = RgbaColor.BLACK
    private var lineWidthPx: Float = 1.0f
    private var fontSpec: String = "400 14px sans-serif"
    private var framebuffer: ByteArray = ByteArray((width * height * 4).coerceAtLeast(4))

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
        lineWidthPx = width
    }

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

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {
        val styleToken = if (italic) "italic " else ""
        fontSpec = "$styleToken$fontWeight ${fontSizePx}px $fontFamily"
    }

    override fun fillText(text: String, x: Float, y: Float) {}
    override fun strokeText(text: String, x: Float, y: Float) {}
    override fun measureTextWidth(text: String): Float = text.length * 7.5f

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        val copyLen = minOf(rgbaBytes.size, framebuffer.size)
        rgbaBytes.copyInto(framebuffer, 0, 0, copyLen)
    }

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val requested = (width * height * 4).coerceAtLeast(0)
        val out = ByteArray(requested)
        val copyLen = minOf(requested, framebuffer.size)
        framebuffer.copyInto(out, 0, 0, copyLen)
        return out
    }
}
