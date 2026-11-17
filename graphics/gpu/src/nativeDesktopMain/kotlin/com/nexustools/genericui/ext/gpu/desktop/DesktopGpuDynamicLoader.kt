package com.nexustools.genericui.ext.gpu.desktop

import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.VKApiVersion
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType

/**
 * Dynamically probes and loads native Vulkan (`vulkan-1.dll`, `libMoltenVK.dylib`, `libvulkan.so.1`)
 * and ANGLE / EGL OpenGL ES 3.0 (`libEGL` + `libGLESv2`) libraries at runtime.
 */
expect object DesktopGpuDynamicLoader {
    fun detectSupport(backend: GpuBackendType): GpuBackendSupport
    fun queryVulkanContextInfo(
        acceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
        apiVersion: VKApiVersion = VKApiVersion.V1_2
    ): VKContextInfo
    fun queryAngleContextInfo(
        acceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
        version: GLContextVersion = GLContextVersion.ES_3_0
    ): GLContextInfo
}
