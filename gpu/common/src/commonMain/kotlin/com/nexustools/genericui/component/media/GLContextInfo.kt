package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.gpu.GpuAcceleration

data class GLContextInfo(
    val backend: GLBackendKind,
    val version: GLContextVersion = GLContextVersion.ES_3_0,
    val shadingLanguage: GLShadingLanguage = GLShadingLanguage.GLSL_ES_300,
    val acceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    val renderer: String = "",
    val vendor: String = ""
)
