package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode

/**
 * Native implementation contract for [com.nexustools.genericui.component.media.VideoPlayer].
 * Provides uniform codec/container/4K/HDR/5.1 capability reporting ([getMediaCapabilities]) across all 8 backends.
 */
interface VideoPlayerNativeImpl<out W> : NativeImpl<W> {
    fun getMediaCapabilities(): MediaCapabilitiesReport

    fun isVideoCodecSupported(codec: VideoCodec): Boolean {
        val level = getMediaCapabilities().videoCodecs[codec] ?: CodecSupportLevel.UNSUPPORTED
        return level != CodecSupportLevel.UNSUPPORTED
    }

    fun isAudioCodecSupported(codec: AudioCodec): Boolean {
        val level = getMediaCapabilities().audioCodecs[codec] ?: CodecSupportLevel.UNSUPPORTED
        return level != CodecSupportLevel.UNSUPPORTED
    }

    fun getSupportedVideoCodecs(): Set<VideoCodec> =
        VideoCodec.entries.filterTo(linkedSetOf()) { isVideoCodecSupported(it) }

    fun getSupportedAudioCodecs(): Set<AudioCodec> =
        AudioCodec.entries.filterTo(linkedSetOf()) { isAudioCodecSupported(it) }

    fun setSource(url: String, seekOffsetSeconds: Double = 0.0, durationOverrideSeconds: Double? = null)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(seconds: Double)

    fun getCurrentTimeSeconds(): Double
    fun getDurationSeconds(): Double
    fun isPlaying(): Boolean

    fun setPlaybackSpeed(speed: Float)
    fun getPlaybackSpeed(): Float

    fun setVolume(volume: Float)
    fun getVolume(): Float

    fun setMuted(muted: Boolean)
    fun isMuted(): Boolean

    fun setResizeMode(mode: VideoResizeMode)
    fun getResizeMode(): VideoResizeMode

    fun supportsAudioTrackSelection(): Boolean = getMediaCapabilities().supportsAudioTrackSelection
    fun supportsSubtitleTrackSelection(): Boolean = getMediaCapabilities().supportsSubtitleTrackSelection

    fun getAudioTracks(): List<MediaTrackInfo>
    fun getSelectedAudioTrackId(): String? = getAudioTracks().firstOrNull { it.isSelected }?.id
    fun selectAudioTrack(trackId: String): Boolean

    fun getSubtitleTracks(): List<SubtitleTrackInfo>
    fun getSelectedSubtitleTrackId(): String? = getSubtitleTracks().firstOrNull { it.isSelected }?.id
    fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String)
    fun selectSubtitleTrack(trackId: String?): Boolean

    fun supportsPictureInPicture(): Boolean
    fun enterPictureInPicture(): Boolean
    fun exitPictureInPicture()

    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?)

    fun setOnTimeUpdate(listener: ((Double) -> Unit)?)
    fun setOnDurationChanged(listener: ((Double) -> Unit)?)
    fun setOnEnded(listener: (() -> Unit)?)
    fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?)
    fun setOnError(listener: ((String) -> Unit)?)

    fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) {}
    fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) {}
    fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {}
    fun setOnTracksChanged(listener: (() -> Unit)?) {}

    fun setPreloadMode(mode: MediaPreloadMode) {}
    fun getPreloadMode(): MediaPreloadMode = MediaPreloadMode.AUTO
    fun setAdaptivePreloadBufferingEnabled(enabled: Boolean) {}
    fun isAdaptivePreloadBufferingEnabled(): Boolean = true
    fun getBufferedRanges(): List<Pair<Double, Double>> = emptyList()
    fun getEstimatedNetworkSpeedRatio(): Double = 1.0
    fun getRequiredSafeBufferSeconds(): Double = 0.0

    fun setAntialiasing(antialiasing: Boolean) {}
    fun isAntialiasing(): Boolean = true

    fun setOnMediaReady(listener: (() -> Unit)?) {}
    fun isMediaReady(): Boolean = false

    fun getPresentedFrameCount(): Long = 0L
    fun setOnBeforeFramePresent(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {}

    fun setInternalSubtitlesEnabled(enabled: Boolean) {}
    fun isInternalSubtitlesEnabled(): Boolean = true
}
