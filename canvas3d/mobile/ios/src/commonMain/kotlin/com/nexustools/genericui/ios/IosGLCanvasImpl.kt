package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl

/**
 * iOS `GLKView` (`EAGLContext` OpenGL ES 3.0) + `CADisplayLink` implementation of [GLCanvasNativeImpl].
 */
class IosGLCanvasImpl : IosAbstractCanvasImpl<GLContext>("GLKView", "canvas-gl"), GLCanvasNativeImpl<IosNativeView> {
    private val glContext = IosGLContextImpl()
    private var onSurfaceCreatedListener: ((GLContext, Int, Int) -> Unit)? = null
    private var onSurfaceResizedListener: ((GLContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): GLContext = glContext

    override fun setOnSurfaceCreated(listener: ((GLContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(glContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GLContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }
}
