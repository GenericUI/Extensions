package com.nexustools.genericui.ext.canvas3d.web

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.web.WebGpuProvider
import com.nexustools.genericui.web.WebBrowserDomBridge
import com.nexustools.genericui.web.WebCanvas3DImpl
import com.nexustools.genericui.web.WebDomElement
import com.nexustools.genericui.web.WebGLContextImpl
import com.nexustools.genericui.web.WebGl2BrowserBridge
import com.nexustools.genericui.web.WebScrollCanvas3DImpl
import com.nexustools.genericui.web.WebVKContextImpl

/**
 * Registers WebGPU ([WebVKContextImpl]), WebGL 2 ([WebGLContextImpl]), [WebCanvas3DImpl],
 * and [WebScrollCanvas3DImpl] into [Canvas3DRegistry], [Canvas3DExtension], and [WebGpuProvider].
 */
object WebCanvas3DProvider {
    fun register(preferWebGpu: Boolean = !WebBrowserDomBridge.isBrowserEnvironment) {
        install(enableWebGpu = preferWebGpu, enableWebGl2 = true)
    }

    fun createContext(
        canvas: WebDomElement = WebDomElement("canvas"),
        backend: GpuBackendType = GpuBackendType.OPENGL_ES,
        config: Canvas3DConfig = Canvas3DConfig(),
        width: Int = 800,
        height: Int = 600,
        acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
    ): GpuRenderContext {
        val useWebGl2 = backend == GpuBackendType.OPENGL_ES ||
            (WebBrowserDomBridge.isBrowserEnvironment && !WebGpuProvider.webGpuAvailable && WebGl2BrowserBridge.isWebGl2Supported())
        return if (useWebGl2) {
            WebGLContextImpl(canvas, config.gl, width, height, acceleration)
        } else {
            WebVKContextImpl(canvas, config.vulkan, width, height, acceleration)
        }
    }

    fun install(
        enableWebGpu: Boolean = !WebBrowserDomBridge.isBrowserEnvironment,
        enableWebGl2: Boolean = true
    ) {
        WebGpuProvider.install(enableWebGpu = enableWebGpu, enableWebGl2 = enableWebGl2)

        Canvas3DRegistry.registerProvider(
            supportDetector = { backend -> WebGpuProvider.detectSupport(backend) },
            canvas3DFactory = { canvas, acceleration ->
                val resolvedBackend = if (
                    canvas.backend == GpuBackendType.VULKAN &&
                    WebBrowserDomBridge.isBrowserEnvironment &&
                    !WebGpuProvider.webGpuAvailable &&
                    WebGl2BrowserBridge.isWebGl2Supported()
                ) {
                    GpuBackendType.OPENGL_ES
                } else {
                    canvas.backend
                }
                WebCanvas3DImpl(
                    config = canvas.config,
                    activeBackend = resolvedBackend,
                    activeAcceleration = acceleration,
                    initialWidth = canvas.initialWidth,
                    initialHeight = canvas.initialHeight
                )
            },
            renderContextFactory = { backend, acceleration, config, width, height ->
                val dom = WebDomElement("canvas")
                createContext(
                    canvas = dom,
                    backend = backend,
                    config = config,
                    width = width,
                    height = height,
                    acceleration = acceleration
                )
            }
        )

        Canvas3DExtension.registerScrollCanvas3DFactory { viewportWidth, viewportHeight, contentWidth, contentHeight, backend ->
            WebScrollCanvas3DImpl(
                initialViewportWidth = viewportWidth,
                initialViewportHeight = viewportHeight,
                initialContentWidth = contentWidth,
                initialContentHeight = contentHeight,
                backend = backend
            )
        }
    }
}
