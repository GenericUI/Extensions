package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer

/**
 * Base interface for an offscreen GPU-accelerated image manipulation operation in `:extensions:imageprocessing`.
 *
 * Implementations provide both:
 * - **Primary: Vulkan (`#version 450` SPIR-V)** shader source ([vulkanShaderSource]).
 * - **Fallback: OpenGL ES (`#version 300 es` / Google ANGLE)** shader source ([glesShaderSource]).
 * - Deterministic execution via [executeOnGpuOrFallback] over [GpuOffscreenContext].
 */
interface ImageFilterOperation {
    val name: String
    val vulkanShaderSource: String
    val glesShaderSource: String

    fun executeOnGpuOrFallback(
        input: ImageBuffer,
        gpu: GpuOffscreenContext
    ): ImageBuffer
}
