package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType

/**
 * Unified native implementation contract for [com.nexustools.genericui.component.media.Canvas3D]
 * across Vulkan (`VKContext`) and OpenGL ES 3.0+ / ANGLE / WebGL2 (`GLContext`).
 */
interface Canvas3DNativeImpl<out W> : AbstractCanvasNativeImpl<W, GpuRenderContext> {
    val config: Canvas3DConfig
    val activeBackend: GpuBackendType
    val activeAcceleration: GpuAcceleration
    val vkContext: VKContext?
    val glContext: GLContext?
    fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?)
    fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?)
    fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {}
}
