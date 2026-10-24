package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl

/**
 * Android `android.view.SurfaceView` + NDK `VK_KHR_android_surface` implementation of [VKCanvasNativeImpl].
 */
class AndroidVKCanvasImpl : AndroidAbstractCanvasImpl<VKContext>("android.view.SurfaceView"), VKCanvasNativeImpl<AndroidViewHandle> {
    private val vkContext = AndroidVKContextImpl()
    private var onSurfaceCreatedListener: ((VKContext, Int, Int) -> Unit)? = null
    private var onSurfaceResizedListener: ((VKContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): VKContext = vkContext

    override fun setOnSurfaceCreated(listener: ((VKContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(vkContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((VKContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
        listener?.invoke(vkContext, getSurfaceWidth(), getSurfaceHeight())
    }
}
