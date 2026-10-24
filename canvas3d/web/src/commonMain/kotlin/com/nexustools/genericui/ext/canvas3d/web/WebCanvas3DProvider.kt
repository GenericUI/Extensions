package com.nexustools.genericui.ext.canvas3d.web

import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.gpu.web.WebGpuProvider
import com.nexustools.genericui.web.WebGLCanvasImpl
import com.nexustools.genericui.web.WebGLContextImpl
import com.nexustools.genericui.web.WebVKCanvasImpl
import com.nexustools.genericui.web.WebVKContextImpl

/**
 * Registers WebGPU ([WebVKContextImpl]) and WebGL 2 ([WebGLContextImpl]) 3D contexts and canvas implementations into
 * [Canvas3DExtension] and [WebGpuProvider].
 */
object WebCanvas3DProvider {
    fun install(
        enableWebGpu: Boolean = true,
        enableWebGl2: Boolean = true
    ) {
        WebGpuProvider.install(enableWebGpu = enableWebGpu, enableWebGl2 = enableWebGl2)

        Canvas3DExtension.registerVKContextFactory { _, width, height ->
            WebVKContextImpl(
                swapchainWidth = width.coerceAtLeast(1),
                swapchainHeight = height.coerceAtLeast(1)
            )
        }
        Canvas3DExtension.registerGLContextFactory { _, width, height ->
            WebGLContextImpl().also {
                it.viewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
            }
        }
        Canvas3DExtension.registerGLCanvasFactory { _, _, _ ->
            WebGLCanvasImpl()
        }
        Canvas3DExtension.registerVKCanvasFactory { _, _, _ ->
            WebVKCanvasImpl()
        }
    }
}
