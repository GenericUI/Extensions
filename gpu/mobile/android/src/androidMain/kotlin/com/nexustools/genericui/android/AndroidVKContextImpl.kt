package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * Android Vulkan implementation of [com.nexustools.genericui.component.media.VKContext].
 */
class AndroidVKContextImpl(
    config: VKDeviceAttributes = VKDeviceAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
) : DesktopVKContextImpl(
    config = config,
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    contextInfo = VKContextInfo(
        backend = VKBackendKind.VULKAN_NATIVE,
        apiVersion = config.apiVersion,
        deviceName = "Android Vulkan GPU",
        deviceType = VKDeviceType.INTEGRATED_GPU,
        acceleration = acceleration
    )
)
