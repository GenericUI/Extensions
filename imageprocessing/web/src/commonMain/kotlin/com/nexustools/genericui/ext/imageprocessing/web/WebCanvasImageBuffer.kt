package com.nexustools.genericui.ext.imageprocessing.web

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.web.WebDomElement

/**
 * Web implementation of `:extensions:imageprocessing`'s universal [ImageBuffer] surface
 * (`:extensions:imageprocessing:web`).
 *
 * Uses an HTML5 `<canvas>` element ([canvasElement]) directly as its backend with no extra code
 * for double buffering because HTML `<canvas>` elements natively double-buffer automatically in
 * the browser. Its [getContext] / [context2D] ([WebHtmlCanvasContext2DBackend]) operates directly
 * on the underlying `CanvasRenderingContext2D` (`canvas.getContext("2d")`) taken from the `<canvas>`
 * HTML element via [WebHtmlCanvas2DBridge].
 */
class WebCanvasImageBuffer(
    width: Int = 800,
    height: Int = 600,
    doubleBuffer: Boolean = false,
    existingCanvasElement: WebDomElement? = null,
    initialAntialias: Boolean = true
) : ImageBuffer(
    width = width.coerceAtLeast(1),
    height = height.coerceAtLeast(1),
    doubleBuffer = doubleBuffer,
    pixelFormat = PixelFormat.RGBA_8888,
    colorSpace = ImageColorSpace.SRGB,
    bytes = ByteArray(0)
) {
    /**
     * The underlying HTML5 `<canvas>` DOM element backing this [ImageBuffer].
     */
    val canvasElement: WebDomElement = existingCanvasElement ?: WebDomElement("canvas").apply {
        setAttribute("width", width.coerceAtLeast(1).toString())
        setAttribute("height", height.coerceAtLeast(1).toString())
    }

    override val backendTypeName: String = "HTML5_CANVAS_2D"
    override val backendDescription: String = "HTML5 <canvas> 2D Direct Surface"

    private var _flushCount: Long = 0L
    override val flushCount: Long
        get() = _flushCount

    override val context2D: WebHtmlCanvasContext2DBackend = WebHtmlCanvasContext2DBackend(
        imageBuffer = this,
        initialAntialias = initialAntialias
    )

    init {
        canvasElement.setAttribute("width", this.width.toString())
        canvasElement.setAttribute("height", this.height.toString())
        installFactory()
    }

    override fun getContext(contextType: String): WebHtmlCanvasContext2DBackend = context2D

    override fun resize(newWidth: Int, newHeight: Int) {
        val w = newWidth.coerceAtLeast(1)
        val h = newHeight.coerceAtLeast(1)
        if (w == width && h == height) return
        updateDimensionsAndBytes(w, h, ByteArray(0))
        canvasElement.setAttribute("width", w.toString())
        canvasElement.setAttribute("height", h.toString())
        WebHtmlCanvas2DBridge.resizeCanvas(
            canvasPeer = canvasElement.nativeBrowserPeer,
            ctxPeer = context2D.ctxPeer,
            width = w,
            height = h,
            antialias = context2D.antialias
        )
    }

    override fun flush() {
        _flushCount++
    }

    override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val readBytes = WebHtmlCanvas2DBridge.getImageDataRgba(context2D.ctxPeer, x, y, w, h)
        if (readBytes.isNotEmpty() && x == 0 && y == 0 && w == this.width && h == this.height) {
            updateDimensionsAndBytes(this.width, this.height, readBytes)
            return readBytes
        }
        if (readBytes.isNotEmpty()) return readBytes
        return if (bytes.size == w * h * 4) bytes else ByteArray(w * h * 4)
    }

    override fun writePixelsRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        WebHtmlCanvas2DBridge.putImageDataRgba(context2D.ctxPeer, rgbaBytes, width, height, dx, dy)
        if (dx == 0 && dy == 0 && width == this.width && height == this.height) {
            updateDimensionsAndBytes(this.width, this.height, rgbaBytes)
        }
        _flushCount++
    }

    override fun directRgbaBytes(): ByteArray = readPixelsRgba(0, 0, width, height)

    override fun directBgraBytes(): ByteArray {
        val rgba = directRgbaBytes()
        val bgra = ByteArray(rgba.size)
        var i = 0
        while (i + 3 < rgba.size) {
            bgra[i] = rgba[i + 2]
            bgra[i + 1] = rgba[i + 1]
            bgra[i + 2] = rgba[i]
            bgra[i + 3] = rgba[i + 3]
            i += 4
        }
        return bgra
    }

    override fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer {
        val rgba = directRgbaBytes()
        return com.nexustools.genericui.image.ImageBuffer(
            width = width,
            height = height,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            bytes = rgba
        )
    }

    override fun dispose() {
        updateDimensionsAndBytes(width, height, ByteArray(0))
    }

    companion object {
        fun installFactory() {
            ImageBuffer.registerFactory { width, height, doubleBuffer, antialias ->
                WebCanvasImageBuffer(
                    width = width,
                    height = height,
                    doubleBuffer = doubleBuffer,
                    initialAntialias = antialias
                )
            }
        }
    }
}
