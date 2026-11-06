package com.nexustools.genericui.ext.canvas3d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas3DNativeImpl
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuDynamicLoader
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider

/**
 * Registers `:desktop`'s Vulkan 1.3 ([DesktopVKContextImpl]) and Google ANGLE OpenGL ES 3.0 ([DesktopGLContextImpl])
 * 3D contexts and [DesktopCanvas3DNativeImpl] into [Canvas3DRegistry] alongside [DesktopGpuProvider].
 */
object DesktopCanvas3DProvider {
    private var registered = false

    fun register(preferVulkan: Boolean = true) {
        install(enableVulkan = preferVulkan, enableAngleOpenGlEs = true)
    }

    fun install(
        enableVulkan: Boolean = true,
        enableAngleOpenGlEs: Boolean = true
    ) {
        registered = true
        DesktopGpuProvider.install(enableVulkan = enableVulkan, enableAngleOpenGlEs = enableAngleOpenGlEs)
        Canvas3DRegistry.registerProvider(
            supportDetector = { backend -> DesktopGpuDynamicLoader.detectSupport(backend) },
            canvas3DFactory = { canvas, acceleration ->
                DesktopCanvas3DNativeImpl(
                    nativeWidget = DesktopNativeWidget("Desktop", "canvas-3d"),
                    config = canvas.config,
                    activeBackend = canvas.backend,
                    activeAcceleration = acceleration,
                    initialWidth = canvas.initialWidth,
                    initialHeight = canvas.initialHeight
                )
            },
            renderContextFactory = { backend, acceleration, config, width, height ->
                when (backend) {
                    GpuBackendType.VULKAN -> DesktopVKContextImpl(
                        config = config.vulkan,
                        initialWidth = width,
                        initialHeight = height,
                        contextInfo = DesktopGpuDynamicLoader.queryVulkanContextInfo(
                            acceleration = acceleration,
                            apiVersion = config.vulkan.apiVersion
                        )
                    )
                    GpuBackendType.OPENGL_ES -> DesktopGLContextImpl(
                        config = config.gl,
                        initialWidth = width,
                        initialHeight = height,
                        contextInfo = DesktopGpuDynamicLoader.queryAngleContextInfo(
                            acceleration = acceleration,
                            version = config.gl.version
                        )
                    )
                }
            }
        )
    }
}
