package com.nexustools.genericui.component.media

data class VKBlendState(
    val enabled: Boolean = false,
    val srcColorFactor: GLBlendFactor = GLBlendFactor.SRC_ALPHA,
    val dstColorFactor: GLBlendFactor = GLBlendFactor.ONE_MINUS_SRC_ALPHA,
    val srcAlphaFactor: GLBlendFactor = GLBlendFactor.ONE,
    val dstAlphaFactor: GLBlendFactor = GLBlendFactor.ONE_MINUS_SRC_ALPHA
)
