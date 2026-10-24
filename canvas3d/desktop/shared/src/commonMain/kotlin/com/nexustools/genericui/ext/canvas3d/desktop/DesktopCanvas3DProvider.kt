package com.nexustools.genericui.ext.canvas3d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopGLCanvasNativeImpl
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKCanvasNativeImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider

/**
 * Registers `:desktop`'s Vulkan 1.3 ([DesktopVKContextImpl]) and Google ANGLE OpenGL ES 3.0 ([DesktopGLContextImpl])
 * 3D contexts and canvas implementations into [Canvas3DExtension] alongside [DesktopGpuProvider].
 */
object DesktopCanvas3DProvider {
    fun install(
        enableVulkan: Boolean = true,
        enableAngleOpenGlEs: Boolean = true
    ) {
        DesktopGpuProvider.install(enableVulkan = enableVulkan, enableAngleOpenGlEs = enableAngleOpenGlEs)

        Canvas3DExtension.registerVKContextFactory { _, width, height ->
            DesktopVKContextImpl(
                swapchainWidth = width.coerceAtLeast(1),
                swapchainHeight = height.coerceAtLeast(1)
            )
        }
        Canvas3DExtension.registerGLContextFactory { config, width, height ->
            DesktopGLContextImpl(
                isAngleBackend = config.preferAngleOnWindows
            ).also {
                it.viewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
            }
        }
        Canvas3DExtension.registerGLCanvasFactory { config, _, _ ->
            DesktopGLCanvasNativeImpl(
                nativeWidget = DesktopNativeWidget("Desktop", "gl-canvas"),
                isAngle = config.preferAngleOnWindows
            )
        }
        Canvas3DExtension.registerVKCanvasFactory { _, _, _ ->
            DesktopVKCanvasNativeImpl(
                nativeWidget = DesktopNativeWidget("Desktop", "vk-canvas")
            )
        }
    }
}
