package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl

/**
 * Dedicated Vulkan 1.1 / 1.2 / 1.3 canvas extending [AbstractCanvas].
 */
open class VKCanvas(
    val config: VKCanvasConfig = VKCanvasConfig(),
    width: Int = 800,
    height: Int = 600
) : AbstractCanvas<VKContext>() {
    var onSurfaceCreated: ((VKContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<VKCanvasNativeImpl<*>>()?.setOnSurfaceCreated(value)
        }

    var onSurfaceResized: ((VKContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<VKCanvasNativeImpl<*>>()?.setOnSurfaceResized(value)
        }

    init {
        this.widthPx = width
        this.heightPx = height
    }

    override fun createNativeImpl(factory: NativeFactory): VKCanvasNativeImpl<*> =
        Canvas3DExtension.createVKCanvasNative(
            config = config,
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<VKCanvasNativeImpl<*>>() ?: return
        impl.setOnSurfaceCreated(onSurfaceCreated)
        impl.setOnSurfaceResized(onSurfaceResized)
    }
}
