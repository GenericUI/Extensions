package com.nexustools.genericui.ext.media.mobile

import com.nexustools.genericui.ext.gpu.mobile.MobileGpuProvider
import com.nexustools.genericui.ext.media.MediaExtension

/**
 * Wires mobile (`:android` MediaCodec/ExoPlayer & `:ios` AVPlayer) media playback and
 * `:extensions:gpu:mobile` ([MobileGpuProvider] Vulkan / MoltenVK primary + OpenGL ES fallback)
 * into [MediaExtension].
 */
object MobileMediaExtensionProvider {
    fun install(
        enableVulkan: Boolean = true,
        enableOpenGlEs: Boolean = true
    ) {
        MobileGpuProvider.install(enableVulkan = enableVulkan, enableOpenGlEs = enableOpenGlEs)
    }
}
