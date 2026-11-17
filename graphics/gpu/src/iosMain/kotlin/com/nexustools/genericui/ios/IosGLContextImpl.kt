package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContextAttributes
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * iOS Metal ANGLE / OpenGL ES 3.0 implementation of [com.nexustools.genericui.component.media.GLContext].
 */
class IosGLContextImpl(
    config: GLContextAttributes = GLContextAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
) : DesktopGLContextImpl(
    config = config,
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    contextInfo = GLContextInfo(
        backend = GLBackendKind.ANGLE_METAL,
        version = config.version,
        shadingLanguage = when (config.version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = acceleration,
        renderer = "iOS ANGLE (Apple Metal)",
        vendor = "Apple / Google Inc."
    )
)
