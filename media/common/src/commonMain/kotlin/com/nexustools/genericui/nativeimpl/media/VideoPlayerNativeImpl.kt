package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.media.VideoResizeMode

/**
 * Native implementation contract for [com.nexustools.genericui.component.media.VideoPlayer].
 * Provides uniform codec/container/4K/HDR/5.1 capability reporting ([getMediaCapabilities]) across all 8 backends.
 */
interface VideoPlayerNativeImpl<out W> : NativeImpl<W> {
    fun getMediaCapabilities(): MediaCapabilitiesReport

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

    fun getAudioTracks(): List<MediaTrackInfo>
    fun selectAudioTrack(trackId: String)

    fun getSubtitleTracks(): List<SubtitleTrackInfo>
    fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String)
    fun selectSubtitleTrack(trackId: String?)

    fun supportsPictureInPicture(): Boolean
    fun enterPictureInPicture(): Boolean
    fun exitPictureInPicture()

    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?)

    fun setOnTimeUpdate(listener: ((Double) -> Unit)?)
    fun setOnDurationChanged(listener: ((Double) -> Unit)?)
    fun setOnEnded(listener: (() -> Unit)?)
    fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?)
    fun setOnError(listener: ((String) -> Unit)?)
}
