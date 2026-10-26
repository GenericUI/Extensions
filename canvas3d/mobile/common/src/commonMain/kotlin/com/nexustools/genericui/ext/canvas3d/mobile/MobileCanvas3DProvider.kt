package com.nexustools.genericui.ext.canvas3d.mobile

import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.gpu.mobile.MobileGpuProvider

/**
 * Registers mobile Vulkan / MoltenVK and Android EGL / iOS EAGL OpenGL ES 3.0 providers into
 * [Canvas3DExtension] and [MobileGpuProvider].
 */
object MobileCanvas3DProvider {
    fun install(
        enableVulkan: Boolean = true,
        enableOpenGlEs: Boolean = true
    ) {
        MobileGpuProvider.install(enableVulkan = enableVulkan, enableOpenGlEs = enableOpenGlEs)
    }
}
