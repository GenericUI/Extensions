package com.nexustools.genericui.component.media

data class GLCompileStatus(
    val success: Boolean,
    val infoLog: String = ""
)
