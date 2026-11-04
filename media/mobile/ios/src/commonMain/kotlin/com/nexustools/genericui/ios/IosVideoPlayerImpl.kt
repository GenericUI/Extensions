package com.nexustools.genericui.ios

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
 * iOS `AVPlayer` + `AVPlayerLayer` + `AVPictureInPictureController` implementation of [VideoPlayerNativeImpl].
 */
class IosVideoPlayerImpl : IosBaseNativeImpl("AVPlayerView", "video-player"), VideoPlayerNativeImpl<IosNativeView> {
    private var currentTimeSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var playing: Boolean = false
    private var speed: Float = 1.0f
    private var volumeLevel: Float = 1.0f
    private var mutedFlag: Boolean = false
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private var antialiasingEnabled: Boolean = true
    private var mediaReadyFlag: Boolean = false
    private var pipActive: Boolean = false
    private var bufferedRangesState: List<Pair<Double, Double>> = emptyList()
    private val audioTracks = mutableListOf<MediaTrackInfo>()
    private val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
    private var activeSubtitleTrackId: String? = null
    private var attachedAnalyzer: AudioAnalyzer? = null

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onBufferingChangedListener: ((Boolean) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPlayingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onBufferingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onBufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onTracksChangedListener: (() -> Unit)? = null
    private var onMediaReadyListener: (() -> Unit)? = null

    init {
        nativeWidget.metadata["antialiasing"] = "true"
    }

    override fun getMediaCapabilities(): MediaCapabilitiesReport = MediaCapabilitiesReport(
        videoCodecs = mapOf(
            VideoCodec.H264 to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.HEVC to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.VP9 to CodecSupportLevel.HARDWARE_ACCELERATED,
            VideoCodec.AV1 to CodecSupportLevel.HARDWARE_ACCELERATED
        ),
        audioCodecs = mapOf(
            AudioCodec.AAC to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.MP3 to CodecSupportLevel.SUPPORTED,
            AudioCodec.ALAC to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.FLAC to CodecSupportLevel.SUPPORTED,
            AudioCodec.AC3 to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.EAC3 to CodecSupportLevel.HARDWARE_ACCELERATED,
            AudioCodec.OPUS to CodecSupportLevel.SUPPORTED,
            AudioCodec.PCM to CodecSupportLevel.SUPPORTED
        ),
        containers = setOf(MediaContainer.MP4, MediaContainer.MOV, MediaContainer.HLS),
        maxWidth = 3840,
        maxHeight = 2160,
        maxFrameRate = 120,
        supportsHdr10 = true,
        supportsHlg = true,
        supportsDolbyVision = true,
        supportsSurround51 = true,
        supportsSurround71 = true,
        supportsAudioTrackSelection = true,
        supportsSubtitleTrackSelection = true,
        hardwareAccelerationBackend = "VideoToolbox / AVFoundation"
    )

    override fun supportsAudioTrackSelection(): Boolean = true
    override fun supportsSubtitleTrackSelection(): Boolean = true

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        mediaReadyFlag = url.isNotBlank()
        if (durationOverrideSeconds != null) {
            durationSec = durationOverrideSeconds
            onDurationChangedListener?.invoke(durationSec)
        }
        nativeWidget.metadata["avPlayerUrl"] = url
        if (mediaReadyFlag) {
            onMediaReadyListener?.invoke()
        }
        updateBufferedHorizon(currentTimeSec)
    }

    override fun play() {
        playing = true
        onPlayingStateChangedListener?.invoke(true)
        updateBufferedHorizon(currentTimeSec)
    }

    override fun pause() {
        playing = false
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
        onPlayingStateChangedListener?.invoke(false)
        onTimeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        currentTimeSec = seconds.coerceAtLeast(0.0)
        onTimeUpdateListener?.invoke(currentTimeSec)
        updateBufferedHorizon(currentTimeSec)
    }

    override fun getCurrentTimeSeconds(): Double = currentTimeSec
    override fun getDurationSeconds(): Double = durationSec
    override fun isPlaying(): Boolean = playing

    override fun setPlaybackSpeed(speed: Float) { this.speed = speed.coerceIn(0.25f, 4.0f) }
    override fun getPlaybackSpeed(): Float = speed

    override fun setVolume(volume: Float) { volumeLevel = volume.coerceIn(0f, 1f) }
    override fun getVolume(): Float = volumeLevel

    override fun setMuted(muted: Boolean) { mutedFlag = muted }
    override fun isMuted(): Boolean = mutedFlag

    override fun setResizeMode(mode: VideoResizeMode) { resizeMode = mode }
    override fun getResizeMode(): VideoResizeMode = resizeMode

    override fun getAudioTracks(): List<MediaTrackInfo> = audioTracks.toList()
    override fun selectAudioTrack(trackId: String): Boolean {
        val indexPrefix = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        for (i in audioTracks.indices) {
            val item = audioTracks[i]
            val selected = if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            audioTracks[i] = item.copy(isSelected = selected)
        }
        onTracksChangedListener?.invoke()
        return true
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracks.toList()

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val id = "sub-${subtitleTracks.size}"
        subtitleTracks.add(SubtitleTrackInfo(id, label, language, externalUrl = vttOrSrtUrl, isSelected = activeSubtitleTrackId == id))
        onTracksChangedListener?.invoke()
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean {
        val indexPrefix = if (trackId != null && trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        activeSubtitleTrackId = if (indexPrefix != null) subtitleTracks.getOrNull(indexPrefix)?.id else trackId
        for (i in subtitleTracks.indices) {
            val item = subtitleTracks[i]
            val selected = if (trackId == null) false else if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            subtitleTracks[i] = item.copy(isSelected = selected)
        }
        onTracksChangedListener?.invoke()
        return true
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
    override fun setOnEnded(listener: ((() -> Unit))?) { onEndedListener = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { onBufferingChangedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }

    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { onPlayingStateChangedListener = listener }
    override fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) { onBufferingStateChangedListener = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) { onBufferedRangesChangedListener = listener }
    override fun setOnTracksChanged(listener: (() -> Unit)?) { onTracksChangedListener = listener }
    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesState

    override fun setAntialiasing(antialiasing: Boolean) {
        antialiasingEnabled = antialiasing
        nativeWidget.metadata["antialiasing"] = antialiasing.toString()
    }

    override fun isAntialiasing(): Boolean = antialiasingEnabled

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean = mediaReadyFlag

    private fun updateBufferedHorizon(currentSec: Double) {
        val bufEnd = if (durationSec > 0.0) minOf(durationSec, currentSec + 15.0) else currentSec + 15.0
        if (bufEnd > 0.0) {
            bufferedRangesState = listOf(0.0 to bufEnd)
            onBufferedRangesChangedListener?.invoke(bufferedRangesState)
        }
    }
}
