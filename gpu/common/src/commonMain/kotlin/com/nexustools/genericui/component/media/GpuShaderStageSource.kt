package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.ShaderType

data class GpuShaderStageSource(
    val stage: ShaderType,
    val glsl450Source: String = "",
    val glslEs300Source: String = "",
    val wgslSource: String = "",
    val spirvBytes: ByteArray = ByteArray(0)
)
