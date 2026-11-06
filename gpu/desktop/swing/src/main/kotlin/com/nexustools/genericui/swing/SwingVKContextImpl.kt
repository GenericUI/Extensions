package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * Swing/JVM implementation of [com.nexustools.genericui.component.media.VKContext] backed by
 * LWJGL 3 Vulkan (`org.lwjgl.vulkan`) when available on the classpath and the GenericUI Vulkan pipeline.
 */
class SwingVKContextImpl(
    config: VKDeviceAttributes = VKDeviceAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = if (isLwjglVulkanAvailable()) GpuAcceleration.HARDWARE else GpuAcceleration.SOFTWARE
) : DesktopVKContextImpl(
    config = config,
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    contextInfo = VKContextInfo(
        backend = VKBackendKind.LWJGL_VULKAN,
        apiVersion = config.apiVersion,
        deviceName = if (acceleration == GpuAcceleration.HARDWARE) "LWJGL Vulkan Device" else "GenericUI Software Vulkan Device",
        deviceType = if (acceleration == GpuAcceleration.HARDWARE) VKDeviceType.DISCRETE_GPU else VKDeviceType.CPU,
        acceleration = acceleration
    )
) {
    val lwjglVulkanAvailable: Boolean = isLwjglVulkanAvailable()

    companion object {
        fun isLwjglVulkanAvailable(): Boolean = try {
            Class.forName("org.lwjgl.vulkan.VK10")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
