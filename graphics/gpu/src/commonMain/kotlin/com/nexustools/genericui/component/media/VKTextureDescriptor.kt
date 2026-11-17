package com.nexustools.genericui.component.media

data class VKTextureDescriptor(
    val width: Int,
    val height: Int,
    val format: VKTextureFormat = VKTextureFormat.RGBA8_UNORM,
    val usage: Set<VKTextureUsage> = setOf(VKTextureUsage.SAMPLED, VKTextureUsage.COPY_DST)
)
