package com.nexustools.genericui.ext.media.desktop

import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.desktop.core.DesktopStageSubtreeClassifier
import com.nexustools.genericui.desktop.system.DesktopMediaSessionManagerNativeImpl
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
    private val MEDIA_STAGE_TOKENS: Set<String> = setOf(
        "desktop-web-video-overlay",
        "mobile-touch-video-overlay",
        "desktop-web-visualizer-overlay",
        "mobile-touch-visualizer-overlay",
        "video-overlay",
        "player-overlay",
        "player-overlay-controls-layer",
        "seek-bar",
        "seek-hover-bubble",
        "subtitle-banner",
        "expanding-volume-pill",
        "expanding-volume-slider",
        "libvlc-video-player",
        "video-player",
        "genericui-video-overlay-host"
    )

    private val mediaStageClassifier = DesktopStageSubtreeClassifier { widget ->
        val tokens = widget.cachedAllClassTokens
        if (tokens.isNotEmpty() && tokens.any { it in MEDIA_STAGE_TOKENS }) {
            return@DesktopStageSubtreeClassifier true
        }
        val nativeCls = widget.nativeClassName
        if (
            nativeCls.equals("libvlc-video", ignoreCase = true) ||
            nativeCls.equals("SeekBar", ignoreCase = true)
        ) {
            return@DesktopStageSubtreeClassifier true
        }
        val htmlTag = widget.attributes["htmlTag"] as? String
        if (
            htmlTag != null && (
                htmlTag.equals("video", ignoreCase = true) ||
                    htmlTag.equals("genericui:video-overlay-host", ignoreCase = true) ||
                    htmlTag.equals("genericui:expanding-volume-slider", ignoreCase = true)
                )
        ) {
            return@DesktopStageSubtreeClassifier true
        }
        false
    }

    init {
        DesktopNativeWidget.registerStageSubtreeClassifier(mediaStageClassifier)
    }

    fun install(
        platformName: String = "Desktop"
    ) {
        DesktopNativeWidget.registerStageSubtreeClassifier(mediaStageClassifier)
        MediaExtension.registerProviders(
            videoPlayer = {
                DesktopLibVlcVideoPlayerImpl(
                    nativeWidget = DesktopNativeWidget(platformName, "libvlc-video"),
                    platformName = platformName
                )
            },
            audioPlayer = {
                DesktopLibVlcAudioPlayerImpl()
            },
            audioAnalyzer = { fftSize ->
                DesktopLibVlcAudioAnalyzerImpl(
                    initialFftSize = fftSize,
                    nativeWidget = DesktopNativeWidget(platformName, "libvlc-audio-analyzer")
                )
            },
            mediaSessionManager = {
                DesktopMediaSessionManagerNativeImpl(backendToolkit = platformName)
            },
            seekBar = {
                DesktopSeekBarNativeImpl(
                    nativeWidget = DesktopNativeWidget(platformName, "SeekBar")
                )
            }
        )
    }
}
