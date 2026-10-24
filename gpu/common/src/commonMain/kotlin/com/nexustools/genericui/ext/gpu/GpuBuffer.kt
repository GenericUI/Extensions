package com.nexustools.genericui.ext.gpu

/**
 * Handle and descriptor for a GPU device memory buffer allocated on a [GpuOffscreenContext].
 */
data class GpuBuffer(
    val id: Long,
    val sizeBytes: Long,
    val usage: GpuBufferUsage,
    val backendType: GpuBackendType = GpuBackendType.VULKAN
)
