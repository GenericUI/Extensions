package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.style.RgbaColor

/**
 * Cross-platform Vulkan 1.1 / 1.2 / 1.3 rendering context exposed by [VKCanvas].
 *
 * Maps to:
 * - Native `VkInstance`, `VkPhysicalDevice`, `VkDevice`, `VkQueue`, `VkSwapchainKHR`, and `VkCommandBuffer`
 *   on `:windows` (`VK_KHR_win32_surface`), `:gtk`/`qt` (`VK_KHR_wayland_surface` / `VK_KHR_xlib_surface`),
 *   `:android` (`VK_KHR_android_surface`), and `:swing` (via optional LWJGL `lwjgl-vulkan`).
 * - `MoltenVK` (`VK_EXT_metal_surface`) on `:macos` and `:ios`.
 * - `WebGPU` (`GPUDevice` / `GPUCommandEncoder`) on `:web`.
 */
interface VKContext {
    val deviceName: String
    val apiVersionString: String
    val swapchainWidth: Int
    val swapchainHeight: Int
    val currentImageIndex: Int
    val isMoltenVk: Boolean

    fun beginRenderPass(clearColor: RgbaColor = RgbaColor.BLACK, clearDepth: Float = 1f)
    fun endRenderPass()

    fun setViewport(x: Float, y: Float, width: Float, height: Float, minDepth: Float = 0f, maxDepth: Float = 1f)
    fun setScissor(x: Int, y: Int, width: Int, height: Int)

    fun createShaderModuleFromSpirv(stage: ShaderType, spirvBytes: ByteArray): Long
    fun createShaderModuleFromGlsl(stage: ShaderType, glslSource: String): Long
    fun destroyShaderModule(moduleHandle: Long)

    fun createGraphicsPipeline(vertexShaderHandle: Long, fragmentShaderHandle: Long, vertexStrideBytes: Int): Long
    fun bindPipeline(pipelineHandle: Long)
    fun destroyPipeline(pipelineHandle: Long)

    fun createDeviceBuffer(sizeBytes: Long, usageFlags: Int): Long
    fun uploadBufferData(bufferHandle: Long, data: ByteArray, offsetBytes: Long = 0L)
    fun uploadBufferFloats(bufferHandle: Long, data: FloatArray, offsetBytes: Long = 0L)
    fun destroyDeviceBuffer(bufferHandle: Long)

    fun createTextureImageRgba(width: Int, height: Int, rgbaBytes: ByteArray?): Long
    fun updateTextureImageRgba(imageHandle: Long, width: Int, height: Int, rgbaBytes: ByteArray)
    fun destroyTextureImage(imageHandle: Long)

    fun bindVertexBuffer(bufferHandle: Long, binding: Int = 0, offsetBytes: Long = 0L)
    fun bindIndexBuffer(bufferHandle: Long, indexTypeUint32: Boolean = true, offsetBytes: Long = 0L)
    fun pushConstantsFloat(pipelineHandle: Long, values: FloatArray)

    fun draw(vertexCount: Int, instanceCount: Int = 1, firstVertex: Int = 0, firstInstance: Int = 0)
    fun drawIndexed(indexCount: Int, instanceCount: Int = 1, firstIndex: Int = 0, vertexOffset: Int = 0, firstInstance: Int = 0)

    fun waitIdle()
}
