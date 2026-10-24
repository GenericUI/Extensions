package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl

/**
 * Android `android.opengl.GLSurfaceView` / `TextureView` + `EGL14` implementation of [GLCanvasNativeImpl].
 */
class AndroidGLCanvasImpl : AndroidAbstractCanvasImpl<GLContext>("android.opengl.GLSurfaceView"), GLCanvasNativeImpl<AndroidViewHandle> {
    private val glContext = AndroidGLContextImpl()
    private var onSurfaceCreatedListener: ((GLContext, Int, Int) -> Unit)? = null
    private var onSurfaceResizedListener: ((GLContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): GLContext = glContext

    override fun setOnSurfaceCreated(listener: ((GLContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(glContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GLContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
        listener?.invoke(glContext, getSurfaceWidth(), getSurfaceHeight())
    }
}
