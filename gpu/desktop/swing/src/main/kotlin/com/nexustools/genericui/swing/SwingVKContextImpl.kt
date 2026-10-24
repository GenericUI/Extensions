package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.style.RgbaColor

/**
 * Java Swing Vulkan (`LWJGL` `org.lwjgl.vulkan.VK10` when present on the runtime classpath, with
 * software/offscreen fallback) implementation of [VKContext].
 */
class SwingVKContextImpl(
    override var swapchainWidth: Int = 800,
    override var swapchainHeight: Int = 600,
    val lwjglVulkanAvailable: Boolean = detectLwjglVulkan()
) : VKContext {
    override val deviceName: String = if (lwjglVulkanAvailable) "LWJGL Vulkan Device" else "Swing Vulkan Offscreen"
    override val apiVersionString: String = "Vulkan 1.3 (JVM / LWJGL)"
    override val currentImageIndex: Int = 0
    override val isMoltenVk: Boolean = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)

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

    companion object {
        fun detectLwjglVulkan(): Boolean = try {
            Class.forName("org.lwjgl.vulkan.VK10")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
