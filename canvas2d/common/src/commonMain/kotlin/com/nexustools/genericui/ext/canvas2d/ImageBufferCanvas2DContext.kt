package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageProcessingContext2DBackend
import com.nexustools.genericui.style.RgbaColor

/**
 * [Canvas2DContext] implementation backed directly by `:extensions:imageprocessing`'s
 * [ImageBuffer] and its [ImageBuffer.getContext] ([ImageProcessingContext2DBackend]).
 */
open class ImageBufferCanvas2DContext(
    override val imageBuffer: ImageBuffer
) : Canvas2DContext {

    val backend: ImageProcessingContext2DBackend
        get() = imageBuffer.getContext()

    override val width: Int
        get() = imageBuffer.width

    override val height: Int
        get() = imageBuffer.height

    override var antialias: Boolean
        get() = backend.antialias
        set(value) {
            backend.antialias = value
        }

    open val swapCount: Long
        get() = imageBuffer.flushCount

    val backendTypeName: String
        get() = imageBuffer.backendTypeName

    val backendDescription: String
        get() = imageBuffer.backendDescription

    override fun resizeSurface(newWidth: Int, newHeight: Int) {
        imageBuffer.resize(newWidth, newHeight)
    }

    open fun resize(newWidth: Int, newHeight: Int) {
        imageBuffer.resize(newWidth, newHeight)
    }

    open fun swapBuffers() {
        imageBuffer.swapBuffers()
    }

    override fun save() = backend.save()
    override fun restore() = backend.restore()
    override fun scale(sx: Float, sy: Float) = backend.scale(sx, sy)
    override fun rotate(angleRadians: Float) = backend.rotate(angleRadians)
    override fun translate(dx: Float, dy: Float) = backend.translate(dx, dy)
    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) =
        backend.setTransform(a, b, c, d, e, f)
    override fun resetTransform() = backend.resetTransform()

    override fun setGlobalAlpha(alpha: Float) = backend.setGlobalAlpha(alpha)
    override fun setGlobalCompositeOperation(mode: String) = backend.setGlobalCompositeOperation(mode)
    override fun setFillColor(color: RgbaColor) = backend.setFillColor(color)
    override fun setStrokeColor(color: RgbaColor) = backend.setStrokeColor(color)
    override fun setLineWidth(width: Float) = backend.setLineWidth(width)
    override fun setLineCap(cap: String) = backend.setLineCap(cap)
    override fun setLineJoin(join: String) = backend.setLineJoin(join)
    override fun setLineDash(segments: FloatArray, offset: Float) = backend.setLineDash(segments, offset)

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) =
        backend.clearRect(x, y, width, height)
    override fun fillRect(x: Float, y: Float, width: Float, height: Float) =
        backend.fillRect(x, y, width, height)
    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) =
        backend.strokeRect(x, y, width, height)
    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) =
        backend.roundRect(x, y, width, height, radius)

    override fun beginPath() = backend.beginPath()
    override fun closePath() = backend.closePath()
    override fun moveTo(x: Float, y: Float) = backend.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = backend.lineTo(x, y)
    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) =
        backend.quadraticCurveTo(cpx, cpy, x, y)
    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) =
        backend.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y)
    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) =
        backend.arc(x, y, radius, startAngle, endAngle, counterClockwise)
    override fun fill() = backend.fill()
    override fun stroke() = backend.stroke()
    override fun clip() = backend.clip()

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) =
        backend.setFont(fontFamily, fontSizePx, fontWeight, italic)
    override fun fillText(text: String, x: Float, y: Float) = backend.fillText(text, x, y)
    override fun strokeText(text: String, x: Float, y: Float) = backend.strokeText(text, x, y)
    override fun measureTextWidth(text: String): Float = backend.measureTextWidth(text)

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) =
        backend.putImageDataRgba(rgbaBytes, width, height, dx, dy)
    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray =
        backend.getImageDataRgba(x, y, width, height)

    open fun drawImageBuffer(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float = 0f, dy: Float = 0f) =
        backend.drawImageBuffer(buffer, dx, dy)

    open fun presentFrontBufferRgba(): ByteArray = backend.presentFrontBufferRgba()
    open fun presentFrontBufferBgra(): ByteArray = backend.presentFrontBufferBgra()
    open fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer = backend.toImageBuffer()

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
    ) = backend.stampBrushStroke(x0, y0, x1, y1, radius, hardness, color, opacity, erase)

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
    ) = backend.fillLinearGradient(x0, y0, x1, y1, startColor, endColor, rectX, rectY, rectW, rectH)

    override fun floodFill(seedX: Int, seedY: Int, fillColor: RgbaColor, tolerance: Int) =
        backend.floodFill(seedX, seedY, fillColor, tolerance)
}
