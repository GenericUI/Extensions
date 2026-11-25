package com.nexustools.genericui.ext.media.mobile

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.media.MediaCodecSupportProvider
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.MediaPlayerSettingsProvider
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl

/**
 * Wires native mobile (`BackendTarget.Android`, `BackendTarget.IOS`, and `BackendTarget.IPadOS`)
 * media playback components into [MediaExtension].
 */
object MobileMediaExtensionProvider {
    fun supportsTarget(target: BackendTarget): Boolean =
        target == BackendTarget.Android || target.isIosFamily

    fun install(
        target: BackendTarget,
        videoPlayer: (() -> VideoPlayerNativeImpl<*>)? = null,
        audioPlayer: (() -> AudioPlayerNativeImpl)? = null,
        audioAnalyzer: ((Int) -> AudioAnalyzerNativeImpl)? = null,
        mediaSessionManager: (() -> MediaSessionManagerNativeImpl)? = null,
        screenWakeLock: (() -> ScreenWakeLockNativeImpl)? = null,
        codecSupportProvider: MediaCodecSupportProvider? = null,
        playerSettingsProvider: MediaPlayerSettingsProvider? = null
    ) {
        require(supportsTarget(target)) {
            "MobileMediaExtensionProvider supports mobile targets only (Android, IOS, IPadOS); got $target."
        }
        install(
            videoPlayer = videoPlayer,
            audioPlayer = audioPlayer,
            audioAnalyzer = audioAnalyzer,
            mediaSessionManager = mediaSessionManager,
            screenWakeLock = screenWakeLock,
            codecSupportProvider = codecSupportProvider,
            playerSettingsProvider = playerSettingsProvider
        )
    }

    fun install(
        videoPlayer: (() -> VideoPlayerNativeImpl<*>)? = null,
        audioPlayer: (() -> AudioPlayerNativeImpl)? = null,
        audioAnalyzer: ((Int) -> AudioAnalyzerNativeImpl)? = null,
        mediaSessionManager: (() -> MediaSessionManagerNativeImpl)? = null,
        screenWakeLock: (() -> ScreenWakeLockNativeImpl)? = null,
        codecSupportProvider: MediaCodecSupportProvider? = null,
        playerSettingsProvider: MediaPlayerSettingsProvider? = null
    ) {
        MediaExtension.registerProviders(
            videoPlayer = videoPlayer,
            audioPlayer = audioPlayer,
            audioAnalyzer = audioAnalyzer,
            mediaSessionManager = mediaSessionManager,
            screenWakeLock = screenWakeLock,
            codecSupportProvider = codecSupportProvider,
            playerSettingsProvider = playerSettingsProvider
        )
    }

    fun install(
        videoPlayerFactory: () -> VideoPlayerNativeImpl<*>,
        audioPlayerFactory: () -> AudioPlayerNativeImpl,
        audioAnalyzerFactory: () -> AudioAnalyzerNativeImpl,
        mediaSessionManagerFactory: () -> MediaSessionManagerNativeImpl,
        screenWakeLockFactory: (() -> ScreenWakeLockNativeImpl)? = null,
        codecSupportProvider: MediaCodecSupportProvider? = null,
        playerSettingsProvider: MediaPlayerSettingsProvider? = null
    ) {
        MediaExtension.registerProviders(
            videoPlayer = videoPlayerFactory,
            audioPlayer = audioPlayerFactory,
            audioAnalyzer = { fftSize ->
                audioAnalyzerFactory().also { it.fftSize = fftSize }
            },
            mediaSessionManager = mediaSessionManagerFactory,
            screenWakeLock = screenWakeLockFactory,
            codecSupportProvider = codecSupportProvider,
            playerSettingsProvider = playerSettingsProvider
        )
    }
}
