package com.nexustools.genericui.ext.canvas3d.mobile

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.mobile.MobileGpuProvider
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * Registers mobile Vulkan / MoltenVK and Android EGL / iOS EAGL OpenGL ES 3.0 providers into
 * [Canvas3DRegistry] and [MobileGpuProvider].
 */
object MobileCanvas3DProvider {
    fun register(
        canvas3DFactory: ((Canvas3D, GpuAcceleration) -> Canvas3DNativeImpl<*>)? = null,
        preferVulkan: Boolean = true
    ) {
        install(enableVulkan = preferVulkan, enableOpenGlEs = true, canvas3DFactory = canvas3DFactory)
    }

    fun install(
        enableVulkan: Boolean = true,
        enableOpenGlEs: Boolean = true,
        canvas3DFactory: ((Canvas3D, GpuAcceleration) -> Canvas3DNativeImpl<*>)? = null
    ) {
        MobileGpuProvider.install(enableVulkan = enableVulkan, enableOpenGlEs = enableOpenGlEs)
        if (canvas3DFactory != null) {
            Canvas3DRegistry.registerProvider(
                supportDetector = { backend -> MobileGpuProvider.detectSupport(backend) },
                canvas3DFactory = canvas3DFactory
            )
        } else {
            Canvas3DRegistry.overrideAllSupportForTesting(
                hardwareAvailable = enableVulkan || enableOpenGlEs,
                softwareAvailable = true
            )
        }
    }
}
