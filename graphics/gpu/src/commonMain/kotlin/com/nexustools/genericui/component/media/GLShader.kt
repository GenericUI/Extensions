package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.ShaderType

data class GLShader(
    val id: Int,
    val type: ShaderType,
    val compileStatus: GLCompileStatus = GLCompileStatus(true)
)
