package com.nexustools.genericui.windows

import com.nexustools.genericui.desktop.canvas.DesktopGLCanvasNativeImpl

/**
 * Windows OpenGL ES 2.0 / 3.0 / 3.2 canvas implementation backed exclusively by **Google ANGLE**
 * (`libEGL.dll` + `libGLESv2.dll` over Direct3D 11 / Vulkan) with `eglSwapInterval(display, 1)` VBlank sync.
 */
class WindowsGLCanvasNativeImpl : DesktopGLCanvasNativeImpl<Win32HwndHandle>(
    nativeWidget = Win32HwndHandle("GenericUIAngleEGLCanvasW"),
    rendererName = "ANGLE (Windows, D3D11/Vulkan Backend, OpenGL ES 3.0)",
    isAngle = true
) {
    init {
        nativeWidget.attributes["eglLibrary"] = "libEGL.dll"
        nativeWidget.attributes["glesv2Library"] = "libGLESv2.dll"
        nativeWidget.attributes["eglSwapInterval"] = 1
    }
}
