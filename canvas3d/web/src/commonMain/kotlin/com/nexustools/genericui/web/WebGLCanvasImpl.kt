package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl

/**
 * Web `<canvas>` WebGL2 / OpenGL ES 3.0 implementation ([GLCanvasNativeImpl]) with `requestAnimationFrame`
 * VBlank loop and high-resolution `deltaMs` tracking.
 */
class WebGLCanvasImpl : WebAbstractCanvasImpl<GLContext>("canvas-gl"), GLCanvasNativeImpl<WebDomElement> {
    private val glContext = WebGLContextImpl()
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
