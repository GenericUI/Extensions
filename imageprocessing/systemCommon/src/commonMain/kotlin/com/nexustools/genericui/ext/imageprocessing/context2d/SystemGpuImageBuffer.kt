package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.gpu.system.SystemGpuDirtyRegion
import com.nexustools.genericui.ext.gpu.system.SystemGpuShaderCache
import com.nexustools.genericui.ext.gpu.system.SystemVBlankSurfaceSynchronizer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.RgbaColor
import kotlin.math.max
import kotlin.math.min

/**
 * Hardware-accelerated **Vulkan / OpenGL ES surface** in `:extensions:imageprocessing:sharedSystem`
 * extending [ImageBuffer] (`:extensions:imageprocessing:common`) for all non-web System targets
 * (Desktop, Swing, Mobile).
 *
 * - Supports a [doubleBuffer] flag (`false` by default; `true` when used by `Canvas2D`):
 *   - When `doubleBuffer == false`: unbuffered direct GL/Vulkan surface (`[activeDrawBytes] === [bytes]`)
 *     that [context2D] ([GpuAcceleratedContext2DBackend]) mutates directly in-place with zero
 *     back-to-front copy overhead.
 *   - When `doubleBuffer == true`: maintains an offscreen back buffer ([backBytes]) that [context2D]
 *     renders into, and [flush] / [swapBuffers] atomically commits the dirty region into the
 *     presented front buffer ([bytes], [gpuTexture], and [bgraBufferBytes]).
 * - Provides [getContext] (`imageBuffer.getContext()`) returning [context2D] ([ImageBuffer2DContext])
 *   so [SystemGpuImageBuffer] can be used both as the backend of `Canvas2D` and directly for offscreen
 *   2D rendering.
 */
class SystemGpuImageBuffer(
    width: Int = 800,
    height: Int = 600,
    doubleBuffer: Boolean = false,
    val initialAntialias: Boolean = true,
    val gpuContext: GpuOffscreenContext = GpuDevice.acquireOffscreenContext(preferVulkan = true),
    colorSpace: ImageColorSpace = ImageColorSpace.SRGB
) : ImageBuffer(
    width = max(1, width),
    height = max(1, height),
    doubleBuffer = doubleBuffer,
    enableDirtyTracking = true,
    pixelFormat = PixelFormat.RGBA_8888,
    colorSpace = colorSpace
) {
    val backendType: GpuBackendType
        get() = gpuContext.backendType

    constructor(
        width: Int = 800,
        height: Int = 600,
        doubleBuffer: Boolean = false,
        initialAntialias: Boolean = true,
        preferVulkan: Boolean = true
    ) : this(
        width = width,
        height = height,
        doubleBuffer = doubleBuffer,
        initialAntialias = initialAntialias,
        gpuContext = GpuDevice.acquireOffscreenContext(preferVulkan = preferVulkan),
        colorSpace = ImageColorSpace.SRGB
    )

    override val backendTypeName: String
        get() = gpuContext.backendType.name

    override val backendDescription: String
        get() = "${gpuContext.backendType} (${gpuContext.deviceName} - ${gpuContext.apiVersion})"

    var gpuTexture: GpuTexture = gpuContext.createEmptyTexture(this.width, this.height, PixelFormat.RGBA_8888)
        private set

    private var backBytes: ByteArray = if (doubleBuffer) ByteArray(this.width * this.height * 4) else ByteArray(0)
    private var bgraBufferBytes: ByteArray = ByteArray(this.width * this.height * 4)
    val dirtyRegion: SystemGpuDirtyRegion = SystemGpuDirtyRegion(this.width, this.height)

    /**
     * Returns the active RGBA byte buffer that [context2D] draws into (`backBytes` when
     * [doubleBuffer] is `true`, or direct [bytes] when [doubleBuffer] is `false`).
     */
    val activeDrawBytes: ByteArray
        get() = if (doubleBuffer) backBytes else bytes

    override var flushCount: Long = 0L
        private set

    val directBlitShader: GpuShaderProgram = SystemGpuShaderCache.getOrCompile(
        gpuContext = gpuContext,
        shaderKey = GpuContext2DShaderCatalog.KEY_IMAGE_BLIT_SWIZZLE,
        vulkanGlsl450 = GpuContext2DShaderCatalog.VULKAN_IMAGE_BLIT_SWIZZLE_450,
        gles300Shader = GpuContext2DShaderCatalog.GLES_IMAGE_BLIT_SWIZZLE_300
    )

    override val context2D: GpuAcceleratedContext2DBackend by lazy {
        GpuAcceleratedContext2DBackend(this).also {
            it.antialias = initialAntialias
        }
    }

    init {
        installFactory()
    }

    override fun getContext(contextType: String): GpuAcceleratedContext2DBackend = context2D

    fun markDirtyRect(x0: Int, y0: Int, x1: Int, y1: Int) {
        dirtyRegion.markRectDirty(x0, y0, x1, y1)
        markDirty(x0, y0, max(0, x1 - x0), max(0, y1 - y0))
    }

    fun markAllDirty() {
        dirtyRegion.markAllDirty()
        markDirty(0, 0, width, height)
    }

    override fun setPixel(x: Int, y: Int, color: RgbaColor) {
        if (x !in 0 until width || y !in 0 until height) return
        val target = activeDrawBytes
        val idx = (y * width + x) shl 2
        if (idx + 3 < target.size) {
            target[idx] = color.r.toByte()
            target[idx + 1] = color.g.toByte()
            target[idx + 2] = color.b.toByte()
            target[idx + 3] = color.a.toByte()
            markDirtyRect(x, y, x + 1, y + 1)
        }
    }

    override fun getPixel(x: Int, y: Int): RgbaColor {
        if (x !in 0 until width || y !in 0 until height) return RgbaColor.TRANSPARENT
        val target = activeDrawBytes
        val idx = (y * width + x) shl 2
        if (idx + 3 >= target.size) return RgbaColor.TRANSPARENT
        return RgbaColor(
            r = target[idx].toInt() and 0xFF,
            g = target[idx + 1].toInt() and 0xFF,
            b = target[idx + 2].toInt() and 0xFF,
            a = target[idx + 3].toInt() and 0xFF
        )
    }

    override fun fill(color: RgbaColor): com.nexustools.genericui.image.ImageBuffer {
        clearRectDirect(0, 0, width, height, color)
        return this
    }

    override fun resize(newWidth: Int, newHeight: Int) {
        val safeW = max(1, newWidth)
        val safeH = max(1, newHeight)
        if (safeW == width && safeH == height) return

        gpuContext.destroyTexture(gpuTexture)
        updateDimensionsAndBytes(
            newWidth = safeW,
            newHeight = safeH,
            newBytes = ByteArray(safeW * safeH * 4)
        )
        if (doubleBuffer) {
            backBytes = ByteArray(safeW * safeH * 4)
        }
        gpuTexture = gpuContext.createEmptyTexture(safeW, safeH, PixelFormat.RGBA_8888)
        bgraBufferBytes = ByteArray(safeW * safeH * 4)
        dirtyRegion.resize(safeW, safeH)
        markAllDirty()
    }

    override fun flush() {
        if (!dirtyRegion.isDirty && flushCount > 0L) return

        val minX = dirtyRegion.minX.coerceIn(0, width)
        val minY = dirtyRegion.minY.coerceIn(0, height)
        val maxX = dirtyRegion.maxX.coerceIn(minX, width)
        val maxY = dirtyRegion.maxY.coerceIn(minY, height)

        val srcBytes = activeDrawBytes
        val frontBytes = bytes
        val totalBytes = frontBytes.size
        if (bgraBufferBytes.size != totalBytes) {
            bgraBufferBytes = ByteArray(totalBytes)
        }

        if (minX == 0 && maxX == width) {
            val startByte = (minY * width) shl 2
            val endByte = min(totalBytes, (maxY * width) shl 2)
            if (doubleBuffer && endByte > startByte) {
                srcBytes.copyInto(frontBytes, startByte, startByte, endByte)
            }
            var i = startByte
            while (i + 3 < endByte) {
                val r = srcBytes[i]
                val g = srcBytes[i + 1]
                val b = srcBytes[i + 2]
                val a = srcBytes[i + 3]
                bgraBufferBytes[i] = b
                bgraBufferBytes[i + 1] = g
                bgraBufferBytes[i + 2] = r
                bgraBufferBytes[i + 3] = a
                i += 4
            }
        } else {
            val rowSpanBytes = (maxX - minX) shl 2
            for (py in minY until maxY) {
                var i = ((py * width) + minX) shl 2
                val endByte = min(totalBytes, i + rowSpanBytes)
                if (doubleBuffer && endByte > i) {
                    srcBytes.copyInto(frontBytes, i, i, endByte)
                }
                while (i + 3 < endByte) {
                    val r = srcBytes[i]
                    val g = srcBytes[i + 1]
                    val b = srcBytes[i + 2]
                    val a = srcBytes[i + 3]
                    bgraBufferBytes[i] = b
                    bgraBufferBytes[i + 1] = g
                    bgraBufferBytes[i + 2] = r
                    bgraBufferBytes[i + 3] = a
                    i += 4
                }
            }
        }

        gpuContext.updateTexture(gpuTexture, this)

        dirtyRegion.clearDirty()
        clearDirtyState()
        flushCount++
    }

    fun clearRectDirect(x0: Int, y0: Int, x1: Int, y1: Int, color: RgbaColor) {
        val sx = x0.coerceIn(0, width)
        val sy = y0.coerceIn(0, height)
        val ex = x1.coerceIn(0, width)
        val ey = y1.coerceIn(0, height)
        if (ex <= sx || ey <= sy) return

        val raw = activeDrawBytes
        val r = color.r.toByte()
        val g = color.g.toByte()
        val b = color.b.toByte()
        val a = color.a.toByte()
        if (sx == 0 && sy == 0 && ex == width && ey == height &&
            r == 0.toByte() && g == 0.toByte() && b == 0.toByte() && a == 0.toByte()
        ) {
            raw.fill(0)
            markAllDirty()
            return
        }
        for (py in sy until ey) {
            val rowBase = py * width
            for (px in sx until ex) {
                val idx = (rowBase + px) shl 2
                if (idx + 3 < raw.size) {
                    raw[idx] = r
                    raw[idx + 1] = g
                    raw[idx + 2] = b
                    raw[idx + 3] = a
                }
            }
        }
        markDirtyRect(sx, sy, ex, ey)
    }

    override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        if (!doubleBuffer && dirtyRegion.isDirty) {
            flush()
        } else if (doubleBuffer && flushCount == 0L && dirtyRegion.isDirty) {
            flush()
        }
        val safeW = max(1, width)
        val safeH = max(1, height)
        val srcBytes = activeDrawBytes
        val surfW = this.width
        val surfH = this.height

        if (x == 0 && y == 0 && safeW == surfW && safeH == surfH) {
            return srcBytes.copyOf()
        }

        val out = ByteArray(safeW * safeH * 4)
        for (ry in 0 until safeH) {
            val sy = y + ry
            if (sy !in 0 until surfH) continue
            val startRx = max(0, -x)
            val endRx = min(safeW, surfW - x)
            if (endRx <= startRx) continue
            val copyBytes = (endRx - startRx) shl 2
            val srcOffset = ((sy * surfW) + (x + startRx)) shl 2
            val dstOffset = ((ry * safeW) + startRx) shl 2
            srcBytes.copyInto(out, dstOffset, srcOffset, srcOffset + copyBytes)
        }
        return out
    }

    override fun writePixelsRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        val dstBytes = activeDrawBytes
        val dstW = this.width
        val dstH = this.height
        val startY = max(0, -dy)
        val endY = min(height, dstH - dy)
        val startX = max(0, -dx)
        val endX = min(width, dstW - dx)
        if (endX <= startX || endY <= startY) return

        val rowBytes = (endX - startX) shl 2
        for (sy in startY until endY) {
            val srcOffset = ((sy * width) + startX) shl 2
            val dstOffset = (((dy + sy) * dstW) + (dx + startX)) shl 2
            if (srcOffset + rowBytes <= rgbaBytes.size && dstOffset + rowBytes <= dstBytes.size) {
                rgbaBytes.copyInto(dstBytes, dstOffset, srcOffset, srcOffset + rowBytes)
            }
        }
        markDirtyRect(dx + startX, dy + startY, dx + endX, dy + endY)
    }

    override fun directRgbaBytes(): ByteArray {
        if (!doubleBuffer && dirtyRegion.isDirty) {
            flush()
        } else if (doubleBuffer && flushCount == 0L && dirtyRegion.isDirty) {
            flush()
        }
        return bytes
    }

    override fun directBgraBytes(): ByteArray {
        if (!doubleBuffer && dirtyRegion.isDirty) {
            flush()
        } else if (doubleBuffer && flushCount == 0L && dirtyRegion.isDirty) {
            flush()
        }
        return bgraBufferBytes
    }

    override fun dispose() {
        gpuContext.destroyTexture(gpuTexture)
    }

    companion object {
        fun installFactory(preferVulkan: Boolean = true) {
            SystemVBlankSurfaceSynchronizer.install()
            GpuDevice.ensureDefaultSystemProvidersInstalled(
                enableVulkan = preferVulkan,
                enableOpenGlEs = true
            )
            ImageBuffer.registerFactory { width, height, doubleBuffer, antialias ->
                SystemGpuImageBuffer(
                    width = width,
                    height = height,
                    doubleBuffer = doubleBuffer,
                    initialAntialias = antialias,
                    gpuContext = GpuDevice.acquireOffscreenContext(preferVulkan = preferVulkan)
                )
            }
        }
    }
}
