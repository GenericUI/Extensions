package com.nexustools.genericui.component.media

/**
 * Opaque handle to an OpenGL ES uniform location queried via [GLContext.getUniformLocation].
 */
data class GLUniformLocation(
    val id: Int,
    val name: String = ""
)
