package com.nexustools.genericui.ext.media

import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
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
    private var audioPlayerProvider: (() -> AudioPlayerNativeImpl)? = null
    private var seekBarProvider: (() -> SeekBarNativeImpl<*>)? = null
    private var audioAnalyzerProvider: ((Int) -> AudioAnalyzerNativeImpl)? = null
    private var mediaSessionManagerProvider: (() -> MediaSessionManagerNativeImpl)? = null

    fun registerProviders(
        videoPlayer: (() -> VideoPlayerNativeImpl<*>)? = null,
        audioPlayer: (() -> AudioPlayerNativeImpl)? = null,
        audioAnalyzer: ((Int) -> AudioAnalyzerNativeImpl)? = null,
        mediaSessionManager: (() -> MediaSessionManagerNativeImpl)? = null,
        seekBar: (() -> SeekBarNativeImpl<*>)? = null
    ) {
        if (videoPlayer != null) videoPlayerProvider = videoPlayer
        if (audioPlayer != null) audioPlayerProvider = audioPlayer
        if (audioAnalyzer != null) audioAnalyzerProvider = audioAnalyzer
        if (mediaSessionManager != null) mediaSessionManagerProvider = mediaSessionManager
        if (seekBar != null) seekBarProvider = seekBar
    }

    fun createVideoPlayerNative(): VideoPlayerNativeImpl<*>? = videoPlayerProvider?.invoke()

    fun createVideoPlayerNativeOrFallback(): VideoPlayerNativeImpl<*> =
        videoPlayerProvider?.invoke()
            ?: throw IllegalStateException("No VideoPlayerNativeImpl provider registered in MediaExtension")

    fun createAudioPlayerNative(): AudioPlayerNativeImpl? = audioPlayerProvider?.invoke()

    fun createAudioPlayerNativeOrFallback(): AudioPlayerNativeImpl =
        audioPlayerProvider?.invoke()
            ?: throw IllegalStateException("No AudioPlayerNativeImpl provider registered in MediaExtension")

    fun createSeekBarNative(): SeekBarNativeImpl<*>? = seekBarProvider?.invoke()

    fun createSeekBarNativeOrFallback(): SeekBarNativeImpl<*> =
        seekBarProvider?.invoke()
            ?: throw IllegalStateException("No SeekBarNativeImpl provider registered in MediaExtension")

    fun createAudioAnalyzerNative(fftSize: Int = 2048): AudioAnalyzerNativeImpl =
        audioAnalyzerProvider?.invoke(fftSize)
            ?: throw IllegalStateException("No AudioAnalyzerNativeImpl provider registered in MediaExtension")

    fun createMediaSessionManagerNative(): MediaSessionManagerNativeImpl? =
        mediaSessionManagerProvider?.invoke()

    /**
     * Checks whether the given [VideoCodec] is supported on the active media backend.
     * Desktop backends (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing` via VLC) always return `true`.
     * Web backend queries `<video>.canPlayType(...)` for each codec's standard MIME/codec probes.
     * Mobile backends (`:android`, `:ios`) query the native OS media codec registries.
     */
    fun isVideoCodecSupported(codec: VideoCodec): Boolean =
        createVideoPlayerNative()?.isVideoCodecSupported(codec) ?: false

    /**
     * Checks whether the given [AudioCodec] is supported on the active media backend.
     * Desktop backends (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing` via VLC) always return `true`.
     * Web backend queries `<video>.canPlayType(...)` / `<audio>.canPlayType(...)` for each codec's standard MIME/codec probes.
     * Mobile backends (`:android`, `:ios`) query the native OS media codec registries.
     */
    fun isAudioCodecSupported(codec: AudioCodec): Boolean {
        val videoImpl = createVideoPlayerNative()
        if (videoImpl != null) {
            return videoImpl.isAudioCodecSupported(codec)
        }
        return createAudioPlayerNative()?.isAudioCodecSupported(codec) ?: false
    }

    /**
     * Returns the set of all [VideoCodec]s supported on the active media backend.
     */
    fun getSupportedVideoCodecs(): Set<VideoCodec> =
        createVideoPlayerNative()?.getSupportedVideoCodecs() ?: emptySet()

    /**
     * Returns the set of all [AudioCodec]s supported on the active media backend.
     */
    fun getSupportedAudioCodecs(): Set<AudioCodec> {
        val videoImpl = createVideoPlayerNative()
        if (videoImpl != null) {
            return videoImpl.getSupportedAudioCodecs()
        }
        return createAudioPlayerNative()?.getSupportedAudioCodecs() ?: emptySet()
    }
}
