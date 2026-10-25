package com.nexustools.genericui.ext.gpu

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.RgbaColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hardware-accelerated offscreen double-buffered GPU surface (`backTexture` + `frontTexture`)
 * managed by [GpuOffscreenContext].
 *
 * - Prioritizes **Vulkan (`[GpuBackendType.VULKAN]`)** as the primary hardware backend,
 *   automatically falling back to **OpenGL ES / Google ANGLE (`[GpuBackendType.OPENGL_ES]`)**
 *   when Vulkan is unavailable.
 * - All drawing and shader operations target the offscreen back-buffer ([backTexture] / [backBuffer]).
 * - Calling [swapBuffers] uploads/executes the GPU presentation kernel (`#version 450` SPIR-V on Vulkan
 *   or `#version 300 es` on OpenGL ES / ANGLE) and atomically swaps the back-buffer into the presented
 *   front-buffer ([frontTexture] / [frontBuffer]) so applications and window compositors never observe
 *   partially rendered frames.
 */
class GpuDoubleBufferedSurface(
    width: Int = 800,
    height: Int = 600,
    val gpuContext: GpuOffscreenContext = GpuDevice.acquireOffscreenContext(preferVulkan = true)
) {
    var width: Int = max(1, width)
        private set

    var height: Int = max(1, height)
        private set

    val backendType: GpuBackendType
        get() = gpuContext.backendType

    val backendDescription: String
        get() = "${gpuContext.backendType} (${gpuContext.deviceName} - ${gpuContext.apiVersion})"

    var backTexture: GpuTexture = gpuContext.createEmptyTexture(this.width, this.height, PixelFormat.RGBA_8888)
        private set

    var frontTexture: GpuTexture = gpuContext.createEmptyTexture(this.width, this.height, PixelFormat.RGBA_8888)
        private set

    var backBuffer: ImageBuffer = ImageBuffer(this.width, this.height, PixelFormat.RGBA_8888)
        private set

    var frontBuffer: ImageBuffer = ImageBuffer(this.width, this.height, PixelFormat.RGBA_8888)
        private set

    private var frontBgraBytes: ByteArray = ByteArray(this.width * this.height * 4)
    private var backBufferDirty: Boolean = true
    private var dirtyMinX: Int = 0
    private var dirtyMinY: Int = 0
    private var dirtyMaxX: Int = this.width
    private var dirtyMaxY: Int = this.height

    var swapCount: Long = 0L
        private set

    val presentBlitShaderProgram: GpuShaderProgram = gpuContext.compileComputeOrFragmentKernel(
        vulkanGlsl450 = VULKAN_DOUBLE_BUFFER_BLIT_GLSL_450,
        gles300Shader = GLES_300_DOUBLE_BUFFER_BLIT_SHADER
    )

    val isBackBufferDirty: Boolean
        get() = backBufferDirty

    fun markBackBufferDirty() {
        backBufferDirty = true
        dirtyMinX = 0
        dirtyMinY = 0
        dirtyMaxX = width
        dirtyMaxY = height
    }

    fun markBackBufferDirtyRegion(x0: Int, y0: Int, x1: Int, y1: Int) {
        val sx = x0.coerceIn(0, width)
        val sy = y0.coerceIn(0, height)
        val ex = x1.coerceIn(sx, width)
        val ey = y1.coerceIn(sy, height)
        if (ex <= sx || ey <= sy) return
        if (!backBufferDirty) {
            backBufferDirty = true
            dirtyMinX = sx
            dirtyMinY = sy
            dirtyMaxX = ex
            dirtyMaxY = ey
        } else {
            if (sx < dirtyMinX) dirtyMinX = sx
            if (sy < dirtyMinY) dirtyMinY = sy
            if (ex > dirtyMaxX) dirtyMaxX = ex
            if (ey > dirtyMaxY) dirtyMaxY = ey
        }
    }

    fun resize(newWidth: Int, newHeight: Int) {
        val safeW = max(1, newWidth)
        val safeH = max(1, newHeight)
        if (safeW == width && safeH == height) return

        gpuContext.destroyTexture(backTexture)
        gpuContext.destroyTexture(frontTexture)

        width = safeW
        height = safeH
        backTexture = gpuContext.createEmptyTexture(safeW, safeH, PixelFormat.RGBA_8888)
        frontTexture = gpuContext.createEmptyTexture(safeW, safeH, PixelFormat.RGBA_8888)
        backBuffer = ImageBuffer(safeW, safeH, PixelFormat.RGBA_8888)
        frontBuffer = ImageBuffer(safeW, safeH, PixelFormat.RGBA_8888)
        frontBgraBytes = ByteArray(safeW * safeH * 4)
        markBackBufferDirty()
    }

    /**
     * Atomically commits the offscreen back-buffer to the GPU [backTexture], dispatches the hardware
     * Vulkan / OpenGL ES (ANGLE) presentation shader into [frontTexture], swaps the GPU texture handles,
     * and updates the presented RGBA and BGRA front buffers over the active dirty region.
     */
    fun swapBuffers() {
        if (!backBufferDirty && swapCount > 0L) return

        val minX = dirtyMinX.coerceIn(0, width)
        val minY = dirtyMinY.coerceIn(0, height)
        val maxX = dirtyMaxX.coerceIn(minX, width)
        val maxY = dirtyMaxY.coerceIn(minY, height)

        // 1. Upload offscreen staging buffer into GPU backTexture
        gpuContext.updateTexture(backTexture, backBuffer)

        // 2. Dispatch hardware-accelerated Vulkan (#version 450) or OpenGL ES / ANGLE (#version 300 es) blit kernel
        gpuContext.dispatchImageKernel(
            program = presentBlitShaderProgram,
            inputs = listOf(backTexture),
            output = frontTexture,
            uniforms = floatArrayOf(width.toFloat(), height.toFloat(), 1.0f, swapCount.toFloat())
        )

        // 3. Swap GPU front/back texture handles and synchronize presented front buffers
        val previousFront = frontTexture
        frontTexture = backTexture
        backTexture = previousFront

        val srcBytes = backBuffer.bytes
        val dstBytes = frontBuffer.bytes
        val totalBytes = min(srcBytes.size, dstBytes.size)
        if (frontBgraBytes.size != totalBytes) {
            frontBgraBytes = ByteArray(totalBytes)
        }

        if (minX == 0 && maxX == width) {
            val startByte = (minY * width) shl 2
            val endByte = min(totalBytes, (maxY * width) shl 2)
            if (endByte > startByte) {
                srcBytes.copyInto(dstBytes, startByte, startByte, endByte)
                var i = startByte
                while (i + 3 < endByte) {
                    val r = dstBytes[i]
                    val g = dstBytes[i + 1]
                    val b = dstBytes[i + 2]
                    val a = dstBytes[i + 3]
                    frontBgraBytes[i] = b
                    frontBgraBytes[i + 1] = g
                    frontBgraBytes[i + 2] = r
                    frontBgraBytes[i + 3] = a
                    i += 4
                }
            }
        } else {
            val rowSpanBytes = (maxX - minX) shl 2
            for (py in minY until maxY) {
                val startByte = ((py * width) + minX) shl 2
                val endByte = min(totalBytes, startByte + rowSpanBytes)
                if (endByte > startByte) {
                    srcBytes.copyInto(dstBytes, startByte, startByte, endByte)
                    var i = startByte
                    while (i + 3 < endByte) {
                        val r = dstBytes[i]
                        val g = dstBytes[i + 1]
                        val b = dstBytes[i + 2]
                        val a = dstBytes[i + 3]
                        frontBgraBytes[i] = b
                        frontBgraBytes[i + 1] = g
                        frontBgraBytes[i + 2] = r
                        frontBgraBytes[i + 3] = a
                        i += 4
                    }
                }
            }
        }

        backBufferDirty = false
        dirtyMinX = width
        dirtyMinY = height
        dirtyMaxX = 0
        dirtyMaxY = 0
        swapCount++
    }

    fun presentFrontImageBuffer(): ImageBuffer {
        if (backBufferDirty) {
            swapBuffers()
        }
        return frontBuffer
    }

    fun presentFrontRgbaBytes(): ByteArray {
        if (backBufferDirty) {
            swapBuffers()
        }
        return frontBuffer.bytes
    }

    fun presentFrontBgraBytes(): ByteArray {
        if (backBufferDirty) {
            swapBuffers()
        }
        return frontBgraBytes
    }

    fun clearBackBuffer(x0: Int, y0: Int, x1: Int, y1: Int, color: RgbaColor) {
        val sx = x0.coerceIn(0, width)
        val sy = y0.coerceIn(0, height)
        val ex = x1.coerceIn(0, width)
        val ey = y1.coerceIn(0, height)
        val bytes = backBuffer.bytes
        val r = color.r.toByte()
        val g = color.g.toByte()
        val b = color.b.toByte()
        val a = color.a.toByte()
        if (sx == 0 && sy == 0 && ex == width && ey == height && r == 0.toByte() && g == 0.toByte() && b == 0.toByte() && a == 0.toByte()) {
            bytes.fill(0)
            markBackBufferDirty()
            return
        }
        for (py in sy until ey) {
            val rowBase = py * width
            for (px in sx until ex) {
                val idx = (rowBase + px) shl 2
                if (idx + 3 < bytes.size) {
                    bytes[idx] = r
                    bytes[idx + 1] = g
                    bytes[idx + 2] = b
                    bytes[idx + 3] = a
                }
            }
        }
        markBackBufferDirtyRegion(sx, sy, ex, ey)
    }

    fun blendPixelOnBackBuffer(px: Int, py: Int, color: RgbaColor, globalAlpha: Float, compositeOp: String) {
        if (px !in 0 until width || py !in 0 until height) return
        val bytes = backBuffer.bytes
        val idx = (py * width + px) * 4
        if (idx + 3 >= bytes.size) return
        markBackBufferDirtyRegion(px, py, px + 1, py + 1)
        val srcA = ((color.a / 255f) * globalAlpha).coerceIn(0f, 1f)
        if (srcA >= 0.995f || compositeOp == "copy") {
            bytes[idx] = color.r.toByte()
            bytes[idx + 1] = color.g.toByte()
            bytes[idx + 2] = color.b.toByte()
            bytes[idx + 3] = (srcA * 255f).roundToInt().coerceIn(0, 255).toByte()
        } else if (srcA > 0.001f) {
            val invA = 1f - srcA
            val dstR = bytes[idx].toInt() and 0xFF
            val dstG = bytes[idx + 1].toInt() and 0xFF
            val dstB = bytes[idx + 2].toInt() and 0xFF
            val dstA = (bytes[idx + 3].toInt() and 0xFF) / 255f
            val outA = (srcA + dstA * invA).coerceIn(0f, 1f)
            bytes[idx] = (color.r * srcA + dstR * invA).roundToInt().coerceIn(0, 255).toByte()
            bytes[idx + 1] = (color.g * srcA + dstG * invA).roundToInt().coerceIn(0, 255).toByte()
            bytes[idx + 2] = (color.b * srcA + dstB * invA).roundToInt().coerceIn(0, 255).toByte()
            bytes[idx + 3] = (outA * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    fun dispose() {
        gpuContext.destroyShaderProgram(presentBlitShaderProgram)
        gpuContext.destroyTexture(backTexture)
        gpuContext.destroyTexture(frontTexture)
    }

    companion object {
        const val VULKAN_DOUBLE_BUFFER_BLIT_GLSL_450: String = """
            #version 450
            layout(set = 0, binding = 0) uniform sampler2D uBackBufferTex;
            layout(push_constant) uniform PushConstants {
                vec2 uResolution;
                float uOpacity;
                float uFrameIndex;
            } pc;
            layout(location = 0) in vec2 vUv;
            layout(location = 0) out vec4 outColor;
            void main() {
                vec4 c = texture(uBackBufferTex, vUv);
                outColor = vec4(c.rgb, c.a * pc.uOpacity);
            }
        """

        const val GLES_300_DOUBLE_BUFFER_BLIT_SHADER: String = """
            #version 300 es
            precision highp float;
            uniform sampler2D uBackBufferTex;
            uniform vec4 uParams;
            in vec2 vUv;
            out vec4 outColor;
            void main() {
                vec4 c = texture(uBackBufferTex, vUv);
                outColor = vec4(c.rgb, c.a * uParams.z);
            }
        """
    }
}
