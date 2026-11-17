package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType

sealed interface GpuRenderContext {
    val backendType: GpuBackendType
    val acceleration: GpuAcceleration
}
