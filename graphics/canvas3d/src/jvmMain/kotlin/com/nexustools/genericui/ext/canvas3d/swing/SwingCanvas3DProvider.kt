package com.nexustools.genericui.ext.canvas3d.swing

import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.swing.SwingGpuProvider
import com.nexustools.genericui.swing.SwingCanvas3DImpl
import com.nexustools.genericui.swing.SwingGLContextImpl
import com.nexustools.genericui.swing.SwingVKContextImpl

/**
 * Registers JVM/Swing LWJGL 3 Vulkan and LWJGL 3 OpenGL ES / Google ANGLE 3D contexts and [SwingCanvas3DImpl]
 * into [Canvas3DRegistry] and [SwingGpuProvider].
 */
object SwingCanvas3DProvider {
    fun register(preferVulkan: Boolean = true) {
        install()
    }

    fun install() {
        SwingGpuProvider.install()
        Canvas3DRegistry.registerProvider(
            supportDetector = { backend -> SwingGpuProvider.detectSupport(backend) },
            canvas3DFactory = { canvas, acceleration ->
                SwingCanvas3DImpl(
                    config = canvas.config,
                    activeBackend = canvas.backend,
                    activeAcceleration = acceleration,
                    initialWidth = canvas.initialWidth,
                    initialHeight = canvas.initialHeight
                )
            },
            renderContextFactory = { backend, acceleration, config, width, height ->
                when (backend) {
                    GpuBackendType.VULKAN -> SwingVKContextImpl(config.vulkan, width, height, acceleration)
                    GpuBackendType.OPENGL_ES -> SwingGLContextImpl(config.gl, width, height, acceleration)
                }
            }
        )
    }

    val isLwjglVulkanAvailable: Boolean
        get() = SwingGpuProvider.isLwjglVulkanOnClasspath

    val isLwjglOpenGlEsAvailable: Boolean
        get() = SwingGpuProvider.isLwjglOpenGlEsOnClasspath
}
