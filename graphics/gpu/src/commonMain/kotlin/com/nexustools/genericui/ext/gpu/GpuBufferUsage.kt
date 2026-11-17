package com.nexustools.genericui.ext.gpu

/**
 * Memory usage flags for a [GpuBuffer] allocated on [GpuOffscreenContext].
 */
enum class GpuBufferUsage {
    VERTEX,
    INDEX,
    UNIFORM,
    STORAGE,
    TRANSFER_SRC,
    TRANSFER_DST
}
