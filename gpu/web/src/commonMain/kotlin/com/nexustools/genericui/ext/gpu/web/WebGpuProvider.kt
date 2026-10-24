package com.nexustools.genericui.ext.gpu.web

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext

/**
 * Registers Web GPU providers into [GpuDevice], configuring WebGPU (`navigator.gpu`) as primary
 * and WebGL 2 (`OffscreenCanvas` / `WebGL2RenderingContext`) as fallback.
 */
object WebGpuProvider {
    var webGpuAvailable: Boolean = true
    var webGl2Available: Boolean = true

    fun install(
        enableWebGpu: Boolean = true,
        enableWebGl2: Boolean = true
    ) {
        webGpuAvailable = enableWebGpu
        webGl2Available = enableWebGl2

        GpuDevice.registerProvider(GpuBackendType.VULKAN) {
            if (webGpuAvailable) WebGpuAndWebGl2Context(GpuBackendType.VULKAN) else null
        }
        GpuDevice.registerProvider(GpuBackendType.OPENGL_ES) {
            if (webGl2Available) WebGpuAndWebGl2Context(GpuBackendType.OPENGL_ES) else null
        }
    }

    fun acquireWebContext(preferWebGpu: Boolean = true): GpuOffscreenContext {
        install(webGpuAvailable, webGl2Available)
        return GpuDevice.acquireOffscreenContext(preferWebGpu)
    }
}
