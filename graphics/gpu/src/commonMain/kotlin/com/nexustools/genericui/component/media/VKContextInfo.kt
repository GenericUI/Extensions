package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.gpu.GpuAcceleration

data class VKContextInfo(
    val backend: VKBackendKind,
    val apiVersion: VKApiVersion = VKApiVersion.V1_2,
    val deviceName: String = "",
    val deviceType: VKDeviceType = VKDeviceType.DISCRETE_GPU,
    val acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
)
