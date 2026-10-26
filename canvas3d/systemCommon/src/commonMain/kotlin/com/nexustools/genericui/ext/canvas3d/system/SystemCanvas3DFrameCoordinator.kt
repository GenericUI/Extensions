package com.nexustools.genericui.ext.canvas3d.system

import com.nexustools.genericui.ext.gpu.GpuBackendType

/**
 * Shared system-target (`:extensions:canvas3d:sharedSystem`) frame pacing and swapchain state
 * coordinator for Vulkan (`VKCanvasExt`) and OpenGL ES (`GLCanvasExt`) surfaces across Desktop,
 * Swing, and Mobile targets.
 */
class SystemCanvas3DFrameCoordinator(
    val preferredBackend: GpuBackendType = GpuBackendType.VULKAN,
    var targetFps: Int = 60
) {
    var frameCount: Long = 0L
        private set

    var lastFrameDeltaMs: Float = 16.67f
        private set

    fun recordFrameStep(deltaMs: Float): Long {
        lastFrameDeltaMs = deltaMs.coerceIn(0.1f, 250f)
        frameCount++
        return frameCount
    }
}
