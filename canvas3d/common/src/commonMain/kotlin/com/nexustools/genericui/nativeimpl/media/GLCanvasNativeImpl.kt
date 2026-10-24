package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.GLContext

/**
 * Native implementation contract for [com.nexustools.genericui.component.media.GLCanvas] (OpenGL ES 2.0 / 3.0 / 3.2).
 * On Windows (`:windows`, `:qt`, `:gtk`, `:swing`), always uses Google ANGLE (`libEGL.dll` + `libGLESv2.dll`).
 */
interface GLCanvasNativeImpl<out W> : AbstractCanvasNativeImpl<W, GLContext> {
    fun setOnSurfaceCreated(listener: ((GLContext, Int, Int) -> Unit)?)
    fun setOnSurfaceResized(listener: ((GLContext, Int, Int) -> Unit)?)
}
