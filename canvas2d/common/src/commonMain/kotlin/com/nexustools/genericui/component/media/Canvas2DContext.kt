package com.nexustools.genericui.component.media

import com.nexustools.genericui.style.RgbaColor

/**
 * Immediate-mode 2D vector and pixel rendering context for [Canvas2D].
 */
interface Canvas2DContext {
    val width: Int
    val height: Int

    fun save()
    fun restore()
    fun scale(sx: Float, sy: Float)
    fun rotate(angleRadians: Float)
    fun translate(dx: Float, dy: Float)
    fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float)
    fun resetTransform()

    fun setGlobalAlpha(alpha: Float)
    fun setGlobalCompositeOperation(mode: String)

    fun setFillColor(color: RgbaColor)
    fun setStrokeColor(color: RgbaColor)
    fun setLineWidth(width: Float)
    fun setLineCap(cap: String)
    fun setLineJoin(join: String)
    fun setLineDash(segments: FloatArray, offset: Float = 0f)

    fun clearRect(x: Float, y: Float, width: Float, height: Float)
    fun fillRect(x: Float, y: Float, width: Float, height: Float)
    fun strokeRect(x: Float, y: Float, width: Float, height: Float)
    fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float)

    fun beginPath()
    fun closePath()
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float)
    fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float)
    fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean = false)
    fun fill()
    fun stroke()
    fun clip()

    fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int = 400, italic: Boolean = false)
    fun fillText(text: String, x: Float, y: Float)
    fun strokeText(text: String, x: Float, y: Float)
    fun measureTextWidth(text: String): Float

    fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int = 0, dy: Int = 0)
    fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray
}
