package com.nexustools.genericui.ext.threejs.renderers

/**
 * Configuration parameters for [WebGLRenderer].
 */
data class WebGLRendererConfig(
    val antialias: Boolean = true,
    val alpha: Boolean = true,
    val depth: Boolean = true,
    val stencil: Boolean = false,
    val powerPreference: String = "high-performance",
    val preserveDrawingBuffer: Boolean = false,
    val preferVulkanWhenAvailable: Boolean = false
)
