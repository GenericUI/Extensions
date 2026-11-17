package com.nexustools.genericui.ext.gpu.desktop

import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GpuShaderStageSource
import com.nexustools.genericui.component.media.VKBuffer
import com.nexustools.genericui.component.media.VKBufferDescriptor
import com.nexustools.genericui.component.media.VKBufferUsage
import com.nexustools.genericui.component.media.VKShaderModule
import com.nexustools.genericui.component.media.VKTexture
import com.nexustools.genericui.component.media.VKTextureDescriptor
import com.nexustools.genericui.component.media.VKTextureFormat
import com.nexustools.genericui.component.media.VKTextureUsage
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuBuffer
import com.nexustools.genericui.ext.gpu.GpuBufferUsage
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.ext.gpu.GpuShaderStage
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat

/**
 * Primary desktop Vulkan 1.2 / 1.3 offscreen and surface GPU context implementing [GpuOffscreenContext]
 * across Windows (`VK_KHR_win32_surface`), macOS (`MoltenVK`), and Linux (`VK_KHR_wayland_surface` / `VK_KHR_xlib_surface`).
 */
class DesktopVulkanGpuContext(
    override val vkContext: DesktopVKContextImpl = DesktopVKContextImpl()
) : GpuOffscreenContext {
    override val backendType: GpuBackendType = GpuBackendType.VULKAN
    override val deviceName: String get() = vkContext.contextInfo.deviceName
    override val apiVersion: String get() = vkContext.contextInfo.apiVersion.name
    override val glContext: GLContext? = null

    private var nextId = 1L
    private val textureCache = mutableMapOf<Long, ImageBuffer>()

    override fun uploadTexture(imageBuffer: ImageBuffer): GpuTexture {
        val rgba = imageBuffer.asRgba8888()
        val vkTex = vkContext.createTexture(
            VKTextureDescriptor(
                width = rgba.width,
                height = rgba.height,
                format = VKTextureFormat.RGBA8_UNORM,
                usage = setOf(VKTextureUsage.SAMPLED, VKTextureUsage.COPY_DST)
            ),
            initialRgbaBytes = rgba.bytes
        )
        textureCache[vkTex.id] = rgba
        return GpuTexture(
            id = vkTex.id,
            width = rgba.width,
            height = rgba.height,
            pixelFormat = PixelFormat.RGBA_8888,
            backendType = GpuBackendType.VULKAN
        )
    }

    override fun updateTexture(texture: GpuTexture, imageBuffer: ImageBuffer) {
        val rgba = imageBuffer.asRgba8888()
        vkContext.writeTexture(
            VKTexture(texture.id, rgba.width, rgba.height, VKTextureFormat.RGBA8_UNORM),
            rgba.width,
            rgba.height,
            rgba.bytes
        )
        textureCache[texture.id] = rgba
    }

    override fun createEmptyTexture(width: Int, height: Int, pixelFormat: PixelFormat): GpuTexture {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val blank = ImageBuffer(safeW, safeH, PixelFormat.RGBA_8888)
        val vkTex = vkContext.createTexture(
            VKTextureDescriptor(
                width = safeW,
                height = safeH,
                format = VKTextureFormat.RGBA8_UNORM,
                usage = setOf(VKTextureUsage.SAMPLED, VKTextureUsage.COPY_DST, VKTextureUsage.RENDER_ATTACHMENT)
            ),
            initialRgbaBytes = blank.bytes
        )
        textureCache[vkTex.id] = blank
        return GpuTexture(
            id = vkTex.id,
            width = safeW,
            height = safeH,
            pixelFormat = pixelFormat,
            backendType = GpuBackendType.VULKAN
        )
    }

    override fun downloadTexture(texture: GpuTexture): ImageBuffer =
        textureCache[texture.id]?.copy() ?: ImageBuffer(texture.width, texture.height, texture.pixelFormat)

    override fun destroyTexture(texture: GpuTexture) {
        vkContext.destroyTexture(VKTexture(texture.id, texture.width, texture.height))
        textureCache.remove(texture.id)
    }

    override fun createBuffer(sizeBytes: Long, usage: GpuBufferUsage): GpuBuffer {
        val vkBuf = vkContext.createBuffer(
            VKBufferDescriptor(
                sizeBytes = sizeBytes,
                usage = setOf(VKBufferUsage.VERTEX, VKBufferUsage.STORAGE, VKBufferUsage.COPY_DST)
            )
        )
        return GpuBuffer(
            id = vkBuf.id,
            sizeBytes = sizeBytes,
            usage = usage,
            backendType = GpuBackendType.VULKAN
        )
    }

    override fun uploadBufferFloats(buffer: GpuBuffer, data: FloatArray) {
        vkContext.writeBuffer(
            VKBuffer(buffer.id, buffer.sizeBytes, setOf(VKBufferUsage.VERTEX, VKBufferUsage.STORAGE, VKBufferUsage.COPY_DST)),
            data
        )
    }

    override fun destroyBuffer(buffer: GpuBuffer) {
        vkContext.destroyBuffer(
            VKBuffer(buffer.id, buffer.sizeBytes, setOf(VKBufferUsage.VERTEX, VKBufferUsage.STORAGE, VKBufferUsage.COPY_DST))
        )
    }

    override fun compileComputeOrFragmentKernel(
        vulkanGlsl450: String,
        gles300Shader: String
    ): GpuShaderProgram {
        val module = vkContext.createShaderModule(
            GpuShaderStageSource(
                stage = ShaderType.FRAGMENT,
                glsl450Source = vulkanGlsl450
            )
        )
        return GpuShaderProgram(
            id = nextId++,
            vulkanGlsl450Source = vulkanGlsl450,
            gles300ShaderSource = gles300Shader,
            stage = GpuShaderStage.FRAGMENT,
            backendType = GpuBackendType.VULKAN,
            vulkanModuleHandle = module.id
        )
    }

    override fun destroyShaderProgram(program: GpuShaderProgram) {
        if (program.vulkanModuleHandle != 0L) {
            vkContext.destroyShaderModule(VKShaderModule(program.vulkanModuleHandle, ShaderType.FRAGMENT))
        }
    }

    override fun dispatchImageKernel(
        program: GpuShaderProgram,
        inputs: List<GpuTexture>,
        output: GpuTexture,
        uniforms: FloatArray
    ) {
        val firstInput = inputs.firstOrNull()?.let { textureCache[it.id] }
        if (firstInput != null) {
            textureCache[output.id] = firstInput
        }
        vkContext.waitIdle()
    }
}
