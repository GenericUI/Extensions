package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.ext.gpu.web.WebVBlankSurfaceSynchronizer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.web.WebDomElement
import kotlin.math.max
import kotlin.math.min

/**
 * Web-target (`:extensions:imageprocessing:web`) implementation of [ImageBuffer] backed by a
 * browser 2D surface element ([surfaceElement]).
 *
 * - Disables dirty-tracking and double-buffer swap overhead (`enableDirtyTracking = false`)
 *   because the browser natively double-buffers and composites 2D surfaces automatically.
 * - Lazily allocates the CPU [bytes] backing array in browser environments (`ByteArray(0)` initially
 *   when [initialBytes] is `null`) so creating large offscreen canvases does not eagerly allocate
 *   tens of megabytes of CPU memory unless pixel-level CPU access is requested.
 * - Exposes [context2D] / [getContext] returning [WebImageBuffer2DContext] ([ImageBuffer2DContext]).
 */
class WebImageBuffer(
    width: Int = 800,
    height: Int = 600,
    doubleBuffer: Boolean = false,
    val initialAntialias: Boolean = true,
    existingSurfaceElement: WebDomElement? = null,
    pixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    colorSpace: ImageColorSpace = ImageColorSpace.SRGB,
    initialBytes: ByteArray? = null
) : ImageBuffer(
    width = max(1, width),
    height = max(1, height),
    doubleBuffer = doubleBuffer,
    enableDirtyTracking = false,
    pixelFormat = pixelFormat,
    colorSpace = colorSpace,
    bytes = initialBytes ?: if (WebImageBuffer2DBridge.isBrowserCanvasSupported) {
        ByteArray(0)
    } else {
        ByteArray(max(1, width) * max(1, height) * pixelFormat.bytesPerPixel)
    }
) {
    val surfaceElement: WebDomElement = existingSurfaceElement ?: WebDomElement("canvas")

    override val backendTypeName: String = "WEB_IMAGE_BUFFER_2D"
    override val backendDescription: String = "Browser Native 2D Surface ImageBuffer"

    private var _flushCount: Long = 0L
    override val flushCount: Long
        get() = _flushCount

    internal var canvasDirty: Boolean = false
    internal var cpuDirty: Boolean = initialBytes != null

    var modificationVersion: Long = 0L
        internal set

    private var bgraBytes: ByteArray = ByteArray(0)

    override val context2D: WebImageBuffer2DContext by lazy {
        WebImageBuffer2DContext(this, initialAntialias)
    }

    init {
        installFactory()
        surfaceElement.clientWidth = this.width
        surfaceElement.clientHeight = this.height
        WebImageBuffer2DBridge.ensureSurface2D(surfaceElement, this.width, this.height, initialAntialias)
        if (initialBytes != null && WebImageBuffer2DBridge.isBrowserCanvasSupported) {
            syncCanvasFromCpuIfNeeded()
        }
    }

    override fun getContext(contextType: String): WebImageBuffer2DContext = context2D

    internal fun ensureCpuBytesAllocated() {
        val expectedSize = this.width * this.height * pixelFormat.bytesPerPixel
        if (super.bytes.size < expectedSize) {
            super.bytes = ByteArray(expectedSize)
        }
    }

    internal fun syncCanvasFromCpuIfNeeded() {
        if (!cpuDirty || !WebImageBuffer2DBridge.isBrowserCanvasSupported) return
        ensureCpuBytesAllocated()
        val rgbaBytes = encodeCpuBytesToRgba()
        WebImageBuffer2DBridge.putImageDataRgba(surfaceElement, rgbaBytes, this.width, this.height)
        cpuDirty = false
    }

    internal fun syncCpuFromCanvasIfNeeded() {
        if (!canvasDirty || !WebImageBuffer2DBridge.isBrowserCanvasSupported) return
        ensureCpuBytesAllocated()
        val fallback = if (pixelFormat == PixelFormat.RGBA_8888) {
            super.bytes
        } else {
            ByteArray(this.width * this.height * 4)
        }
        val rgba = WebImageBuffer2DBridge.getImageDataRgba(
            surfaceElement = surfaceElement,
            x = 0,
            y = 0,
            width = this.width,
            height = this.height,
            fallbackBytes = fallback
        )
        if (rgba.size == this.width * this.height * 4) {
            decodeRgbaToCpuBytes(rgba)
        }
        canvasDirty = false
    }

    internal fun markCanvasModified() {
        canvasDirty = true
        cpuDirty = false
        modificationVersion++
    }

    internal fun markCpuModified() {
        cpuDirty = true
        canvasDirty = false
        modificationVersion++
    }

    private fun encodeCpuBytesToRgba(): ByteArray {
        val raw = super.bytes
        if (pixelFormat == PixelFormat.RGBA_8888) {
            return raw
        }
        val totalPixels = this.width * this.height
        val out = ByteArray(totalPixels * 4)
        for (y in 0 until this.height) {
            for (x in 0 until this.width) {
                val c = super.getPixel(x, y)
                val dstIdx = (y * this.width + x) shl 2
                out[dstIdx] = c.r.toByte()
                out[dstIdx + 1] = c.g.toByte()
                out[dstIdx + 2] = c.b.toByte()
                out[dstIdx + 3] = c.a.toByte()
            }
        }
        return out
    }

    private fun decodeRgbaToCpuBytes(rgba: ByteArray) {
        if (pixelFormat == PixelFormat.RGBA_8888) {
            if (rgba !== super.bytes) {
                rgba.copyInto(super.bytes, 0, 0, min(rgba.size, super.bytes.size))
            }
            return
        }
        for (y in 0 until this.height) {
            for (x in 0 until this.width) {
                val srcIdx = (y * this.width + x) shl 2
                if (srcIdx + 3 < rgba.size) {
                    super.setPixel(
                        x,
                        y,
                        RgbaColor(
                            r = rgba[srcIdx].toInt() and 0xFF,
                            g = rgba[srcIdx + 1].toInt() and 0xFF,
                            b = rgba[srcIdx + 2].toInt() and 0xFF,
                            a = rgba[srcIdx + 3].toInt() and 0xFF
                        )
                    )
                }
            }
        }
    }

    override val nativeSurfacePeer: Any?
        get() {
            syncCanvasFromCpuIfNeeded()
            return surfaceElement.nativeBrowserPeer ?: surfaceElement
        }

    override fun toDataUrl(mimeType: String): String? {
        syncCanvasFromCpuIfNeeded()
        return WebImageBuffer2DBridge.toDataUrl(surfaceElement, mimeType)
    }

    override var bytes: ByteArray
        get() {
            ensureCpuBytesAllocated()
            syncCpuFromCanvasIfNeeded()
            return super.bytes
        }
        protected set(value) {
            super.bytes = value
            markCpuModified()
        }

    override fun getPixel(x: Int, y: Int): RgbaColor {
        ensureCpuBytesAllocated()
        syncCpuFromCanvasIfNeeded()
        return super.getPixel(x, y)
    }

    override fun setPixel(x: Int, y: Int, color: RgbaColor) {
        ensureCpuBytesAllocated()
        syncCpuFromCanvasIfNeeded()
        super.setPixel(x, y, color)
        markCpuModified()
    }

    override fun fill(color: RgbaColor): WebImageBuffer = apply {
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported) {
            if (color.a <= 0) {
                WebImageBuffer2DBridge.clearRect(surfaceElement, 0f, 0f, width.toFloat(), height.toFloat())
            } else {
                val fillCss = "rgba(${color.r},${color.g},${color.b},${color.a / 255f})"
                WebImageBuffer2DBridge.fillRect(
                    surfaceElement = surfaceElement,
                    x = 0f,
                    y = 0f,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    fillCss = fillCss,
                    globalAlpha = 1f,
                    compositeOp = "copy"
                )
            }
            markCanvasModified()
        } else {
            ensureCpuBytesAllocated()
            super.fill(color)
            if (color.a <= 0) {
                WebImageBuffer2DBridge.clearRect(surfaceElement, 0f, 0f, width.toFloat(), height.toFloat())
            } else {
                val fillCss = "rgba(${color.r},${color.g},${color.b},${color.a / 255f})"
                WebImageBuffer2DBridge.fillRect(
                    surfaceElement = surfaceElement,
                    x = 0f,
                    y = 0f,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    fillCss = fillCss
                )
            }
            markCpuModified()
        }
    }

    fun clear() {
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported) {
            WebImageBuffer2DBridge.clearRect(surfaceElement, 0f, 0f, width.toFloat(), height.toFloat())
            markCanvasModified()
        } else {
            ensureCpuBytesAllocated()
            super.bytes.fill(0)
            WebImageBuffer2DBridge.clearRect(surfaceElement, 0f, 0f, width.toFloat(), height.toFloat())
            markCpuModified()
        }
    }

    fun notifyDirectBufferModified() {
        markCpuModified()
    }

    override fun resize(newWidth: Int, newHeight: Int) {
        val safeW = max(1, newWidth)
        val safeH = max(1, newHeight)
        if (safeW == width && safeH == height) return
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported) {
            syncCanvasFromCpuIfNeeded()
            updateDimensionsAndBytes(safeW, safeH, ByteArray(0))
            bgraBytes = ByteArray(0)
            WebImageBuffer2DBridge.resizeSurface2D(surfaceElement, safeW, safeH)
            markCanvasModified()
        } else {
            ensureCpuBytesAllocated()
            val oldW = this.width
            val oldH = this.height
            val oldBytes = super.bytes
            val bpp = pixelFormat.bytesPerPixel
            val newBytes = ByteArray(safeW * safeH * bpp)
            val copyRowBytes = min(oldW, safeW) * bpp
            val copyRows = min(oldH, safeH)
            for (y in 0 until copyRows) {
                val srcOff = y * oldW * bpp
                val dstOff = y * safeW * bpp
                if (srcOff + copyRowBytes <= oldBytes.size && dstOff + copyRowBytes <= newBytes.size) {
                    oldBytes.copyInto(newBytes, dstOff, srcOff, srcOff + copyRowBytes)
                }
            }
            updateDimensionsAndBytes(safeW, safeH, newBytes)
            bgraBytes = ByteArray(0)
            WebImageBuffer2DBridge.resizeSurface2D(surfaceElement, safeW, safeH)
            markCpuModified()
        }
    }

    override fun flush() {
        syncCanvasFromCpuIfNeeded()
        _flushCount++
    }

    override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val safeW = max(1, width)
        val safeH = max(1, height)
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported) {
            syncCanvasFromCpuIfNeeded()
            val domPixels = WebImageBuffer2DBridge.getImageDataRgba(surfaceElement, x, y, safeW, safeH)
            if (domPixels.size == safeW * safeH * 4) {
                return domPixels
            }
        } else {
            WebImageBuffer2DBridge.getImageDataRgba(surfaceElement, x, y, safeW, safeH)
        }

        ensureCpuBytesAllocated()
        syncCpuFromCanvasIfNeeded()
        val rawRgba = encodeCpuBytesToRgba()
        if (x == 0 && y == 0 && safeW == this.width && safeH == this.height) {
            return rawRgba.copyOf()
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
                if (srcIdx + 3 < rawRgba.size && dstIdx + 3 < out.size) {
                    out[dstIdx] = rawRgba[srcIdx]
                    out[dstIdx + 1] = rawRgba[srcIdx + 1]
                    out[dstIdx + 2] = rawRgba[srcIdx + 2]
                    out[dstIdx + 3] = rawRgba[srcIdx + 3]
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

        val isFullSurface = dx == 0 && dy == 0 && width == this.width && height == this.height
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported && isFullSurface && pixelFormat == PixelFormat.RGBA_8888) {
            WebImageBuffer2DBridge.putImageDataRgba(
                surfaceElement = surfaceElement,
                rgbaBytes = rgbaBytes,
                width = this.width,
                height = this.height,
                dirtyX = 0,
                dirtyY = 0,
                dirtyWidth = this.width,
                dirtyHeight = this.height
            )
            if (super.bytes.size == this.width * this.height * 4) {
                rgbaBytes.copyInto(super.bytes, 0, 0, min(rgbaBytes.size, super.bytes.size))
                canvasDirty = false
                cpuDirty = false
                modificationVersion++
            } else {
                markCanvasModified()
            }
            return
        }

        ensureCpuBytesAllocated()
        syncCpuFromCanvasIfNeeded()
        if (pixelFormat == PixelFormat.RGBA_8888) {
            val rowBytes = (endX - startX) shl 2
            val dstBytes = super.bytes
            for (sy in startY until endY) {
                val srcOffset = ((sy * width) + startX) shl 2
                val dstOffset = (((dy + sy) * this.width) + (dx + startX)) shl 2
                if (srcOffset + rowBytes <= rgbaBytes.size && dstOffset + rowBytes <= dstBytes.size) {
                    rgbaBytes.copyInto(dstBytes, dstOffset, srcOffset, srcOffset + rowBytes)
                }
            }
        } else {
            for (sy in startY until endY) {
                for (sx in startX until endX) {
                    val srcOffset = ((sy * width) + sx) shl 2
                    if (srcOffset + 3 < rgbaBytes.size) {
                        super.setPixel(
                            dx + sx,
                            dy + sy,
                            RgbaColor(
                                r = rgbaBytes[srcOffset].toInt() and 0xFF,
                                g = rgbaBytes[srcOffset + 1].toInt() and 0xFF,
                                b = rgbaBytes[srcOffset + 2].toInt() and 0xFF,
                                a = rgbaBytes[srcOffset + 3].toInt() and 0xFF
                            )
                        )
                    }
                }
            }
        }

        val fullRgba = if (isFullSurface && rgbaBytes.size >= this.width * this.height * 4) {
            rgbaBytes
        } else {
            encodeCpuBytesToRgba()
        }
        WebImageBuffer2DBridge.putImageDataRgba(
            surfaceElement = surfaceElement,
            rgbaBytes = fullRgba,
            width = this.width,
            height = this.height,
            dirtyX = dx + startX,
            dirtyY = dy + startY,
            dirtyWidth = endX - startX,
            dirtyHeight = endY - startY
        )
        canvasDirty = false
        cpuDirty = false
        modificationVersion++
    }

    override fun directRgbaBytes(): ByteArray {
        ensureCpuBytesAllocated()
        syncCpuFromCanvasIfNeeded()
        return encodeCpuBytesToRgba()
    }

    override fun directBgraBytes(): ByteArray {
        val src = directRgbaBytes()
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

    override fun copy(): WebImageBuffer {
        ensureCpuBytesAllocated()
        syncCpuFromCanvasIfNeeded()
        return WebImageBuffer(
            width = width,
            height = height,
            doubleBuffer = doubleBuffer,
            initialAntialias = initialAntialias,
            pixelFormat = pixelFormat,
            colorSpace = colorSpace,
            initialBytes = super.bytes.copyOf()
        )
    }

    override fun toRgba8888(): com.nexustools.genericui.image.ImageBuffer {
        if (pixelFormat == PixelFormat.RGBA_8888) return copy()
        ensureCpuBytesAllocated()
        syncCpuFromCanvasIfNeeded()
        return WebImageBuffer(
            width = width,
            height = height,
            doubleBuffer = doubleBuffer,
            initialAntialias = initialAntialias,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = colorSpace,
            initialBytes = encodeCpuBytesToRgba()
        )
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
