package com.nexustools.genericui.ext.media

import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Extension coordinator for `:extensions:media`.
 *
 * `:extensions:media` is completely self-contained and does not rely on any other extensions:
 * - Native desktop targets (`:windows`, `:macos`, `:gtk`, `:qt`) use native `libvlc`.
 * - JVM `:swing` uses `libvlcj` (`uk.co.caprica.vlcj`).
 * - Mobile targets (`:android`, `:ios`) use native OS media components (`MediaPlayer` / `ExoPlayer` / `AudioRecord` and `AVPlayer`).
 * - `:web` uses native HTML5 `<video>`, `<audio>`, and WebAudio `AnalyserNode` components.
 */
object MediaExtension {
    private var videoPlayerProvider: (() -> VideoPlayerNativeImpl<*>)? = null
    private var audioPlayerProvider: (() -> AudioPlayerNativeImpl<*>)? = null
    private var audioAnalyzerProvider: ((Int) -> AudioAnalyzerNativeImpl)? = null
    private var mediaSessionManagerProvider: (() -> MediaSessionManagerNativeImpl)? = null

    fun registerProviders(
        videoPlayer: (() -> VideoPlayerNativeImpl<*>)? = null,
        audioPlayer: (() -> AudioPlayerNativeImpl<*>)? = null,
        audioAnalyzer: ((Int) -> AudioAnalyzerNativeImpl)? = null,
        mediaSessionManager: (() -> MediaSessionManagerNativeImpl)? = null
    ) {
        if (videoPlayer != null) videoPlayerProvider = videoPlayer
        if (audioPlayer != null) audioPlayerProvider = audioPlayer
        if (audioAnalyzer != null) audioAnalyzerProvider = audioAnalyzer
        if (mediaSessionManager != null) mediaSessionManagerProvider = mediaSessionManager
    }

    fun createVideoPlayerNative(): VideoPlayerNativeImpl<*>? = videoPlayerProvider?.invoke()

    fun createVideoPlayerNativeOrFallback(): VideoPlayerNativeImpl<*> =
        videoPlayerProvider?.invoke()
            ?: throw IllegalStateException("No VideoPlayerNativeImpl provider registered in MediaExtension")

    fun createAudioPlayerNative(): AudioPlayerNativeImpl<*>? = audioPlayerProvider?.invoke()

    fun createAudioPlayerNativeOrFallback(): AudioPlayerNativeImpl<*> =
        audioPlayerProvider?.invoke()
            ?: throw IllegalStateException("No AudioPlayerNativeImpl provider registered in MediaExtension")

    fun createAudioAnalyzerNative(fftSize: Int = 2048): AudioAnalyzerNativeImpl? =
        audioAnalyzerProvider?.invoke(fftSize)

    fun createMediaSessionManagerNative(): MediaSessionManagerNativeImpl? =
        mediaSessionManagerProvider?.invoke()
}
