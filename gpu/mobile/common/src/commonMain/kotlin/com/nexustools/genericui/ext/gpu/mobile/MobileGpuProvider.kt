package com.nexustools.genericui.ext.gpu.mobile

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext

/**
 * Registers mobile GPU providers into [GpuDevice], configuring Vulkan / MoltenVK as primary
 * and Android EGL / iOS EAGLContext OpenGL ES 3.0 as fallback.
 */
object MobileGpuProvider {
    var vulkanAvailable: Boolean = true
    var openGlEsAvailable: Boolean = true

    fun install(
        enableVulkan: Boolean = true,
        enableOpenGlEs: Boolean = true
    ) {
        vulkanAvailable = enableVulkan
        openGlEsAvailable = enableOpenGlEs

        GpuDevice.registerProvider(GpuBackendType.VULKAN) {
            if (vulkanAvailable) MobileGpuContext(GpuBackendType.VULKAN) else null
        }
        GpuDevice.registerProvider(GpuBackendType.OPENGL_ES) {
            if (openGlEsAvailable) MobileGpuContext(GpuBackendType.OPENGL_ES) else null
        }
    }

    fun acquireMobileContext(preferVulkan: Boolean = true): GpuOffscreenContext {
        install(vulkanAvailable, openGlEsAvailable)
        return GpuDevice.acquireOffscreenContext(preferVulkan)
    }
}
