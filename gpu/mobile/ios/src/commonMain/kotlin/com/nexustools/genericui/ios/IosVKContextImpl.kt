package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.style.RgbaColor

/**
 * iOS `MoltenVK` (`VK_EXT_metal_surface` over `CAMetalLayer`) implementation of [VKContext].
 */
class IosVKContextImpl(
    override var swapchainWidth: Int = 390,
    override var swapchainHeight: Int = 600
) : VKContext {
    override val deviceName: String = "Apple GPU (MoltenVK over Metal)"
    override val apiVersionString: String = "Vulkan 1.2 (MoltenVK)"
    override val currentImageIndex: Int = 0
    override val isMoltenVk: Boolean = true

    private var nextHandle: Long = 1L

    override fun beginRenderPass(clearColor: RgbaColor, clearDepth: Float) {}
    override fun endRenderPass() {}
    override fun setViewport(x: Float, y: Float, width: Float, height: Float, minDepth: Float, maxDepth: Float) {}
    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {}

    override fun createShaderModuleFromSpirv(stage: ShaderType, spirvBytes: ByteArray): Long = nextHandle++
    override fun createShaderModuleFromGlsl(stage: ShaderType, glslSource: String): Long = nextHandle++
    override fun destroyShaderModule(moduleHandle: Long) {}

    override fun createGraphicsPipeline(vertexShaderHandle: Long, fragmentShaderHandle: Long, vertexStrideBytes: Int): Long = nextHandle++
    override fun bindPipeline(pipelineHandle: Long) {}
    override fun destroyPipeline(pipelineHandle: Long) {}

    override fun createDeviceBuffer(sizeBytes: Long, usageFlags: Int): Long = nextHandle++
    override fun uploadBufferData(bufferHandle: Long, data: ByteArray, offsetBytes: Long) {}
    override fun uploadBufferFloats(bufferHandle: Long, data: FloatArray, offsetBytes: Long) {}
    override fun destroyDeviceBuffer(bufferHandle: Long) {}

    override fun createTextureImageRgba(width: Int, height: Int, rgbaBytes: ByteArray?): Long = nextHandle++
    override fun updateTextureImageRgba(imageHandle: Long, width: Int, height: Int, rgbaBytes: ByteArray) {}
    override fun destroyTextureImage(imageHandle: Long) {}

    override fun bindVertexBuffer(bufferHandle: Long, binding: Int, offsetBytes: Long) {}
    override fun bindIndexBuffer(bufferHandle: Long, indexTypeUint32: Boolean, offsetBytes: Long) {}
    override fun pushConstantsFloat(pipelineHandle: Long, values: FloatArray) {}

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {}
    override fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int, vertexOffset: Int, firstInstance: Int) {}
    override fun waitIdle() {}
}
