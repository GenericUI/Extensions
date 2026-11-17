package com.nexustools.genericui.component.media

data class VKSamplerDescriptor(
    val minFilter: VKFilter = VKFilter.LINEAR,
    val magFilter: VKFilter = VKFilter.LINEAR,
    val addressModeU: VKAddressMode = VKAddressMode.REPEAT,
    val addressModeV: VKAddressMode = VKAddressMode.REPEAT
)
