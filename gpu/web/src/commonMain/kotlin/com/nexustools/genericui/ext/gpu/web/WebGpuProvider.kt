package com.nexustools.genericui.ext.gpu.web

import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.web.WebBrowserDomBridge
import com.nexustools.genericui.web.WebGl2BrowserBridge

/**
 * Registers Web GPU providers into [GpuDevice], configuring hardware WebGL 2 (`WebGL2RenderingContext`)
 * as the hardware-accelerated browser backend when WebGPU hardware dispatch is not enabled.
 */
object WebGpuProvider {
    var webGpuAvailable: Boolean = !WebBrowserDomBridge.isBrowserEnvironment
    var webGl2Available: Boolean = true

    fun detectSupport(backend: GpuBackendType): GpuBackendSupport = when (backend) {
        GpuBackendType.VULKAN -> GpuBackendSupport(
            backend = GpuBackendType.VULKAN,
            hardwareAvailable = if (WebBrowserDomBridge.isBrowserEnvironment && !webGpuAvailable) {
                false
            } else {
                webGpuAvailable
            },
            softwareAvailable = true
        )
        GpuBackendType.OPENGL_ES -> GpuBackendSupport(
            backend = GpuBackendType.OPENGL_ES,
            hardwareAvailable = if (WebBrowserDomBridge.isBrowserEnvironment) {
                webGl2Available && WebGl2BrowserBridge.isWebGl2Supported()
            } else {
                webGl2Available
            },
            softwareAvailable = true
        )
    }

    fun register(preferWebGpu: Boolean = !WebBrowserDomBridge.isBrowserEnvironment) {
        install(enableWebGpu = preferWebGpu, enableWebGl2 = webGl2Available)
    }

    fun install(
        enableWebGpu: Boolean = !WebBrowserDomBridge.isBrowserEnvironment,
        enableWebGl2: Boolean = true
    ) {
        webGpuAvailable = enableWebGpu
        webGl2Available = enableWebGl2

        GpuDevice.registerProvider(GpuBackendType.VULKAN) {
            if (webGpuAvailable) WebGpuAndWebGl2Context(GpuBackendType.VULKAN) else null
        }
        GpuDevice.registerProvider(GpuBackendType.OPENGL_ES) {
            val glSupported = if (WebBrowserDomBridge.isBrowserEnvironment) {
                webGl2Available && WebGl2BrowserBridge.isWebGl2Supported()
            } else {
                webGl2Available
            }
            if (glSupported) WebGpuAndWebGl2Context(GpuBackendType.OPENGL_ES) else null
        }
    }

    fun acquireWebContext(preferWebGpu: Boolean = !WebBrowserDomBridge.isBrowserEnvironment): GpuOffscreenContext {
        install(webGpuAvailable, webGl2Available)
        return GpuDevice.acquireOffscreenContext(preferWebGpu)
    }
}
