package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.canvas2d.OffscreenCanvas2D
import com.nexustools.genericui.ext.gpu.web.WebGpuProvider
import com.nexustools.genericui.style.RgbaColor

/**
 * Web HTML5 `<canvas>` 2D rendering context implementation of [Canvas2DContext] backed by
 * hardware-accelerated [OffscreenCanvas2D] and `:extensions:gpu` (WebGPU primary, WebGL2 fallback).
 */
class WebCanvas2DContextImpl(
    width: Int = 800,
    height: Int = 600
) : Canvas2DContext {

    val offscreenCanvas: OffscreenCanvas2D = OffscreenCanvas2D(
        width = width,
        height = height,
        gpuContext = WebGpuProvider.acquireWebContext(preferWebGpu = true)
    )

    override var width: Int
        get() = offscreenCanvas.width
        set(value) {
            offscreenCanvas.resize(value, offscreenCanvas.height)
        }

    override var height: Int
        get() = offscreenCanvas.height
        set(value) {
            offscreenCanvas.resize(offscreenCanvas.width, value)
        }

    fun swapBuffers() = offscreenCanvas.swapBuffers()

    override fun save() = offscreenCanvas.save()
    override fun restore() = offscreenCanvas.restore()
    override fun scale(sx: Float, sy: Float) = offscreenCanvas.scale(sx, sy)
    override fun rotate(angleRadians: Float) = offscreenCanvas.rotate(angleRadians)
    override fun translate(dx: Float, dy: Float) = offscreenCanvas.translate(dx, dy)
    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) =
        offscreenCanvas.setTransform(a, b, c, d, e, f)
    override fun resetTransform() = offscreenCanvas.resetTransform()

    override fun setGlobalAlpha(alpha: Float) = offscreenCanvas.setGlobalAlpha(alpha)
    override fun setGlobalCompositeOperation(mode: String) = offscreenCanvas.setGlobalCompositeOperation(mode)
    override fun setFillColor(color: RgbaColor) = offscreenCanvas.setFillColor(color)
    override fun setStrokeColor(color: RgbaColor) = offscreenCanvas.setStrokeColor(color)
    override fun setLineWidth(width: Float) = offscreenCanvas.setLineWidth(width)
    override fun setLineCap(cap: String) = offscreenCanvas.setLineCap(cap)
    override fun setLineJoin(join: String) = offscreenCanvas.setLineJoin(join)
    override fun setLineDash(segments: FloatArray, offset: Float) = offscreenCanvas.setLineDash(segments, offset)

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) =
        offscreenCanvas.clearRect(x, y, width, height)
    override fun fillRect(x: Float, y: Float, width: Float, height: Float) =
        offscreenCanvas.fillRect(x, y, width, height)
    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) =
        offscreenCanvas.strokeRect(x, y, width, height)
    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) =
        offscreenCanvas.roundRect(x, y, width, height, radius)

    override fun beginPath() = offscreenCanvas.beginPath()
    override fun closePath() = offscreenCanvas.closePath()
    override fun moveTo(x: Float, y: Float) = offscreenCanvas.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = offscreenCanvas.lineTo(x, y)
    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) =
        offscreenCanvas.quadraticCurveTo(cpx, cpy, x, y)
    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) =
        offscreenCanvas.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y)
    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) =
        offscreenCanvas.arc(x, y, radius, startAngle, endAngle, counterClockwise)
    override fun fill() = offscreenCanvas.fill()
    override fun stroke() = offscreenCanvas.stroke()
    override fun clip() = offscreenCanvas.clip()

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) =
        offscreenCanvas.setFont(fontFamily, fontSizePx, fontWeight, italic)
    override fun fillText(text: String, x: Float, y: Float) = offscreenCanvas.fillText(text, x, y)
    override fun strokeText(text: String, x: Float, y: Float) = offscreenCanvas.strokeText(text, x, y)
    override fun measureTextWidth(text: String): Float = offscreenCanvas.measureTextWidth(text)

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) =
        offscreenCanvas.putImageDataRgba(rgbaBytes, width, height, dx, dy)
    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray =
        offscreenCanvas.getImageDataRgba(x, y, width, height)
}
