package com.nexustools.genericui.component.media

data class VKRenderPipelineDescriptor(
    val vertex: VKShaderModule,
    val fragment: VKShaderModule,
    val vertexLayout: VKVertexLayout,
    val topology: VKPrimitiveTopology = VKPrimitiveTopology.TRIANGLE_LIST,
    val blend: VKBlendState = VKBlendState(),
    val colorFormat: VKTextureFormat = VKTextureFormat.RGBA8_UNORM,
    val bindGroupLayout: List<VKBindingLayout> = emptyList()
)
