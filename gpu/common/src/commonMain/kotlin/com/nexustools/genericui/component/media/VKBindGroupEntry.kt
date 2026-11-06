package com.nexustools.genericui.component.media

data class VKBindGroupEntry(
    val binding: Int,
    val buffer: VKBuffer? = null,
    val texture: VKTexture? = null,
    val sampler: VKSampler? = null
)
