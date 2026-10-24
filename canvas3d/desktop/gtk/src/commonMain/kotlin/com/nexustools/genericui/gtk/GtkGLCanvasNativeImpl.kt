package com.nexustools.genericui.gtk

import com.nexustools.genericui.desktop.canvas.DesktopGLCanvasNativeImpl

/**
 * GTK 4 `GtkGLArea` implementation of [com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl]
 * configured with `gtk_gl_area_set_use_es(area, TRUE)` (OpenGL ES 3.0 / 3.2 via EGL on Linux and
 * Google ANGLE `libEGL` + `libGLESv2` on Windows).
 */
class GtkGLCanvasNativeImpl : DesktopGLCanvasNativeImpl<GtkWidgetHandle>(
    nativeWidget = GtkWidgetHandle("GtkGLArea"),
    rendererName = "GTK4 GtkGLArea (OpenGL ES 3.2 / ANGLE EGL)",
    isAngle = true
) {
    init {
        nativeWidget.attributes["gtk_gl_area_set_use_es"] = true
    }
}
