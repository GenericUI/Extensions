package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDoubleBufferedSurface
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.RgbaColor
import kotlin.math.max
import kotlin.math.min

/**
 * Hardware-accelerated offscreen double-buffered 3D canvas (`OffscreenCanvas3D`) in `:extensions:canvas3d`,
 * backed by `:extensions:gpu`'s [GpuDoubleBufferedSurface] and [GpuOffscreenContext].
 *
 * - Hardware-accelerated via **Vulkan (`[GpuBackendType.VULKAN]`)** when available, or
 *   **OpenGL ES / Google ANGLE (`[GpuBackendType.OPENGL_ES]`)** as an automatic fallback.
 * - Maintains offscreen color back/front textures ([GpuDoubleBufferedSurface.backTexture] /
 *   [GpuDoubleBufferedSurface.frontTexture]) alongside a 32-bit floating-point depth buffer ([depthBuffer]).
 * - Supports rendering 3D passes via either [VKContext] ([renderVulkanPass]) or [GLContext] ([renderOpenGlEsPass])
 *   and atomically presenting the completed frame via [swapBuffers].
 */
class OffscreenCanvas3D(
    width: Int = 800,
    height: Int = 600,
    val preferVulkan: Boolean = true,
    val vkConfig: VKCanvasConfig = VKCanvasConfig(),
    val glConfig: GLCanvasConfig = GLCanvasConfig(),
    val gpuContext: GpuOffscreenContext = Canvas3DExtension.acquireGpuOffscreenContext(preferVulkan = preferVulkan)
) {
    val surface: GpuDoubleBufferedSurface = GpuDoubleBufferedSurface(width, height, gpuContext)

    val width: Int
        get() = surface.width

    val height: Int
        get() = surface.height

    val backendType: GpuBackendType
        get() = surface.backendType

    val backendDescription: String
        get() = surface.backendDescription

    val swapCount: Long
        get() = surface.swapCount

    var depthBuffer: FloatArray = FloatArray(this.width * this.height) { 1.0f }
        private set

    val vkContext: VKContext by lazy {
        gpuContext.vkContext ?: Canvas3DExtension.createVKContext(vkConfig, this.width, this.height)
    }

    val glContext: GLContext by lazy {
        gpuContext.glContext ?: Canvas3DExtension.createGLContext(glConfig, this.width, this.height)
    }

    val resolve3DShaderProgram: GpuShaderProgram = gpuContext.compileComputeOrFragmentKernel(
        vulkanGlsl450 = VULKAN_CANVAS3D_RESOLVE_GLSL_450,
        gles300Shader = GLES_300_CANVAS3D_RESOLVE_SHADER
    )

    fun resize(newWidth: Int, newHeight: Int) {
        val safeW = max(1, newWidth)
        val safeH = max(1, newHeight)
        if (safeW == width && safeH == height) return
        surface.resize(safeW, safeH)
        depthBuffer = FloatArray(safeW * safeH) { 1.0f }
        vkContext.setViewport(0f, 0f, safeW.toFloat(), safeH.toFloat(), 0f, 1f)
        glContext.viewport(0, 0, safeW, safeH)
    }

    /**
     * Clears the offscreen back-buffer color and depth attachments at the start of a 3D frame.
     */
    fun beginFrame(
        clearColor: RgbaColor = RgbaColor(15, 23, 42, 255),
        clearDepth: Float = 1.0f
    ) {
        surface.clearBackBuffer(0, 0, width, height, clearColor)
        depthBuffer.fill(clearDepth)
        if (backendType == GpuBackendType.VULKAN) {
            vkContext.beginRenderPass(clearColor, clearDepth)
        } else {
            glContext.viewport(0, 0, width, height)
            glContext.clearColor(
                clearColor.r / 255f,
                clearColor.g / 255f,
                clearColor.b / 255f,
                clearColor.a / 255f
            )
            glContext.clearDepth(clearDepth)
            glContext.clear(GLContext.GL_COLOR_BUFFER_BIT or GLContext.GL_DEPTH_BUFFER_BIT)
        }
    }

    /**
     * Executes a 3D rendering pass using [VKContext] into the offscreen back-buffer and optionally swaps buffers.
     */
    fun renderVulkanPass(
        clearColor: RgbaColor = RgbaColor(15, 23, 42, 255),
        clearDepth: Float = 1.0f,
        autoSwap: Boolean = true,
        block: (VKContext) -> Unit
    ) {
        beginFrame(clearColor, clearDepth)
        block(vkContext)
        vkContext.endRenderPass()
        surface.markBackBufferDirty()
        if (autoSwap) {
            swapBuffers()
        }
    }

    /**
     * Executes a 3D rendering pass using [GLContext] (OpenGL ES 3.0 / Google ANGLE) into the offscreen
     * back-buffer and optionally swaps buffers.
     */
    fun renderOpenGlEsPass(
        clearColor: RgbaColor = RgbaColor(15, 23, 42, 255),
        clearDepth: Float = 1.0f,
        autoSwap: Boolean = true,
        block: (GLContext) -> Unit
    ) {
        beginFrame(clearColor, clearDepth)
        block(glContext)
        glContext.flush()
        val glPixels = glContext.readPixelsRgba(0, 0, width, height)
        val dstBytes = surface.backBuffer.bytes
        val copyLen = min(glPixels.size, dstBytes.size)
        if (copyLen > 0 && glPixels.any { it.toInt() != 0 }) {
            glPixels.copyInto(dstBytes, 0, 0, copyLen)
        }
        surface.markBackBufferDirty()
        if (autoSwap) {
            swapBuffers()
        }
    }

    /**
     * Writes a depth-tested 3D fragment `(px, py, depthZ)` onto the offscreen back-buffer.
     */
    fun writeDepthTestedFragment(
        px: Int,
        py: Int,
        depthZ: Float,
        color: RgbaColor,
        globalAlpha: Float = 1.0f
    ): Boolean {
        if (px !in 0 until width || py !in 0 until height) return false
        val depthIdx = py * width + px
        if (depthIdx !in depthBuffer.indices) return false
        if (depthZ <= depthBuffer[depthIdx]) {
            depthBuffer[depthIdx] = depthZ
            surface.blendPixelOnBackBuffer(px, py, color, globalAlpha, "source-over")
            return true
        }
        return false
    }

    fun uploadTexture(imageBuffer: ImageBuffer): GpuTexture =
        gpuContext.uploadTexture(imageBuffer)

    fun swapBuffers() {
        surface.swapBuffers()
    }

    fun presentFrontImageBuffer(): ImageBuffer = surface.presentFrontImageBuffer()

    fun presentFrontBufferRgba(): ByteArray = surface.presentFrontRgbaBytes()

    fun presentFrontBufferBgra(): ByteArray = surface.presentFrontBgraBytes()

    fun toImageBuffer(): ImageBuffer = surface.presentFrontImageBuffer().copy()

    fun dispose() {
        gpuContext.destroyShaderProgram(resolve3DShaderProgram)
        surface.dispose()
    }

    companion object {
        const val VULKAN_CANVAS3D_RESOLVE_GLSL_450: String = """
            #version 450
            layout(set = 0, binding = 0) uniform sampler2D uColorAttachmentTex;
            layout(push_constant) uniform PushConstants {
                vec2 uResolution;
                float uExposure;
                float uGamma;
            } pc;
            layout(location = 0) in vec2 vUv;
            layout(location = 0) out vec4 outColor;
            void main() {
                vec4 hdr = texture(uColorAttachmentTex, vUv);
                vec3 mapped = vec3(1.0) - exp(-hdr.rgb * max(0.01, pc.uExposure));
                outColor = vec4(mapped, hdr.a);
            }
        """

        const val GLES_300_CANVAS3D_RESOLVE_SHADER: String = """
            #version 300 es
            precision highp float;
            uniform sampler2D uColorAttachmentTex;
            uniform vec4 uParams;
            in vec2 vUv;
            out vec4 outColor;
            void main() {
                vec4 hdr = texture(uColorAttachmentTex, vUv);
                vec3 mapped = vec3(1.0) - exp(-hdr.rgb * max(0.01, uParams.z));
                outColor = vec4(mapped, hdr.a);
            }
        """
    }
}
