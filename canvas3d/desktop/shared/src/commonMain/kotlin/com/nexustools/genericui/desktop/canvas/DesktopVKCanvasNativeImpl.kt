package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl

/**
 * Shared desktop base implementation of [VKCanvasNativeImpl] backed by [DesktopVKContextImpl]
 * (`VK_KHR_win32_surface` on `:windows`, `MoltenVK` `VK_EXT_metal_surface` on `:macos`,
 * `VK_KHR_wayland_surface`/`VK_KHR_xlib_surface` on `:gtk`, and `QVulkanWindow` on `:qt`).
 */
open class DesktopVKCanvasNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    deviceName: String = "Desktop Vulkan 1.3 GPU",
    isMoltenVk: Boolean = false,
    surfaceExtensionName: String = "VK_KHR_surface"
) : DesktopBaseNativeImpl<W>(nativeWidget, "vk-canvas"), VKCanvasNativeImpl<W> {

    protected val vkContext: DesktopVKContextImpl = DesktopVKContextImpl(
        deviceName = deviceName,
        isMoltenVk = isMoltenVk,
        surfaceExtensionName = surfaceExtensionName
    )
    protected var vblankActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var onSurfaceCreatedListener: ((VKContext, Int, Int) -> Unit)? = null
    protected var onSurfaceResizedListener: ((VKContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): VKContext = vkContext

    override fun setOnSurfaceCreated(listener: ((VKContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(vkContext, vkContext.swapchainWidth, vkContext.swapchainHeight)
    }

    override fun setOnSurfaceResized(listener: ((VKContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        vblankCallback = if (active) onVBlankFrame else null
    }

    override fun requestSingleRender() {
        vblankCallback?.invoke(null)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        nativeWidget.attributes["pointerEventsPassThrough"] = passThrough
    }

    override fun getSurfaceWidth(): Int = vkContext.swapchainWidth

    override fun getSurfaceHeight(): Int = vkContext.swapchainHeight

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        ByteArray(getSurfaceWidth().coerceAtLeast(1) * getSurfaceHeight().coerceAtLeast(1) * 4)

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(getSurfaceWidth(), getSurfaceHeight(), PixelFormat.RGBA_8888, bytes = rgba)
        return LibJpegCodec.encode(buf, quality)
    }
}
