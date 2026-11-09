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
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.RTLD_LAZY
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.readlink

actual object DesktopGpuDynamicLoader {

    private var probedVulkan = false
    private var vulkanHardwareAvailable = false
    private var vulkanSoftwareAvailable = false

    private var probedAngle = false
    private var angleHardwareAvailable = false
    private var angleSoftwareAvailable = false

    private fun queryExecutableDir(): String? = memScoped {
        val buf = allocArray<ByteVar>(1024)
        val len = readlink("/proc/self/exe", buf, 1023u)
        if (len <= 0L) return null
        buf[len.toInt()] = 0
        val full = buf.toKString()
        val lastSep = full.lastIndexOf('/')
        if (lastSep > 0) full.substring(0, lastSep) else null
    }

    private fun openBundledOrSystemSo(primaryName: String, fallbackName: String): COpaquePointer? {
        val exeDir = queryExecutableDir()
        if (!exeDir.isNullOrBlank()) {
            dlopen("$exeDir/$primaryName", RTLD_LAZY)?.let { return it }
            dlopen("$exeDir/$fallbackName", RTLD_LAZY)?.let { return it }
            dlopen("$exeDir/lib/$primaryName", RTLD_LAZY)?.let { return it }
            dlopen("$exeDir/lib/$fallbackName", RTLD_LAZY)?.let { return it }
        }
        return dlopen(primaryName, RTLD_LAZY) ?: dlopen(fallbackName, RTLD_LAZY)
    }

    private fun ensureVulkanProbed() {
        if (probedVulkan) return
        probedVulkan = true
        val handle = openBundledOrSystemSo("libvulkan.so.1", "libvulkan.so")
        if (handle != null && dlsym(handle, "vkCreateInstance") != null) {
            vulkanHardwareAvailable = true
            vulkanSoftwareAvailable = true // Mesa Lavapipe / SwiftShader
        }
    }

    private fun ensureAngleProbed() {
        if (probedAngle) return
        probedAngle = true
        val egl = openBundledOrSystemSo("libEGL.so.1", "libEGL.so")
        val gles = openBundledOrSystemSo("libGLESv2.so.2", "libGLESv2.so")
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
