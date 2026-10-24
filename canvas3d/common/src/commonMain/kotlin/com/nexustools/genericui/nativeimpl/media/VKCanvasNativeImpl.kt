package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.VKContext

/**
 * Native implementation contract for [com.nexustools.genericui.component.media.VKCanvas] (Vulkan 1.1 / 1.2 / 1.3).
 */
interface VKCanvasNativeImpl<out W> : AbstractCanvasNativeImpl<W, VKContext> {
    fun setOnSurfaceCreated(listener: ((VKContext, Int, Int) -> Unit)?)
    fun setOnSurfaceResized(listener: ((VKContext, Int, Int) -> Unit)?)
}
