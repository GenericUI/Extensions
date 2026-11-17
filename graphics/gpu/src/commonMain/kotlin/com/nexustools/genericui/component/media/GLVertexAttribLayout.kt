package com.nexustools.genericui.component.media

data class GLVertexAttribLayout(
    val index: Int,
    val size: Int,
    val type: GLDataType = GLDataType.FLOAT,
    val normalized: Boolean = false,
    val stride: Int = 0,
    val offset: Int = 0
)
