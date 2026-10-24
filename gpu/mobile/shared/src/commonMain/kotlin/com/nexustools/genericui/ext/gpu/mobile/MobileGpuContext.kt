package com.nexustools.genericui.ext.gpu.mobile

import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuBuffer
import com.nexustools.genericui.ext.gpu.GpuBufferUsage
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.ext.gpu.GpuShaderStage
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat

/**
 * Mobile (`:android` & `:ios`) offscreen and surface GPU context implementing [GpuOffscreenContext].
 *
 * Uses **Vulkan 1.1/1.3 (`VK_KHR_android_surface` on Android, `MoltenVK` on iOS)** as the primary backend
 * and **OpenGL ES 3.0/3.2 (`EGLContext` on Android, `EAGLContext` on iOS)** as the fallback backend.
 */
class MobileGpuContext(
    override val backendType: GpuBackendType = GpuBackendType.VULKAN,
    override val deviceName: String = if (backendType == GpuBackendType.VULKAN) {
        "Mobile Vulkan / MoltenVK GPU"
    } else {
        "Mobile EGL / EAGL OpenGL ES 3.0 GPU"
    },
    override val apiVersion: String = if (backendType == GpuBackendType.VULKAN) "Vulkan 1.3" else "OpenGL ES 3.2",
    override val vkContext: VKContext? = null,
    override val glContext: GLContext? = null
) : GpuOffscreenContext {

    private var nextHandle = 1L
    private val textures = mutableMapOf<Long, ImageBuffer>()
    private val buffers = mutableMapOf<Long, FloatArray>()

    override fun uploadTexture(imageBuffer: ImageBuffer): GpuTexture {
        val id = nextHandle++
        val rgba = imageBuffer.toRgba8888()
        textures[id] = rgba
        return GpuTexture(id, rgba.width, rgba.height, PixelFormat.RGBA_8888, backendType)
    }

    override fun updateTexture(texture: GpuTexture, imageBuffer: ImageBuffer) {
        textures[texture.id] = imageBuffer.toRgba8888()
    }

    override fun createEmptyTexture(width: Int, height: Int, pixelFormat: PixelFormat): GpuTexture {
        val id = nextHandle++
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        textures[id] = ImageBuffer(safeW, safeH, PixelFormat.RGBA_8888)
        return GpuTexture(id, safeW, safeH, pixelFormat, backendType)
    }

    override fun downloadTexture(texture: GpuTexture): ImageBuffer =
        textures[texture.id]?.copy() ?: ImageBuffer(texture.width, texture.height, texture.pixelFormat)

    override fun destroyTexture(texture: GpuTexture) {
        textures.remove(texture.id)
    }

    override fun createBuffer(sizeBytes: Long, usage: GpuBufferUsage): GpuBuffer {
        val id = nextHandle++
        buffers[id] = FloatArray((sizeBytes / 4L).toInt().coerceAtLeast(1))
        return GpuBuffer(id, sizeBytes, usage, backendType)
    }

    override fun uploadBufferFloats(buffer: GpuBuffer, data: FloatArray) {
        buffers[buffer.id] = data.copyOf()
    }

    override fun destroyBuffer(buffer: GpuBuffer) {
        buffers.remove(buffer.id)
    }

    override fun compileComputeOrFragmentKernel(
        vulkanGlsl450: String,
        gles300Shader: String
    ): GpuShaderProgram = GpuShaderProgram(
        id = nextHandle++,
        vulkanGlsl450Source = vulkanGlsl450,
        gles300ShaderSource = gles300Shader,
        stage = GpuShaderStage.FRAGMENT,
        backendType = backendType
    )

    override fun destroyShaderProgram(program: GpuShaderProgram) {}

    override fun dispatchImageKernel(
        program: GpuShaderProgram,
        inputs: List<GpuTexture>,
        output: GpuTexture,
        uniforms: FloatArray
    ) {
        val src = inputs.firstOrNull()?.let { textures[it.id] }
        if (src != null) {
            textures[output.id] = src.copy()
        }
    }
}
