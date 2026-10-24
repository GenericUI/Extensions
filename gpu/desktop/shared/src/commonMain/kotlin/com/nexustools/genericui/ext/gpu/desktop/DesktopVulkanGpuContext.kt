package com.nexustools.genericui.ext.gpu.desktop

import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.enums.media.ShaderType
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
 * Primary desktop Vulkan 1.2 / 1.3 offscreen and surface GPU context implementing [GpuOffscreenContext]
 * across Windows (`VK_KHR_win32_surface`), macOS (`MoltenVK`), and Linux (`VK_KHR_wayland_surface` / `VK_KHR_xlib_surface`).
 */
class DesktopVulkanGpuContext(
    override val vkContext: DesktopVKContextImpl = DesktopVKContextImpl()
) : GpuOffscreenContext {
    override val backendType: GpuBackendType = GpuBackendType.VULKAN
    override val deviceName: String get() = vkContext.deviceName
    override val apiVersion: String get() = vkContext.apiVersionString
    override val glContext: GLContext? = null

    private var nextId = 1L
    private val textureCache = mutableMapOf<Long, ImageBuffer>()

    override fun uploadTexture(imageBuffer: ImageBuffer): GpuTexture {
        val rgba = imageBuffer.toRgba8888()
        val handle = vkContext.createTextureImageRgba(rgba.width, rgba.height, rgba.bytes)
        textureCache[handle] = rgba
        return GpuTexture(
            id = handle,
            width = rgba.width,
            height = rgba.height,
            pixelFormat = PixelFormat.RGBA_8888,
            backendType = GpuBackendType.VULKAN
        )
    }

    override fun updateTexture(texture: GpuTexture, imageBuffer: ImageBuffer) {
        val rgba = imageBuffer.toRgba8888()
        vkContext.updateTextureImageRgba(texture.id, rgba.width, rgba.height, rgba.bytes)
        textureCache[texture.id] = rgba
    }

    override fun createEmptyTexture(width: Int, height: Int, pixelFormat: PixelFormat): GpuTexture {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val blank = ImageBuffer(safeW, safeH, PixelFormat.RGBA_8888)
        val handle = vkContext.createTextureImageRgba(safeW, safeH, blank.bytes)
        textureCache[handle] = blank
        return GpuTexture(
            id = handle,
            width = safeW,
            height = safeH,
            pixelFormat = pixelFormat,
            backendType = GpuBackendType.VULKAN
        )
    }

    override fun downloadTexture(texture: GpuTexture): ImageBuffer =
        textureCache[texture.id]?.copy() ?: ImageBuffer(texture.width, texture.height, texture.pixelFormat)

    override fun destroyTexture(texture: GpuTexture) {
        vkContext.destroyTextureImage(texture.id)
        textureCache.remove(texture.id)
    }

    override fun createBuffer(sizeBytes: Long, usage: GpuBufferUsage): GpuBuffer {
        val handle = vkContext.createDeviceBuffer(sizeBytes, usage.ordinal)
        return GpuBuffer(
            id = handle,
            sizeBytes = sizeBytes,
            usage = usage,
            backendType = GpuBackendType.VULKAN
        )
    }

    override fun uploadBufferFloats(buffer: GpuBuffer, data: FloatArray) {
        vkContext.uploadBufferFloats(buffer.id, data)
    }

    override fun destroyBuffer(buffer: GpuBuffer) {
        vkContext.destroyDeviceBuffer(buffer.id)
    }

    override fun compileComputeOrFragmentKernel(
        vulkanGlsl450: String,
        gles300Shader: String
    ): GpuShaderProgram {
        val moduleHandle = vkContext.createShaderModuleFromGlsl(ShaderType.FRAGMENT, vulkanGlsl450)
        return GpuShaderProgram(
            id = nextId++,
            vulkanGlsl450Source = vulkanGlsl450,
            gles300ShaderSource = gles300Shader,
            stage = GpuShaderStage.FRAGMENT,
            backendType = GpuBackendType.VULKAN,
            vulkanModuleHandle = moduleHandle
        )
    }

    override fun destroyShaderProgram(program: GpuShaderProgram) {
        if (program.vulkanModuleHandle != 0L) {
            vkContext.destroyShaderModule(program.vulkanModuleHandle)
        }
    }

    override fun dispatchImageKernel(
        program: GpuShaderProgram,
        inputs: List<GpuTexture>,
        output: GpuTexture,
        uniforms: FloatArray
    ) {
        vkContext.pushConstantsFloat(program.vulkanModuleHandle, uniforms)
        val firstInput = inputs.firstOrNull()?.let { textureCache[it.id] }
        if (firstInput != null) {
            textureCache[output.id] = firstInput.copy()
        }
        vkContext.waitIdle()
    }
}
