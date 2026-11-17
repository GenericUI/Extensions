package com.nexustools.genericui.ext.canvas3d.mobile

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

internal expect fun createMobileCanvas3DNativeImpl(
    canvas: Canvas3D,
    acceleration: GpuAcceleration
): Canvas3DNativeImpl<*>

internal expect fun createMobileCanvas3DRenderContext(
    backend: GpuBackendType,
    acceleration: GpuAcceleration,
    config: Canvas3DConfig,
    width: Int,
    height: Int
): GpuRenderContext
