package com.nexustools.genericui.qt

import com.nexustools.genericui.desktop.canvas.DesktopGLCanvasNativeImpl

/**
 * Qt 6 `QOpenGLWidget` (`QSurfaceFormat::OpenGLES` 3.0 + `QOpenGLExtraFunctions` + Google ANGLE on Windows)
 * implementation of [com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl].
 */
class QtGLCanvasNativeImpl : DesktopGLCanvasNativeImpl<QtWidgetHandle>(
    nativeWidget = QtWidgetHandle("QOpenGLWidget"),
    rendererName = "Qt6 QOpenGLWidget (OpenGL ES 3.0 / Google ANGLE)",
    isAngle = true
) {
    init {
        nativeWidget.attributes["renderableType"] = "QSurfaceFormat::OpenGLES"
        nativeWidget.attributes["swapInterval"] = 1
    }
}
