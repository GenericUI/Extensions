package com.nexustools.genericui.component.media

/**
 * Configuration options for initializing a [GLCanvas] (OpenGL ES 2.0 / 3.0 / 3.2).
 *
 * On Windows (`:windows`, `:qt`, `:gtk`, `:swing`), [preferAngleOnWindows] defaults to `true`
 * so OpenGL ES calls execute through Google ANGLE (`libEGL.dll` + `libGLESv2.dll` over Direct3D 11 / Vulkan).
 */
data class GLCanvasConfig(
    val majorVersion: Int = 3,
    val minorVersion: Int = 0,
    val alphaBits: Int = 8,
    val depthBits: Int = 24,
    val stencilBits: Int = 8,
    val msaaSamples: Int = 4,
    val vsync: Boolean = true,
    val preferAngleOnWindows: Boolean = true
)
