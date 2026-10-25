package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.max
import kotlin.math.min

/**
 * Portable fallback [ImageBuffer] in `:extensions:imageprocessing:common` used when
 * [ImageBuffer.create] is invoked before a platform factory (`SystemGpuImageBuffer` on System
 * targets or `WebImageBuffer` on Web) has been installed, or directly in common unit tests.
 */
class DefaultCpuImageBuffer(
    width: Int = 800,
    height: Int = 600,
    doubleBuffer: Boolean = false,
    initialAntialias: Boolean = true
) : ImageBuffer(
    width = max(1, width),
    height = max(1, height),
    doubleBuffer = doubleBuffer,
    enableDirtyTracking = true,
    pixelFormat = PixelFormat.RGBA_8888,
    colorSpace = ImageColorSpace.SRGB
) {
    override val backendTypeName: String = "COMMON_IMAGE_BUFFER"
    override val backendDescription: String = "ImageProcessing ImageBuffer Surface"

    private var _flushCount: Long = 0L
    override val flushCount: Long
        get() = _flushCount

    private var frontBytes: ByteArray = ByteArray(this.width * this.height * 4)
    private var bgraBytes: ByteArray = ByteArray(this.width * this.height * 4)

    override val context2D: ImageBuffer2DContext = DefaultCpuContext2DBackend(
        imageBuffer = this,
        initialAntialias = initialAntialias
    )

    override fun resize(newWidth: Int, newHeight: Int) {
        val w = max(1, newWidth)
        val h = max(1, newHeight)
        if (w == width && h == height) return
        updateDimensionsAndBytes(w, h, ByteArray(w * h * 4))
        frontBytes = ByteArray(w * h * 4)
        bgraBytes = ByteArray(w * h * 4)
        clearDirtyState()
    }

    override fun flush() {
        val src = bytes
        if (doubleBuffer) {
            if (frontBytes.size != src.size) {
                frontBytes = ByteArray(src.size)
            }
            src.copyInto(frontBytes)
        }
        val active = if (doubleBuffer) frontBytes else src
        if (bgraBytes.size != active.size) {
            bgraBytes = ByteArray(active.size)
        }
        var i = 0
        while (i + 3 < active.size) {
            bgraBytes[i] = active[i + 2]
            bgraBytes[i + 1] = active[i + 1]
            bgraBytes[i + 2] = active[i]
            bgraBytes[i + 3] = active[i + 3]
            i += 4
        }
        clearDirtyState()
        _flushCount++
    }

    override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val safeW = max(1, width)
        val safeH = max(1, height)
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
        markDirty(dx + startX, dy + startY, endX - startX, endY - startY)
    }

    override fun directRgbaBytes(): ByteArray =
        if (doubleBuffer && _flushCount > 0L) frontBytes else bytes

    override fun directBgraBytes(): ByteArray {
        flush()
        return bgraBytes
    }

    override fun dispose() {}
}
