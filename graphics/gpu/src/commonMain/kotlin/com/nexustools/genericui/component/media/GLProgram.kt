package com.nexustools.genericui.component.media

data class GLProgram(
    val id: Int,
    val linkStatus: GLCompileStatus = GLCompileStatus(true)
)
