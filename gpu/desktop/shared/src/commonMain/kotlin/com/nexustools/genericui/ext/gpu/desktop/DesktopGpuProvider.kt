package com.nexustools.genericui.ext.gpu.desktop

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext

/**
 * Registers desktop GPU contexts into [GpuDevice], configuring [DesktopVulkanGpuContext] (Vulkan 1.3)
 * as the primary backend and [DesktopAngleGlesGpuContext] (OpenGL ES 3.0 / Google ANGLE on Windows)
 * as the automatic fallback backend.
 */
object DesktopGpuProvider {
    var vulkanAvailable: Boolean = true
    var angleOpenGlEsAvailable: Boolean = true

    fun install(
        enableVulkan: Boolean = true,
        enableAngleOpenGlEs: Boolean = true
    ) {
        vulkanAvailable = enableVulkan
        angleOpenGlEsAvailable = enableAngleOpenGlEs

        GpuDevice.registerProvider(GpuBackendType.VULKAN) {
            if (vulkanAvailable) DesktopVulkanGpuContext() else null
        }
        GpuDevice.registerProvider(GpuBackendType.OPENGL_ES) {
            if (angleOpenGlEsAvailable) DesktopAngleGlesGpuContext() else null
        }
    }

    fun acquireDesktopContext(preferVulkan: Boolean = true): GpuOffscreenContext {
        install(vulkanAvailable, angleOpenGlEsAvailable)
        return GpuDevice.acquireOffscreenContext(preferVulkan)
    }
}
