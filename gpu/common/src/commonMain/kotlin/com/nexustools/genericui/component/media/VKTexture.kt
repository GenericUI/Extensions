package com.nexustools.genericui.component.media

data class VKTexture(
    val id: Long,
    val width: Int,
    val height: Int,
    val format: VKTextureFormat = VKTextureFormat.RGBA8_UNORM
)
