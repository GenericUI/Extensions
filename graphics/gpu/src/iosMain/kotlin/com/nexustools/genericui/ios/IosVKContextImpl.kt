package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * iOS MoltenVK (Vulkan-on-Metal) implementation of [com.nexustools.genericui.component.media.VKContext].
 */
class IosVKContextImpl(
    config: VKDeviceAttributes = VKDeviceAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
) : DesktopVKContextImpl(
    config = config,
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    contextInfo = VKContextInfo(
        backend = VKBackendKind.MOLTENVK,
        apiVersion = config.apiVersion,
        deviceName = "Apple iOS GPU (MoltenVK)",
        deviceType = VKDeviceType.INTEGRATED_GPU,
        acceleration = acceleration
    )
)
