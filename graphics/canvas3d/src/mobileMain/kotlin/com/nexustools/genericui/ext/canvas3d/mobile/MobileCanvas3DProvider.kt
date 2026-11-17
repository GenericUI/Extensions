package com.nexustools.genericui.ext.canvas3d.mobile

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.mobile.MobileGpuProvider
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * Registers mobile Vulkan / MoltenVK and Android EGL / iOS & iPadOS Metal ANGLE OpenGL ES 3.0
 * providers and native [Canvas3DNativeImpl] factories into [Canvas3DRegistry] and [MobileGpuProvider].
 *
 * [BackendTarget.IPadOS] is supported identically to [BackendTarget.IOS].
 */
object MobileCanvas3DProvider {
    fun supportsTarget(target: BackendTarget): Boolean =
        target == BackendTarget.Android || target.isIosFamily

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
        val resolvedFactory: (Canvas3D, GpuAcceleration) -> Canvas3DNativeImpl<*> =
            canvas3DFactory ?: { canvas, acceleration ->
                createMobileCanvas3DNativeImpl(canvas, acceleration)
            }
        Canvas3DRegistry.registerProvider(
            supportDetector = { backend -> MobileGpuProvider.detectSupport(backend) },
            canvas3DFactory = resolvedFactory,
            renderContextFactory = { backend, acceleration, config, width, height ->
                createMobileCanvas3DRenderContext(
                    backend = backend,
                    acceleration = acceleration,
                    config = config,
                    width = width,
                    height = height
                )
            }
        )
    }
}
