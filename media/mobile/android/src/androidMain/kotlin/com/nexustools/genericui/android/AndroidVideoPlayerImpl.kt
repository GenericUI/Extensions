package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl

/**
 * Android `androidx.media3.exoplayer.ExoPlayer` / `android.widget.VideoView` implementation of [VideoPlayerNativeImpl].
 */
class AndroidVideoPlayerImpl : AndroidBaseNativeImpl("android.widget.VideoView", "video-player"), VideoPlayerNativeImpl<AndroidViewHandle> {
    private var sourceUrl: String = ""
    private var currentPositionSeconds: Double = 0.0
    private var durationSeconds: Double = 180.0
    private var playing: Boolean = false
    private var volume: Float = 1f
    private var muted: Boolean = false
    private var playbackSpeed: Float = 1f
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private val subtitles = mutableListOf<SubtitleTrackInfo>()
    private var onTimeUpdate: ((Double) -> Unit)? = null
    private var onDurationChanged: ((Double) -> Unit)? = null
    private var onEnded: (() -> Unit)? = null
    private var onBufferingChanged: ((Boolean) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun getMediaCapabilities(): MediaCapabilitiesReport = MediaCapabilitiesReport(
        videoCodecs = mapOf(
            VideoCodec.H264 to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.HEVC to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.VP8 to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.VP9 to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.AV1 to CodecSupportLevel.HARDWARE_ACCELERATED
        ),
        audioCodecs = mapOf(
            AudioCodec.AAC to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.MP3 to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.OPUS to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.VORBIS to CodecSupportLevel.SOFTWARE_ONLY,
            AudioCodec.FLAC to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.PCM to CodecSupportLevel.HARDWARE_ACCELERATED
        ),
        containers = setOf(MediaContainer.MP4, MediaContainer.MKV, MediaContainer.WEBM, MediaContainer.HLS, MediaContainer.DASH),
        supportsHdr10 = true,
        supportsHlg = true,
        supportsDolbyVision = true,
        hardwareAccelerationBackend = "Android MediaCodec / ExoPlayer"
    )

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        sourceUrl = url
        currentPositionSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        nativeWidget.attributes["src"] = url
        if (durationOverrideSeconds != null) {
            durationSeconds = durationOverrideSeconds
            onDurationChanged?.invoke(durationSeconds)
        }
    }

    override fun play() { playing = true }
    override fun pause() { playing = false }
    override fun stop() {
        playing = false
        currentPositionSeconds = 0.0
    }

    override fun seekTo(seconds: Double) {
        currentPositionSeconds = seconds.coerceAtLeast(0.0)
        onTimeUpdate?.invoke(currentPositionSeconds)
    }

    override fun getCurrentTimeSeconds(): Double = currentPositionSeconds
    override fun getDurationSeconds(): Double = durationSeconds
    override fun isPlaying(): Boolean = playing

    override fun setPlaybackSpeed(speed: Float) { this.playbackSpeed = speed.coerceIn(0.25f, 4f) }
    override fun getPlaybackSpeed(): Float = playbackSpeed
    override fun setVolume(volume: Float) { this.volume = volume.coerceIn(0f, 1f) }
    override fun getVolume(): Float = volume
    override fun setMuted(muted: Boolean) { this.muted = muted }
    override fun isMuted(): Boolean = muted
    override fun setResizeMode(mode: VideoResizeMode) { this.resizeMode = mode }
    override fun getResizeMode(): VideoResizeMode = resizeMode

    override fun getAudioTracks(): List<MediaTrackInfo> = emptyList()
    override fun selectAudioTrack(trackId: String) {}

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitles.toList()
    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        subtitles.add(SubtitleTrackInfo("sub_${subtitles.size}", label, language, externalUrl = vttOrSrtUrl))
    }
    override fun selectSubtitleTrack(trackId: String?) {}

    override fun supportsPictureInPicture(): Boolean = true
    override fun enterPictureInPicture(): Boolean = true
    override fun exitPictureInPicture() {}

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {}

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdate = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChanged = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEnded = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { onBufferingChanged = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onError = listener }
}
