package com.nexustools.genericui.ext.gpu.mobile

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext

/**
 * Registers mobile GPU providers into [GpuDevice], configuring Vulkan / MoltenVK as primary
 * and Android EGL / iOS & iPadOS Metal ANGLE / EAGLContext OpenGL ES 3.0 as fallback.
 *
 * [BackendTarget.IPadOS] is supported identically to [BackendTarget.IOS].
 */
object MobileGpuProvider {
    var vulkanAvailable: Boolean = true
    var openGlEsAvailable: Boolean = true

    fun supportsTarget(target: BackendTarget): Boolean =
        target == BackendTarget.Android || target.isIosFamily

    fun detectSupport(
        backend: GpuBackendType,
        target: BackendTarget? = Application.current?.backendTarget ?: GenericUI.activeFactory?.backendTarget
    ): GpuBackendSupport {
        if (target != null && !supportsTarget(target)) {
            return GpuBackendSupport(
                backend = backend,
                hardwareAvailable = false,
                softwareAvailable = false
            )
        }
        return when (backend) {
            GpuBackendType.VULKAN -> GpuBackendSupport(
                backend = GpuBackendType.VULKAN,
                hardwareAvailable = vulkanAvailable,
                softwareAvailable = true
            )
            GpuBackendType.OPENGL_ES -> GpuBackendSupport(
                backend = GpuBackendType.OPENGL_ES,
                hardwareAvailable = openGlEsAvailable,
                softwareAvailable = true
            )
        }
    }

    fun register(preferVulkan: Boolean = true) {
        install(enableVulkan = preferVulkan || vulkanAvailable, enableOpenGlEs = openGlEsAvailable)
    }

    fun install(
        enableVulkan: Boolean = true,
        enableOpenGlEs: Boolean = true
    ) {
        vulkanAvailable = enableVulkan
        openGlEsAvailable = enableOpenGlEs

        GpuDevice.registerProvider(GpuBackendType.VULKAN) {
            if (vulkanAvailable) {
                val target = Application.current?.backendTarget ?: GenericUI.activeFactory?.backendTarget
                val name = when {
                    target == BackendTarget.Android -> "Android Vulkan 1.3 GPU"
                    target?.isIosFamily == true -> "Apple ${target.id.uppercase()} MoltenVK GPU"
                    else -> "Mobile Vulkan / MoltenVK GPU"
                }
                MobileGpuContext(GpuBackendType.VULKAN, deviceName = name)
            } else {
                null
            }
        }
        GpuDevice.registerProvider(GpuBackendType.OPENGL_ES) {
            if (openGlEsAvailable) {
                val target = Application.current?.backendTarget ?: GenericUI.activeFactory?.backendTarget
                val name = when {
                    target == BackendTarget.Android -> "Android EGL OpenGL ES 3.2 GPU"
                    target?.isIosFamily == true -> "Apple ${target.id.uppercase()} Metal ANGLE OpenGL ES 3.0 GPU"
                    else -> "Mobile EGL / EAGL OpenGL ES 3.0 GPU"
                }
                MobileGpuContext(GpuBackendType.OPENGL_ES, deviceName = name)
            } else {
                null
            }
        }
    }

    fun acquireMobileContext(preferVulkan: Boolean = true): GpuOffscreenContext {
        install(vulkanAvailable, openGlEsAvailable)
        return GpuDevice.acquireOffscreenContext(preferVulkan)
    }
}
