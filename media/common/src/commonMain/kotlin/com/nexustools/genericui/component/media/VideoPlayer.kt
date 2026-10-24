package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl

/**
 * Hardware-accelerated cross-platform video player component.
 *
 * On desktop backends (`:windows`, `:macos`, `:gtk`, `:qt`), powered by `:desktop`'s VLC video playback,
 * HDR10/HLG/Dolby Vision tone-mapping (using Vulkan shaders as primary and OpenGL ES shaders as fallback),
 * and hardware/software decoding engine (`DesktopVlcVideoPlayerImpl`).
 */
open class VideoPlayer(
    sourceUrl: String? = null
) : AbstractComponent() {
    var sourceUrl: String? = sourceUrl
        private set

    var seekOffsetSeconds: Double = 0.0
        private set

    var durationOverrideSeconds: Double? = null
        private set

    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2f)
            typedNative<VideoPlayerNativeImpl<*>>()?.setVolume(field)
        }

    var isMuted: Boolean = false
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setMuted(value)
        }

    var playbackSpeed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 8.0f)
            typedNative<VideoPlayerNativeImpl<*>>()?.setPlaybackSpeed(field)
        }

    var resizeMode: VideoResizeMode = VideoResizeMode.FIT
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setResizeMode(value)
        }

    val currentTimeSeconds: Double
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.getCurrentTimeSeconds() ?: seekOffsetSeconds

    val durationSeconds: Double
        get() = durationOverrideSeconds ?: typedNative<VideoPlayerNativeImpl<*>>()?.getDurationSeconds() ?: 0.0

    val isPlaying: Boolean
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.isPlaying() ?: false

    var onTimeUpdate: ((Double) -> Unit)? = null
    var onDurationChanged: ((Double) -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var attachedAnalyzer: AudioAnalyzer? = null

    /**
     * Queries the uniform codec, container, 4K, HDR10/HLG/Dolby Vision, and surround audio capabilities
     * of the active platform video engine.
     */
    fun getMediaCapabilities(): MediaCapabilitiesReport {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.getMediaCapabilities() ?: MediaCapabilitiesReport(
            videoCodecs = emptyMap(),
            audioCodecs = emptyMap(),
            containers = emptySet()
        )
    }

    fun setSource(url: String, seekOffsetSeconds: Double = 0.0, durationOverrideSeconds: Double? = null) {
        this.sourceUrl = url
        this.seekOffsetSeconds = seekOffsetSeconds
        this.durationOverrideSeconds = durationOverrideSeconds
        typedNative<VideoPlayerNativeImpl<*>>()?.setSource(url, seekOffsetSeconds, durationOverrideSeconds)
    }

    fun play() {
        typedNative<VideoPlayerNativeImpl<*>>()?.play()
    }

    fun pause() {
        typedNative<VideoPlayerNativeImpl<*>>()?.pause()
    }

    fun stop() {
        typedNative<VideoPlayerNativeImpl<*>>()?.stop()
    }

    fun seekTo(seconds: Double) {
        seekOffsetSeconds = seconds
        typedNative<VideoPlayerNativeImpl<*>>()?.seekTo(seconds)
    }

    fun getAudioTracks(): List<MediaTrackInfo> =
        typedNative<VideoPlayerNativeImpl<*>>()?.getAudioTracks() ?: emptyList()

    fun selectAudioTrack(trackId: String) {
        typedNative<VideoPlayerNativeImpl<*>>()?.selectAudioTrack(trackId)
    }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> =
        typedNative<VideoPlayerNativeImpl<*>>()?.getSubtitleTracks() ?: emptyList()

    fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        typedNative<VideoPlayerNativeImpl<*>>()?.addExternalSubtitleTrack(label, language, vttOrSrtUrl)
    }

    fun selectSubtitleTrack(trackId: String?) {
        typedNative<VideoPlayerNativeImpl<*>>()?.selectSubtitleTrack(trackId)
    }

    fun supportsPictureInPicture(): Boolean =
        typedNative<VideoPlayerNativeImpl<*>>()?.supportsPictureInPicture() ?: false

    fun enterPictureInPicture(): Boolean =
        typedNative<VideoPlayerNativeImpl<*>>()?.enterPictureInPicture() ?: false

    fun exitPictureInPicture() {
        typedNative<VideoPlayerNativeImpl<*>>()?.exitPictureInPicture()
    }

    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        typedNative<VideoPlayerNativeImpl<*>>()?.attachAudioAnalyzer(analyzer)
    }

    override fun createNativeImpl(factory: NativeFactory): VideoPlayerNativeImpl<*> =
        MediaExtension.createVideoPlayerNativeOrFallback()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<VideoPlayerNativeImpl<*>>() ?: return
        impl.setVolume(volume)
        impl.setMuted(isMuted)
        impl.setPlaybackSpeed(playbackSpeed)
        impl.setResizeMode(resizeMode)
        impl.setOnTimeUpdate(onTimeUpdate)
        impl.setOnDurationChanged(onDurationChanged)
        impl.setOnEnded(onEnded)
        impl.setOnBufferingChanged(onBufferingChanged)
        impl.setOnError(onError)
        attachedAnalyzer?.let { impl.attachAudioAnalyzer(it) }
        sourceUrl?.let { impl.setSource(it, seekOffsetSeconds, durationOverrideSeconds) }
    }
}
