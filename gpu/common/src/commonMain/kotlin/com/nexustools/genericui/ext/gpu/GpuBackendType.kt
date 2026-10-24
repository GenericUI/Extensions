package com.nexustools.genericui.ext.gpu

/**
 * Hardware GPU backend API priority used across `:extensions:gpu`, `:extensions:imageprocessing`,
 * `:extensions:canvas3d`, and `:extensions:media`:
 * - [VULKAN]: Primary GPU backend (Vulkan 1.1/1.2/1.3, MoltenVK on Apple, WebGPU on Web).
 * - [OPENGL_ES]: Secondary GPU fallback backend (OpenGL ES 3.0/3.2, Google ANGLE on Windows, WebGL2 on Web).
 * - [SOFTWARE_FALLBACK]: Deterministic CPU/SIMD fallback used in headless unit tests or when no GPU driver is present.
 */
enum class GpuBackendType {
    VULKAN,
    OPENGL_ES,
    SOFTWARE_FALLBACK
}
