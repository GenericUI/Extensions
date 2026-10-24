package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing OpenGL ES / ANGLE (`LWJGL` runtime-detected via [SwingGLContextImpl]) implementation of [GLCanvasNativeImpl].
 */
class SwingGLCanvasImpl : SwingAbstractCanvasImpl<GLContext>(JPanel()), GLCanvasNativeImpl<JComponent> {
    private val glContext = SwingGLContextImpl(800, 600)
    private var createdListener: ((GLContext, Int, Int) -> Unit)? = null
    private var resizedListener: ((GLContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): GLContext = glContext

    override fun setOnSurfaceCreated(listener: ((GLContext, Int, Int) -> Unit)?) {
        createdListener = listener
        listener?.invoke(glContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GLContext, Int, Int) -> Unit)?) {
        resizedListener = listener
    }
}
