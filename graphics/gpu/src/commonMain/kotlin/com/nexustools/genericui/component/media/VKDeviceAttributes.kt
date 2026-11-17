package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.gpu.GPUPowerPreference

data class VKDeviceAttributes(
    val apiVersion: VKApiVersion = VKApiVersion.V1_2,
    val powerPreference: GPUPowerPreference = GPUPowerPreference.HIGH_PERFORMANCE,
    val presentMode: VKPresentMode = VKPresentMode.FIFO,
    val enableValidation: Boolean = false,
    val hdrIfAvailable: Boolean = false
)
