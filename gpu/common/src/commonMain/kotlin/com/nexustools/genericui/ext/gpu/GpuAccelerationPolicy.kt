package com.nexustools.genericui.ext.gpu

/**
 * Policy controlling whether hardware acceleration is required, preferred, or bypassed in favor of software rendering.
 */
enum class GpuAccelerationPolicy {
    HARDWARE_ONLY,
    HARDWARE_PREFERRED,
    SOFTWARE_ONLY
}
