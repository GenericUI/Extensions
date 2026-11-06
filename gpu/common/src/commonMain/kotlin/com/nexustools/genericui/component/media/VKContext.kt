package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType

/**
 * Typed WebGPU / Vulkan rendering context interface.
 */
interface VKContext : GpuRenderContext {
    override val backendType: GpuBackendType
        get() = GpuBackendType.VULKAN
    val contextInfo: VKContextInfo
    override val acceleration: GpuAcceleration
        get() = contextInfo.acceleration
    val swapchainWidth: Int
    val swapchainHeight: Int
    val currentImageIndex: Int

    fun beginRenderPass(descriptor: VKRenderPassDescriptor = VKRenderPassDescriptor())
    fun endRenderPass()

    fun setViewport(x: Float, y: Float, width: Float, height: Float, minDepth: Float = 0f, maxDepth: Float = 1f)
    fun setScissor(x: Int, y: Int, width: Int, height: Int)

    fun createShaderModule(source: GpuShaderStageSource): VKShaderModule
    fun destroyShaderModule(module: VKShaderModule)

    fun createRenderPipeline(descriptor: VKRenderPipelineDescriptor): VKPipeline
    fun bindPipeline(pipeline: VKPipeline)
    fun destroyPipeline(pipeline: VKPipeline)

    fun createBuffer(descriptor: VKBufferDescriptor): VKBuffer
    fun writeBuffer(buffer: VKBuffer, data: ByteArray, offsetBytes: Long = 0L)
    fun writeBuffer(buffer: VKBuffer, data: FloatArray, offsetBytes: Long = 0L)
    fun destroyBuffer(buffer: VKBuffer)

    fun createTexture(descriptor: VKTextureDescriptor, initialRgbaBytes: ByteArray? = null): VKTexture
    fun writeTexture(texture: VKTexture, width: Int, height: Int, rgbaBytes: ByteArray)
    fun destroyTexture(texture: VKTexture)

    fun createSampler(descriptor: VKSamplerDescriptor = VKSamplerDescriptor()): VKSampler
    fun destroySampler(sampler: VKSampler)

    fun createRenderTarget(width: Int, height: Int, format: VKTextureFormat = VKTextureFormat.RGBA8_UNORM): VKRenderTarget
    fun destroyRenderTarget(target: VKRenderTarget)

    fun createBindGroup(descriptor: VKBindGroupDescriptor): VKBindGroup
    fun setBindGroup(index: Int, bindGroup: VKBindGroup)
    fun destroyBindGroup(bindGroup: VKBindGroup)

    fun bindVertexBuffer(buffer: VKBuffer, binding: Int = 0, offsetBytes: Long = 0L)
    fun bindIndexBuffer(buffer: VKBuffer, indexType: GLIndexType = GLIndexType.UNSIGNED_INT, offsetBytes: Long = 0L)
    fun pushConstants(pipeline: VKPipeline, values: FloatArray)

    fun draw(vertexCount: Int, instanceCount: Int = 1, firstVertex: Int = 0, firstInstance: Int = 0)
    fun drawIndexed(indexCount: Int, instanceCount: Int = 1, firstIndex: Int = 0, vertexOffset: Int = 0, firstInstance: Int = 0)

    fun readPixels(x: Int, y: Int, width: Int, height: Int): ByteArray
    fun waitIdle()
}
