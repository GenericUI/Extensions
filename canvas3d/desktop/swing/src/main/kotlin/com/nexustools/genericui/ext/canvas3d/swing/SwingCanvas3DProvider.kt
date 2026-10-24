package com.nexustools.genericui.ext.canvas3d.swing

import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.gpu.swing.SwingGpuProvider
import com.nexustools.genericui.swing.SwingGLCanvasImpl
import com.nexustools.genericui.swing.SwingGLContextImpl
import com.nexustools.genericui.swing.SwingVKCanvasImpl
import com.nexustools.genericui.swing.SwingVKContextImpl

/**
 * Registers JVM/Swing LWJGL 3 Vulkan and LWJGL 3 OpenGL ES / Google ANGLE 3D contexts and canvas implementations into
 * [Canvas3DExtension] and [SwingGpuProvider].
 */
object SwingCanvas3DProvider {
    fun install() {
        SwingGpuProvider.install()
        Canvas3DExtension.registerGLContextFactory { _, width, height ->
            SwingGLContextImpl(width.coerceAtLeast(1), height.coerceAtLeast(1))
        }
        Canvas3DExtension.registerVKContextFactory { _, width, height ->
            SwingVKContextImpl(width.coerceAtLeast(1), height.coerceAtLeast(1))
        }
        Canvas3DExtension.registerGLCanvasFactory { _, _, _ ->
            SwingGLCanvasImpl()
        }
        Canvas3DExtension.registerVKCanvasFactory { _, _, _ ->
            SwingVKCanvasImpl()
        }
    }

    val isLwjglVulkanAvailable: Boolean
        get() = SwingGpuProvider.isLwjglVulkanOnClasspath

    val isLwjglOpenGlEsAvailable: Boolean
        get() = SwingGpuProvider.isLwjglOpenGlEsOnClasspath
}
