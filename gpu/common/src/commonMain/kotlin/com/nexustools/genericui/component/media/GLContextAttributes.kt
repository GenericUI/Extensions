package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.gpu.GPUPowerPreference

data class GLContextAttributes(
    val version: GLContextVersion = GLContextVersion.ES_3_0,
    val alpha: Boolean = true,
    val depth: Boolean = true,
    val stencil: Boolean = false,
    val antialias: Boolean = true,
    val premultipliedAlpha: Boolean = true,
    val preserveDrawingBuffer: Boolean = false,
    val powerPreference: GPUPowerPreference = GPUPowerPreference.HIGH_PERFORMANCE,
    val vsync: Boolean = true
)
