package com.nexustools.genericui.macos

import com.nexustools.genericui.desktop.canvas.DesktopGLCanvasNativeImpl

/**
 * macOS OpenGL ES 3.0 canvas implementation backed by Metal-translated ANGLE (`libEGL.dylib` +
 * `libGLESv2.dylib` over `CAMetalLayer`) synchronized with `CADisplayLink`.
 */
class MacosGLCanvasNativeImpl : DesktopGLCanvasNativeImpl<MacosViewHandle>(
    nativeWidget = MacosViewHandle("GenericUIMetalAngleGLView"),
    rendererName = "ANGLE (Apple Metal Backend, OpenGL ES 3.0)",
    isAngle = true
)
