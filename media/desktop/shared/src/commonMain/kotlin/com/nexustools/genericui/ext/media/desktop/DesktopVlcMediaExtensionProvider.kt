package com.nexustools.genericui.ext.media.desktop

import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.desktop.system.DesktopMediaSessionManagerNativeImpl
import com.nexustools.genericui.ext.gpu.desktop.DesktopGpuProvider
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcAudioAnalyzerImpl
import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcAudioPlayerImpl
import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcVideoPlayerImpl

/**
 * Shared desktop media extension provider that wires native `libvlc` ([DesktopLibVlcVideoPlayerImpl],
 * [DesktopLibVlcAudioPlayerImpl], [DesktopLibVlcAudioAnalyzerImpl]) into [MediaExtension] for
 * `:qt`, `:gtk`, `:windows`, and `:macos`.
 */
object DesktopVlcMediaExtensionProvider {
    fun install(
        platformName: String = "Desktop",
        vulkanAvailable: Boolean = true,
        angleOpenGlEsAvailable: Boolean = true
    ) {
        DesktopGpuProvider.install(
            enableVulkan = vulkanAvailable,
            enableAngleOpenGlEs = angleOpenGlEsAvailable
        )

        MediaExtension.registerProviders(
            videoPlayer = {
                DesktopLibVlcVideoPlayerImpl(
                    nativeWidget = DesktopNativeWidget(platformName, "libvlc-video"),
                    platformName = platformName
                )
            },
            audioPlayer = {
                DesktopLibVlcAudioPlayerImpl(
                    nativeWidget = DesktopNativeWidget(platformName, "libvlc-audio")
                )
            },
            audioAnalyzer = { fftSize ->
                DesktopLibVlcAudioAnalyzerImpl(initialFftSize = fftSize)
            },
            mediaSessionManager = {
                DesktopMediaSessionManagerNativeImpl(backendToolkit = platformName)
            }
        )
    }
}
