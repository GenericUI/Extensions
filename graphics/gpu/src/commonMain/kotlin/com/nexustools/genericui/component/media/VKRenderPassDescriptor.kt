package com.nexustools.genericui.component.media

import com.nexustools.genericui.style.color.RgbaColor

data class VKRenderPassDescriptor(
    val target: VKRenderTarget? = null,
    val loadOp: VKLoadOp = VKLoadOp.CLEAR,
    val storeOp: VKStoreOp = VKStoreOp.STORE,
    val clearColor: RgbaColor = RgbaColor.BLACK,
    val clearDepth: Float = 1f
)
