package com.nexustools.genericui.ext.media.mobile

import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Wires native mobile (`:android` MediaPlayer/ExoPlayer/AudioRecord & `:ios` AVPlayer) media playback
 * components into [MediaExtension].
 */
object MobileMediaExtensionProvider {
    fun install(
        videoPlayer: (() -> VideoPlayerNativeImpl<*>)? = null,
        audioPlayer: (() -> AudioPlayerNativeImpl<*>)? = null,
        audioAnalyzer: ((Int) -> AudioAnalyzerNativeImpl)? = null,
        mediaSessionManager: (() -> MediaSessionManagerNativeImpl)? = null
    ) {
        MediaExtension.registerProviders(
            videoPlayer = videoPlayer,
            audioPlayer = audioPlayer,
            audioAnalyzer = audioAnalyzer,
            mediaSessionManager = mediaSessionManager
        )
    }

    fun install(
        videoPlayerFactory: () -> VideoPlayerNativeImpl<*>,
        audioPlayerFactory: () -> AudioPlayerNativeImpl<*>,
        audioAnalyzerFactory: () -> AudioAnalyzerNativeImpl,
        mediaSessionManagerFactory: () -> MediaSessionManagerNativeImpl
    ) {
        MediaExtension.registerProviders(
            videoPlayer = videoPlayerFactory,
            audioPlayer = audioPlayerFactory,
            audioAnalyzer = { fftSize ->
                audioAnalyzerFactory().also { it.fftSize = fftSize }
            },
            mediaSessionManager = mediaSessionManagerFactory
        )
    }
}
