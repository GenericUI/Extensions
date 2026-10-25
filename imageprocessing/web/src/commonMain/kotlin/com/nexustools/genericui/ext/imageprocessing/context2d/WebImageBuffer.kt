package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.ext.gpu.web.WebVBlankSurfaceSynchronizer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.web.WebDomElement
import kotlin.math.max
import kotlin.math.min

/**
 * Web-target (`:extensions:imageprocessing:web`) implementation of [ImageBuffer] backed by a
 * browser 2D surface element ([surfaceElement]).
 *
 * - Disables dirty-tracking and double-buffer swap overhead (`enableDirtyTracking = false`)
 *   because the browser natively double-buffers and composites 2D surfaces automatically.
 * - Exposes [context2D] / [getContext] returning [WebImageBuffer2DContext] ([ImageBuffer2DContext]).
 */
class WebImageBuffer(
    width: Int = 800,
    height: Int = 600,
    doubleBuffer: Boolean = false,
    val initialAntialias: Boolean = true,
     existingSurfaceElement: WebDomElement? = null
) : ImageBuffer(
    width = max(1, width),
    height = max(1, height),
    doubleBuffer = doubleBuffer,
    enableDirtyTracking = false,
    pixelFormat = PixelFormat.RGBA_8888,
    colorSpace = ImageColorSpace.SRGB
) {
    val surfaceElement: WebDomElement = existingSurfaceElement ?: WebDomElement("canvas")

    override val backendTypeName: String = "WEB_IMAGE_BUFFER_2D"
    override val backendDescription: String = "Browser Native 2D Surface ImageBuffer"

    private var _flushCount: Long = 0L
    override val flushCount: Long
        get() = _flushCount

    private var bgraBytes: ByteArray = ByteArray(this.width * this.height * 4)

    override val context2D: WebImageBuffer2DContext by lazy {
        WebImageBuffer2DContext(this, initialAntialias)
    }

    init {
        installFactory()
        surfaceElement.clientWidth = this.width
        surfaceElement.clientHeight = this.height
        WebImageBuffer2DBridge.ensureSurface2D(surfaceElement, this.width, this.height, initialAntialias)
    }

    override fun getContext(contextType: String): WebImageBuffer2DContext = context2D

    override fun resize(newWidth: Int, newHeight: Int) {
        val safeW = max(1, newWidth)
        val safeH = max(1, newHeight)
        if (safeW == width && safeH == height) return
        updateDimensionsAndBytes(safeW, safeH, ByteArray(safeW * safeH * 4))
        bgraBytes = ByteArray(safeW * safeH * 4)
        WebImageBuffer2DBridge.resizeSurface2D(surfaceElement, safeW, safeH)
    }

    override fun flush() {
        // Browser 2D surfaces natively double-buffer and composite automatically.
        _flushCount++
    }

    override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val domPixels = WebImageBuffer2DBridge.getImageDataRgba(surfaceElement, x, y, safeW, safeH)
        if (domPixels.size == safeW * safeH * 4) {
            return domPixels
        }
        if (x == 0 && y == 0 && safeW == this.width && safeH == this.height) {
            return bytes.copyOf()
        }
        val out = ByteArray(safeW * safeH * 4)
        for (ry in 0 until safeH) {
            val sy = y + ry
            if (sy !in 0 until this.height) continue
            for (rx in 0 until safeW) {
                val sx = x + rx
                if (sx !in 0 until this.width) continue
                val srcIdx = (sy * this.width + sx) shl 2
                val dstIdx = (ry * safeW + rx) shl 2
                if (srcIdx + 3 < bytes.size && dstIdx + 3 < out.size) {
                    out[dstIdx] = bytes[srcIdx]
                    out[dstIdx + 1] = bytes[srcIdx + 1]
                    out[dstIdx + 2] = bytes[srcIdx + 2]
                    out[dstIdx + 3] = bytes[srcIdx + 3]
                }
            }
        }
        return out
    }

    override fun writePixelsRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        val startY = max(0, -dy)
        val endY = min(height, this.height - dy)
        val startX = max(0, -dx)
        val endX = min(width, this.width - dx)
        if (endX <= startX || endY <= startY) return
        val rowBytes = (endX - startX) shl 2
        for (sy in startY until endY) {
            val srcOffset = ((sy * width) + startX) shl 2
            val dstOffset = (((dy + sy) * this.width) + (dx + startX)) shl 2
            if (srcOffset + rowBytes <= rgbaBytes.size && dstOffset + rowBytes <= bytes.size) {
                rgbaBytes.copyInto(bytes, dstOffset, srcOffset, srcOffset + rowBytes)
            }
        }
        WebImageBuffer2DBridge.putImageDataRgba(surfaceElement, rgbaBytes, width, height, dx, dy)
    }

    override fun directRgbaBytes(): ByteArray = bytes

    override fun directBgraBytes(): ByteArray {
        val src = bytes
        if (bgraBytes.size != src.size) {
            bgraBytes = ByteArray(src.size)
        }
        var i = 0
        while (i + 3 < src.size) {
            bgraBytes[i] = src[i + 2]
            bgraBytes[i + 1] = src[i + 1]
            bgraBytes[i + 2] = src[i]
            bgraBytes[i + 3] = src[i + 3]
            i += 4
        }
        return bgraBytes
    }

    override fun dispose() {}

    companion object {
        fun installFactory() {
            WebVBlankSurfaceSynchronizer.install()
            ImageBuffer.registerFactory { width, height, doubleBuffer, antialias ->
                WebImageBuffer(
                    width = width,
                    height = height,
                    doubleBuffer = doubleBuffer,
                    initialAntialias = antialias
                )
            }
        }
    }
}
