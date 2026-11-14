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
import kotlinx.cinterop.COpaquePointer
import platform.Foundation.NSBundle
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

    private fun queryExecutableDir(): String? {
        val full = NSBundle.mainBundle.executablePath ?: return null
        val lastSep = full.lastIndexOf('/')
        return if (lastSep > 0) full.substring(0, lastSep) else null
    }

    private fun openBundledOrSystemDylib(fileName: String): COpaquePointer? {
        val exeDir = queryExecutableDir()
        if (!exeDir.isNullOrBlank()) {
            dlopen("$exeDir/../Frameworks/$fileName", RTLD_LAZY)?.let { return it }
            dlopen("$exeDir/$fileName", RTLD_LAZY)?.let { return it }
        }
        return dlopen(fileName, RTLD_LAZY)
            ?: dlopen("/opt/homebrew/lib/$fileName", RTLD_LAZY)
            ?: dlopen("/usr/local/lib/$fileName", RTLD_LAZY)
    }

    private fun ensureVulkanProbed() {
        if (probedVulkan) return
        probedVulkan = true
        val handle = openBundledOrSystemDylib("libMoltenVK.dylib")
            ?: openBundledOrSystemDylib("libvulkan.1.dylib")
        if (handle != null && dlsym(handle, "vkCreateInstance") != null) {
            vulkanHardwareAvailable = true
        }
        val swHandle = openBundledOrSystemDylib("libvk_swiftshader.dylib")
        if (swHandle != null) {
            vulkanSoftwareAvailable = true
        }
    }

    private fun ensureAngleProbed() {
        if (probedAngle) return
        probedAngle = true
        val egl = openBundledOrSystemDylib("libEGL.dylib")
        val gles = openBundledOrSystemDylib("libGLESv2.dylib")
        if (egl != null && gles != null && dlsym(egl, "eglGetDisplay") != null) {
            angleHardwareAvailable = true
        }
        if (openBundledOrSystemDylib("libvk_swiftshader.dylib") != null) {
            angleSoftwareAvailable = true
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
        backend = VKBackendKind.MOLTENVK,
        apiVersion = apiVersion,
        deviceName = if (acceleration == GpuAcceleration.SOFTWARE) "SwiftShader macOS" else "Apple Metal (MoltenVK)",
        deviceType = if (acceleration == GpuAcceleration.SOFTWARE) VKDeviceType.CPU else VKDeviceType.INTEGRATED_GPU,
        acceleration = acceleration
    )

    actual fun queryAngleContextInfo(
        acceleration: GpuAcceleration,
        version: GLContextVersion
    ): GLContextInfo = GLContextInfo(
        backend = GLBackendKind.ANGLE_METAL,
        version = version,
        shadingLanguage = when (version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = acceleration,
        renderer = if (acceleration == GpuAcceleration.SOFTWARE) "ANGLE (SwiftShader)" else "ANGLE (Apple Metal)",
        vendor = "Google Inc. (ANGLE)"
    )
}
