package com.nexustools.genericui.ext.gpu

/**
 * Hardware-accelerated GPU API backends supported by `:extensions:graphics:gpu` and `:extensions:graphics:canvas3d`.
 *
 * Software fallback is modeled orthogonally via [GpuAcceleration] and [GpuAccelerationPolicy].
 */
enum class GpuBackendType {
    VULKAN,
    OPENGL_ES
}
