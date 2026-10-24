package com.nexustools.genericui.web

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
 * Web HTML5 `<video>` + MSE/HLS implementation of [VideoPlayerNativeImpl].
 */
class WebVideoPlayerImpl : WebBaseNativeImpl("video", "video-player"), VideoPlayerNativeImpl<WebDomElement> {
    private var sourceUrl: String = ""
    private var currentTimeSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var playing: Boolean = false
    private var speed: Float = 1.0f
    private var volumeLevel: Float = 1.0f
    private var mutedFlag: Boolean = false
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private var pipActive: Boolean = false
    private val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
    private var activeSubtitleTrackId: String? = null
    private var attachedAnalyzer: AudioAnalyzer? = null

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onBufferingChangedListener: ((Boolean) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    override fun getMediaCapabilities(): MediaCapabilitiesReport = MediaCapabilitiesReport(
        videoCodecs = mapOf(
            VideoCodec.H264 to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.HEVC to CodecSupportLevel.SUPPORTED,
            VideoCodec.VP8 to CodecSupportLevel.SUPPORTED,
            VideoCodec.VP9 to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.AV1 to CodecSupportLevel.HARDWARE_ACCELERATED
        ),
        audioCodecs = mapOf(
            AudioCodec.AAC to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.MP3 to CodecSupportLevel.SUPPORTED,
            AudioCodec.OPUS to CodecSupportLevel.SUPPORTED,
            AudioCodec.VORBIS to CodecSupportLevel.SUPPORTED,
            AudioCodec.FLAC to CodecSupportLevel.SUPPORTED
        ),
        containers = setOf(MediaContainer.MP4, MediaContainer.WEBM, MediaContainer.OGG, MediaContainer.HLS, MediaContainer.DASH),
        maxWidth = 3840,
        maxHeight = 2160,
        maxFrameRate = 60,
        supportsHdr10 = true,
        supportsHlg = true,
        supportsDolbyVision = false,
        supportsSurround51 = true,
        supportsSurround71 = false,
        hardwareAccelerationBackend = "WebCodecs / HTMLMediaElement MSE"
    )

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        sourceUrl = url
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        if (durationOverrideSeconds != null) {
            durationSec = durationOverrideSeconds
            onDurationChangedListener?.invoke(durationSec)
        }
        nativeWidget.setAttribute("src", url)
    }

    override fun play() {
        playing = true
    }

    override fun pause() {
        playing = false
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
    }

    override fun seekTo(seconds: Double) {
        currentTimeSec = seconds.coerceAtLeast(0.0)
        onTimeUpdateListener?.invoke(currentTimeSec)
    }

    override fun getCurrentTimeSeconds(): Double = currentTimeSec
    override fun getDurationSeconds(): Double = durationSec
    override fun isPlaying(): Boolean = playing

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.25f, 4.0f)
    }

    override fun getPlaybackSpeed(): Float = speed

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 1f)
    }

    override fun getVolume(): Float = volumeLevel

    override fun setMuted(muted: Boolean) {
        mutedFlag = muted
    }

    override fun isMuted(): Boolean = mutedFlag

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeMode = mode
        nativeWidget.style["object-fit"] = when (mode) {
            VideoResizeMode.FIT -> "contain"
            VideoResizeMode.FILL -> "cover"
            VideoResizeMode.STRETCH -> "fill"
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeMode

    override fun getAudioTracks(): List<MediaTrackInfo> = emptyList()
    override fun selectAudioTrack(trackId: String) {}

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracks.toList()

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val id = "sub-${subtitleTracks.size}"
        subtitleTracks.add(SubtitleTrackInfo(id = id, label = label, language = language, externalUrl = vttOrSrtUrl))
    }

    override fun selectSubtitleTrack(trackId: String?) {
        activeSubtitleTrackId = trackId
    }

    override fun supportsPictureInPicture(): Boolean = true

    override fun enterPictureInPicture(): Boolean {
        pipActive = true
        return true
    }

    override fun exitPictureInPicture() {
        pipActive = false
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChangedListener = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEndedListener = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { onBufferingChangedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }
}
