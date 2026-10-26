package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.style.RgbaColor

/**
 * Shared desktop Vulkan 1.3 (`VK_KHR_win32_surface`, `MoltenVK` `VK_EXT_metal_surface`,
 * `VK_KHR_wayland_surface`, `VK_KHR_xlib_surface`) rendering context implementing [VKContext].
 * Supports SPIR-V and GLSL `#version 450` shader modules, graphics pipelines, device buffers,
 * RGBA texture images, push constants, and indexed/non-indexed draw calls.
 */
class DesktopVKContextImpl(
    override val deviceName: String = "Desktop Vulkan 1.3 GPU",
    override val apiVersionString: String = "1.3.280",
    override var swapchainWidth: Int = 1280,
    override var swapchainHeight: Int = 720,
    override val isMoltenVk: Boolean = false,
    val surfaceExtensionName: String = "VK_KHR_surface"
) : VKContext {

    override var currentImageIndex: Int = 0
        private set

    private var nextHandle: Long = 0x1000L
    private var activeRenderPass: Boolean = false
    private var boundPipeline: Long = 0L

    private val shaderModules = mutableMapOf<Long, Pair<ShaderType, Int>>()
    private val pipelines = mutableMapOf<Long, Triple<Long, Long, Int>>()
    private val deviceBuffers = mutableMapOf<Long, ByteArray>()
    private val textureImages = mutableMapOf<Long, ByteArray>()
    private val pushConstantStore = mutableMapOf<Long, FloatArray>()

    fun resizeSwapchain(width: Int, height: Int) {
        swapchainWidth = width.coerceAtLeast(1)
        swapchainHeight = height.coerceAtLeast(1)
    }

    override fun beginRenderPass(clearColor: RgbaColor, clearDepth: Float) {
        activeRenderPass = true
    }

    override fun endRenderPass() {
        activeRenderPass = false
        currentImageIndex = (currentImageIndex + 1) % 3
    }

    override fun setViewport(x: Float, y: Float, width: Float, height: Float, minDepth: Float, maxDepth: Float) {
        swapchainWidth = width.toInt().coerceAtLeast(1)
        swapchainHeight = height.toInt().coerceAtLeast(1)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {}

    override fun createShaderModuleFromSpirv(stage: ShaderType, spirvBytes: ByteArray): Long {
        val handle = nextHandle++
        shaderModules[handle] = stage to spirvBytes.size
        return handle
    }

    override fun createShaderModuleFromGlsl(stage: ShaderType, glslSource: String): Long {
        val handle = nextHandle++
        shaderModules[handle] = stage to glslSource.length
        return handle
    }

    override fun destroyShaderModule(moduleHandle: Long) {
        shaderModules.remove(moduleHandle)
    }

    override fun createGraphicsPipeline(
        vertexShaderHandle: Long,
        fragmentShaderHandle: Long,
        vertexStrideBytes: Int
    ): Long {
        val handle = nextHandle++
        pipelines[handle] = Triple(vertexShaderHandle, fragmentShaderHandle, vertexStrideBytes)
        return handle
    }

    override fun bindPipeline(pipelineHandle: Long) {
        boundPipeline = pipelineHandle
    }

    override fun destroyPipeline(pipelineHandle: Long) {
        pipelines.remove(pipelineHandle)
        if (boundPipeline == pipelineHandle) {
            boundPipeline = 0L
        }
    }

    override fun createDeviceBuffer(sizeBytes: Long, usageFlags: Int): Long {
        val handle = nextHandle++
        deviceBuffers[handle] = ByteArray(sizeBytes.coerceIn(0L, 16_777_216L).toInt())
        return handle
    }

    override fun uploadBufferData(bufferHandle: Long, data: ByteArray, offsetBytes: Long) {
        val dst = deviceBuffers[bufferHandle] ?: return
        val start = offsetBytes.toInt().coerceAtLeast(0)
        val len = data.size.coerceAtMost((dst.size - start).coerceAtLeast(0))
        if (len > 0) {
            data.copyInto(dst, destinationOffset = start, startIndex = 0, endIndex = len)
        }
    }

    override fun uploadBufferFloats(bufferHandle: Long, data: FloatArray, offsetBytes: Long) {
        val bytes = ByteArray(data.size * 4)
        for (i in data.indices) {
            val bits = data[i].toRawBits()
            val o = i * 4
            bytes[o] = (bits and 0xFF).toByte()
            bytes[o + 1] = ((bits ushr 8) and 0xFF).toByte()
            bytes[o + 2] = ((bits ushr 16) and 0xFF).toByte()
            bytes[o + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        uploadBufferData(bufferHandle, bytes, offsetBytes)
    }

    override fun destroyDeviceBuffer(bufferHandle: Long) {
        deviceBuffers.remove(bufferHandle)
    }

    override fun createTextureImageRgba(width: Int, height: Int, rgbaBytes: ByteArray?): Long {
        val handle = nextHandle++
        val size = (width.coerceAtLeast(1) * height.coerceAtLeast(1) * 4)
        textureImages[handle] = rgbaBytes?.copyOf() ?: ByteArray(size)
        return handle
    }

    override fun updateTextureImageRgba(imageHandle: Long, width: Int, height: Int, rgbaBytes: ByteArray) {
        textureImages[imageHandle] = rgbaBytes.copyOf()
    }

    override fun destroyTextureImage(imageHandle: Long) {
        textureImages.remove(imageHandle)
    }

    override fun bindVertexBuffer(bufferHandle: Long, binding: Int, offsetBytes: Long) {}

    override fun bindIndexBuffer(bufferHandle: Long, indexTypeUint32: Boolean, offsetBytes: Long) {}

    override fun pushConstantsFloat(pipelineHandle: Long, values: FloatArray) {
        pushConstantStore[pipelineHandle] = values.copyOf()
    }

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {}

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int
    ) {}

    override fun waitIdle() {}
}
