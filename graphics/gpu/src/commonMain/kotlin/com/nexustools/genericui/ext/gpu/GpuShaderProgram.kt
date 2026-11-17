package com.nexustools.genericui.ext.gpu

/**
 * Handle to a compiled GPU compute or fragment shader program containing both
 * primary Vulkan GLSL `#version 450` (SPIR-V) and fallback OpenGL ES `#version 300 es` sources.
 */
data class GpuShaderProgram(
    val id: Long,
    val vulkanGlsl450Source: String,
    val gles300ShaderSource: String,
    val stage: GpuShaderStage = GpuShaderStage.FRAGMENT,
    val backendType: GpuBackendType = GpuBackendType.VULKAN,
    val vulkanModuleHandle: Long = 0L,
    val glesProgramId: Int = 0
)
