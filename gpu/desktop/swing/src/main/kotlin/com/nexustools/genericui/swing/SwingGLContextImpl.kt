package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContextAttributes
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * Swing/JVM implementation of [com.nexustools.genericui.component.media.GLContext] backed by
 * LWJGL 3 OpenGL ES / EGL when available on the classpath and the GenericUI GLES3 pipeline.
 */
class SwingGLContextImpl(
    config: GLContextAttributes = GLContextAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = if (isLwjglGlAvailable()) GpuAcceleration.HARDWARE else GpuAcceleration.SOFTWARE
) : DesktopGLContextImpl(
    config = config,
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    contextInfo = GLContextInfo(
        backend = GLBackendKind.LWJGL_EGL,
        version = config.version,
        shadingLanguage = when (config.version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = acceleration,
        renderer = if (acceleration == GpuAcceleration.HARDWARE) "LWJGL OpenGL ES 3.0" else "GenericUI Software GLES 3.0",
        vendor = "LWJGL / GenericUI"
    )
) {
    val lwjglAvailable: Boolean = isLwjglGlAvailable()

    companion object {
        fun isLwjglGlAvailable(): Boolean = try {
            Class.forName("org.lwjgl.opengl.GL")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
