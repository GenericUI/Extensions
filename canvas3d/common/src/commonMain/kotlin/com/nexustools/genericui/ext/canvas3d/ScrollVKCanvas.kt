package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.VBlankRenderCallback
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.image.ImageBuffer

/**
 * Type-safe scrollable Vulkan 1.2+ / MoltenVK / WebGPU 3D canvas ([ScrollCanvas3D] with `preferVulkan = true`).
 *
 * The underlying [vkCanvas] (`VKCanvasExt`) only allocates a swapchain for the visible viewport
 * (`viewportWidth` x `viewportHeight`), while scrolling over `contentWidth` x `contentHeight` is
 * simulated via [onScroll] / [ScrollCanvas3DListener].
 */
open class ScrollVKCanvas(
    config: VKCanvasConfig = VKCanvasConfig(),
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight
) : ScrollCanvas3D(
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    contentWidth = contentWidth,
    contentHeight = contentHeight,
    preferVulkan = true,
    vkConfig = config
) {
    val config: VKCanvasConfig
        get() = vkConfig

    val vkCanvas: VKCanvasExt
        get() = viewportVkCanvas

    fun getContext(): VKContext? = resolveVKContext()

    fun uploadImageBufferTexture(imageBuffer: ImageBuffer): Long =
        viewportVkCanvas.uploadImageBufferTexture(imageBuffer)

    fun registerVBlankRender(callback: VBlankRenderCallback<VKContext>): VBlankSubscription =
        registerVKVBlankRender(callback)

    fun dispatchVBlankFrame(explicitTimestampMs: Double? = null) {
        viewportVkCanvas.dispatchVBlankFrame(explicitTimestampMs)
    }
}
