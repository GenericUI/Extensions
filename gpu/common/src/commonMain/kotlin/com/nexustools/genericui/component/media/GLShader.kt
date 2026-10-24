package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.ShaderType

/**
 * Opaque handle to a compiled OpenGL ES shader object created via [GLContext.createShader].
 */
data class GLShader(
    val id: Int,
    val type: ShaderType
)
