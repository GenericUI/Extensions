package com.nexustools.genericui.component.media

data class VKBuffer(
    val id: Long,
    val sizeBytes: Long,
    val usage: Set<VKBufferUsage>
)
