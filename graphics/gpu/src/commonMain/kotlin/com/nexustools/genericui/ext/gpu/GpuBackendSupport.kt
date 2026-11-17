package com.nexustools.genericui.ext.gpu

/**
 * Describes runtime availability of hardware and software drivers for a specific [GpuBackendType].
 */
data class GpuBackendSupport(
    val backend: GpuBackendType,
    val hardwareAvailable: Boolean,
    val softwareAvailable: Boolean
)
