@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.gpu.desktop

import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.VKApiVersion
import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import platform.posix.RTLD_LAZY
import platform.posix.dlopen
import platform.posix.dlsym

actual object DesktopGpuDynamicLoader {

    private var probedVulkan = false
    private var vulkanHardwareAvailable = false
    private var vulkanSoftwareAvailable = false

    private var probedAngle = false
    private var angleHardwareAvailable = false
    private var angleSoftwareAvailable = false

    private fun ensureVulkanProbed() {
        if (probedVulkan) return
        probedVulkan = true
        val handle = dlopen("libvulkan.so.1", RTLD_LAZY) ?: dlopen("libvulkan.so", RTLD_LAZY)
        if (handle != null && dlsym(handle, "vkCreateInstance") != null) {
            vulkanHardwareAvailable = true
            vulkanSoftwareAvailable = true // Mesa Lavapipe / SwiftShader
        }
    }

    private fun ensureAngleProbed() {
        if (probedAngle) return
        probedAngle = true
        val egl = dlopen("libEGL.so.1", RTLD_LAZY) ?: dlopen("libEGL.so", RTLD_LAZY)
        val gles = dlopen("libGLESv2.so.2", RTLD_LAZY) ?: dlopen("libGLESv2.so", RTLD_LAZY)
        if (egl != null && gles != null && dlsym(egl, "eglGetDisplay") != null) {
            angleHardwareAvailable = true
            angleSoftwareAvailable = true // Mesa llvmpipe / softpipe
        }
    }

    actual fun detectSupport(backend: GpuBackendType): GpuBackendSupport = when (backend) {
        GpuBackendType.VULKAN -> {
            ensureVulkanProbed()
            GpuBackendSupport(
                backend = GpuBackendType.VULKAN,
                hardwareAvailable = vulkanHardwareAvailable,
                softwareAvailable = vulkanSoftwareAvailable
            )
        }
        GpuBackendType.OPENGL_ES -> {
            ensureAngleProbed()
            GpuBackendSupport(
                backend = GpuBackendType.OPENGL_ES,
                hardwareAvailable = angleHardwareAvailable,
                softwareAvailable = angleSoftwareAvailable
            )
        }
    }

    actual fun queryVulkanContextInfo(
        acceleration: GpuAcceleration,
        apiVersion: VKApiVersion
    ): VKContextInfo = VKContextInfo(
        backend = VKBackendKind.VULKAN_NATIVE,
        apiVersion = apiVersion,
        deviceName = if (acceleration == GpuAcceleration.SOFTWARE) "Mesa Lavapipe / SwiftShader" else "Linux Vulkan GPU",
        deviceType = if (acceleration == GpuAcceleration.SOFTWARE) VKDeviceType.CPU else VKDeviceType.DISCRETE_GPU,
        acceleration = acceleration
    )

    actual fun queryAngleContextInfo(
        acceleration: GpuAcceleration,
        version: GLContextVersion
    ): GLContextInfo = GLContextInfo(
        backend = GLBackendKind.MESA_EGL,
        version = version,
        shadingLanguage = when (version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = acceleration,
        renderer = if (acceleration == GpuAcceleration.SOFTWARE) "Mesa llvmpipe (EGL)" else "Mesa EGL / GLESv2",
        vendor = "Mesa"
    )
}
