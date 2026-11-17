package com.nexustools.genericui.component.media

/**
 * Opaque handle to an OpenGL ES texture object created via [GLContext.createTexture].
 */
data class GLTexture(
    val id: Int,
    val width: Int = 0,
    val height: Int = 0
)
