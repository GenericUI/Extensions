package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl

/**
 * Dedicated OpenGL ES 2.0 / 3.0 / 3.2 canvas extending [AbstractCanvas].
 *
 * On Windows, [GLCanvas] uses Google ANGLE (`libEGL.dll` + `libGLESv2.dll`) so OpenGL ES shaders
 * and draw calls run with hardware acceleration over Direct3D 11 / Vulkan.
 */
open class GLCanvas(
    val config: GLCanvasConfig = GLCanvasConfig(),
    width: Int = 800,
    height: Int = 600
) : AbstractCanvas<GLContext>() {
    var onSurfaceCreated: ((GLContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<GLCanvasNativeImpl<*>>()?.setOnSurfaceCreated(value)
        }

    var onSurfaceResized: ((GLContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<GLCanvasNativeImpl<*>>()?.setOnSurfaceResized(value)
        }

    init {
        this.widthPx = width
        this.heightPx = height
    }

    override fun createNativeImpl(factory: NativeFactory): GLCanvasNativeImpl<*> =
        Canvas3DExtension.createGLCanvasNative(
            config = config,
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<GLCanvasNativeImpl<*>>() ?: return
        impl.setOnSurfaceCreated(onSurfaceCreated)
        impl.setOnSurfaceResized(onSurfaceResized)
    }
}
