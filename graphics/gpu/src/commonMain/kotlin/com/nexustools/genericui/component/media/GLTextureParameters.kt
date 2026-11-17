package com.nexustools.genericui.component.media

data class GLTextureParameters(
    val minFilter: GLTextureFilter = GLTextureFilter.LINEAR,
    val magFilter: GLTextureFilter = GLTextureFilter.LINEAR,
    val wrapS: GLTextureWrap = GLTextureWrap.REPEAT,
    val wrapT: GLTextureWrap = GLTextureWrap.REPEAT
)
