package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl

/**
 * iOS `MTKView` (`CAMetalLayer` + Khronos `MoltenVK` Vulkan 1.2) + `CADisplayLink` implementation of [VKCanvasNativeImpl].
 */
class IosVKCanvasImpl : IosAbstractCanvasImpl<VKContext>("MTKView", "canvas-vk"), VKCanvasNativeImpl<IosNativeView> {
    private val vkContext = IosVKContextImpl()
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
