package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl

/**
 * Web `<canvas>` WebGPU / Vulkan implementation ([VKCanvasNativeImpl]) with `requestAnimationFrame`
 * VBlank loop and high-resolution `deltaMs` tracking.
 */
class WebVKCanvasImpl : WebAbstractCanvasImpl<VKContext>("canvas-vk"), VKCanvasNativeImpl<WebDomElement> {
    private val vkContext = WebVKContextImpl()
    private var onSurfaceCreatedListener: ((VKContext, Int, Int) -> Unit)? = null
    private var onSurfaceResizedListener: ((VKContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): VKContext {
        vkContext.swapchainWidth = getSurfaceWidth()
        vkContext.swapchainHeight = getSurfaceHeight()
        return vkContext
    }

    override fun setOnSurfaceCreated(listener: ((VKContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(getRenderingContext(), getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((VKContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }
}
