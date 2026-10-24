package com.nexustools.genericui.ext.gpu.swing

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext

/**
 * Runtime detector and provider for `:extensions:gpu:swing`, probing the JVM classpath for
 * optional LWJGL 3 Vulkan (`org.lwjgl.vulkan.VK`) as primary and LWJGL 3 OpenGL ES / Google ANGLE
 * (`org.lwjgl.opengles.GLES` / `org.lwjgl.opengl.GL`) as fallback.
 */
object SwingGpuProvider {
    val isLwjglVulkanOnClasspath: Boolean by lazy {
        isClassPresent("org.lwjgl.vulkan.VK")
    }

    val isLwjglOpenGlEsOnClasspath: Boolean by lazy {
        isClassPresent("org.lwjgl.opengles.GLES") || isClassPresent("org.lwjgl.opengl.GL")
    }

    fun install() {
        GpuDevice.registerProvider(GpuBackendType.VULKAN) {
            SwingLwjglGpuContext(
                backendType = GpuBackendType.VULKAN,
                deviceName = if (isLwjglVulkanOnClasspath) {
                    "LWJGL 3 Vulkan 1.3 Native Driver"
                } else {
                    "Swing Vulkan 1.3 Context (Headless/Simulated)"
                }
            )
        }
        GpuDevice.registerProvider(GpuBackendType.OPENGL_ES) {
            SwingLwjglGpuContext(
                backendType = GpuBackendType.OPENGL_ES,
                deviceName = if (isLwjglOpenGlEsOnClasspath) {
                    "LWJGL 3 OpenGL ES 3.0 (Google ANGLE)"
                } else {
                    "Swing OpenGL ES 3.0 Context (Headless/Simulated)"
                }
            )
        }
    }

    fun acquireSwingContext(preferVulkan: Boolean = true): GpuOffscreenContext {
        install()
        return GpuDevice.acquireOffscreenContext(preferVulkan)
    }

    private fun isClassPresent(className: String): Boolean =
        try {
            Class.forName(className, false, SwingGpuProvider::class.java.classLoader)
            true
        } catch (_: Throwable) {
            false
        }
}
