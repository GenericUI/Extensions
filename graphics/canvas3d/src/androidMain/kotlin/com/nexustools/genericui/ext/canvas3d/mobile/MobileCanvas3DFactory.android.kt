package com.nexustools.genericui.ext.canvas3d.mobile

import com.nexustools.genericui.android.AndroidCanvas3DImpl
import com.nexustools.genericui.android.AndroidGLContextImpl
import com.nexustools.genericui.android.AndroidVKContextImpl
import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

internal actual fun createMobileCanvas3DNativeImpl(
    canvas: Canvas3D,
    acceleration: GpuAcceleration
): Canvas3DNativeImpl<*> = AndroidCanvas3DImpl(
    config = canvas.config,
    activeBackend = canvas.backend,
    activeAcceleration = acceleration,
    initialWidth = canvas.initialWidth,
    initialHeight = canvas.initialHeight
)

internal actual fun createMobileCanvas3DRenderContext(
    backend: GpuBackendType,
    acceleration: GpuAcceleration,
    config: Canvas3DConfig,
    width: Int,
    height: Int
): GpuRenderContext = when (backend) {
    GpuBackendType.VULKAN -> AndroidVKContextImpl(
        config = config.vulkan,
        initialWidth = width,
        initialHeight = height,
        acceleration = acceleration
    )
    GpuBackendType.OPENGL_ES -> AndroidGLContextImpl(
        config = config.gl,
        initialWidth = width,
        initialHeight = height,
        acceleration = acceleration
    )
}
