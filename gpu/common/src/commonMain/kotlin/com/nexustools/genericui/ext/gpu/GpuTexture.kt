package com.nexustools.genericui.ext.gpu

import com.nexustools.genericui.image.PixelFormat

/**
 * Handle and descriptor for a 2D RGBA or floating-point GPU texture allocated on a [GpuOffscreenContext].
 */
data class GpuTexture(
    val id: Long,
    val width: Int,
    val height: Int,
    val pixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    val backendType: GpuBackendType = GpuBackendType.VULKAN
)
