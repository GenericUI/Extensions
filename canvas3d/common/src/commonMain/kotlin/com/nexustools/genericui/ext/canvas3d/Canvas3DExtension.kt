package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl
import com.nexustools.genericui.nativeimpl.media.ScrollCanvas3DNativeImpl

/**
 * Extension coordinator for `:extensions:canvas3d` bridging [Canvas3D] and [ScrollCanvas3D]
 * with `:extensions:gpu` ([GpuDevice], [GpuOffscreenContext], [VKContext], and [GLContext]).
 */
object Canvas3DExtension {
    private var scrollCanvas3dNativeFactory: ((Int, Int, Int, Int, GpuBackendType) -> ScrollCanvas3DNativeImpl<*, *>)? = null

    fun registerScrollCanvas3DFactory(
        factory: (viewportWidth: Int, viewportHeight: Int, contentWidth: Int, contentHeight: Int, backend: GpuBackendType) -> ScrollCanvas3DNativeImpl<*, *>
    ) {
        scrollCanvas3dNativeFactory = factory
    }

    fun createCanvas3DNative(canvas: Canvas3D): Canvas3DNativeImpl<*> =
        Canvas3DRegistry.createCanvas3DNative(canvas)

    fun createScrollCanvas3DNativeOrNull(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight,
        backend: GpuBackendType = GpuBackendType.VULKAN
    ): ScrollCanvas3DNativeImpl<*, *>? =
        scrollCanvas3dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight, backend)

    fun createScrollCanvas3DNative(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight,
        backend: GpuBackendType = GpuBackendType.VULKAN,
        acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
        config: Canvas3DConfig = Canvas3DConfig()
    ): ScrollCanvas3DNativeImpl<*, *> =
        scrollCanvas3dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight, backend)
            ?: SystemScrollCanvas3DNativeImpl(
                initialViewportWidth = viewportWidth,
                initialViewportHeight = viewportHeight,
                initialContentWidth = contentWidth,
                initialContentHeight = contentHeight,
                backend = backend,
                acceleration = acceleration,
                config = config
            )

    fun acquireGpuOffscreenContext(preferVulkan: Boolean = true): GpuOffscreenContext =
        GpuDevice.acquireOffscreenContext(preferVulkan)

    fun createOffscreenCanvas3D(
        width: Int = 800,
        height: Int = 600,
        preferVulkan: Boolean = true,
        config: Canvas3DConfig = Canvas3DConfig()
    ): OffscreenCanvas3D = OffscreenCanvas3D(
        width = width,
        height = height,
        preferVulkan = preferVulkan,
        config = config,
        gpuContext = acquireGpuOffscreenContext(preferVulkan)
    )

    fun createRenderContext(
        backend: GpuBackendType = GpuBackendType.VULKAN,
        acceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
        config: Canvas3DConfig = Canvas3DConfig(),
        width: Int = 800,
        height: Int = 600
    ): GpuRenderContext =
        Canvas3DRegistry.createDefaultPipelineContext(
            backend = backend,
            acceleration = acceleration,
            config = config,
            width = width,
            height = height
        )

    fun createVKContext(
        config: Canvas3DConfig = Canvas3DConfig(),
        width: Int = 800,
        height: Int = 600,
        acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
    ): VKContext =
        createRenderContext(GpuBackendType.VULKAN, acceleration, config, width, height) as VKContext

    fun createGLContext(
        config: Canvas3DConfig = Canvas3DConfig(),
        width: Int = 800,
        height: Int = 600,
        acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
    ): GLContext =
        createRenderContext(GpuBackendType.OPENGL_ES, acceleration, config, width, height) as GLContext
}
