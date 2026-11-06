package com.nexustools.genericui.swing

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
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing [VideoPlayerNativeImpl] backed by `libvlcj` ([CallbackMediaPlayerComponent] / [MediaPlayerFactory]).
 */
class SwingVideoPlayerImpl : SwingBaseNativeImpl(
    object : JPanel(BorderLayout()) {
        override fun paint(g: Graphics?) {
            val g2d = g as? Graphics2D
            if (g2d != null) {
                val aa = getClientProperty("antialiasing") as? Boolean ?: true
                if (aa) {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                } else {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
                }
            }
            super.paint(g)
        }
    },
    "vlcj-video-player"
), VideoPlayerNativeImpl<JComponent> {
    override val nativeWidget: JPanel = super.nativeWidget as JPanel

    private var antialiasingEnabled: Boolean = true
    private var mediaReadyFlag: Boolean = false
    private var onMediaReadyListener: (() -> Unit)? = null

    private val vlcjComponent: CallbackMediaPlayerComponent? = runCatching {
        CallbackMediaPlayerComponent()
    }.getOrNull()

    private val vlcjPlayer: MediaPlayer?
        get() = vlcjComponent?.mediaPlayer()

    private var currentSecFallback: Double = 0.0
    private var durationSecOverride: Double? = null
    private var playingFallback: Boolean = false
    private var speedVal: Float = 1.0f
    private var volumeVal: Float = 1.0f
    private var mutedFlag: Boolean = false
    private var resizeModeVal: VideoResizeMode = VideoResizeMode.FIT
    private var bufferedRangesState: List<Pair<Double, Double>> = emptyList()
    private val subtitles = mutableListOf<SubtitleTrackInfo>()

    private var timeUpdateListener: ((Double) -> Unit)? = null
    private var durationChangedListener: ((Double) -> Unit)? = null
    private var endedListener: (() -> Unit)? = null
    private var bufferingListener: ((Boolean) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var playingStateChangedListener: ((Boolean) -> Unit)? = null
    private var bufferingStateChangedListener: ((Boolean) -> Unit)? = null
    private var bufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var tracksChangedListener: (() -> Unit)? = null

    init {
        nativeWidget.putClientProperty("antialiasing", true)
        if (vlcjComponent != null) {
            nativeWidget.add(vlcjComponent, BorderLayout.CENTER)
            vlcjComponent.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    playingFallback = true
                    if (!mediaReadyFlag) {
                        mediaReadyFlag = true
                        onMediaReadyListener?.invoke()
                    }
                    playingStateChangedListener?.invoke(true)
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    playingStateChangedListener?.invoke(false)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    playingStateChangedListener?.invoke(false)
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    val sec = newTime / 1000.0
                    currentSecFallback = sec
                    timeUpdateListener?.invoke(sec)
                    updateBufferedHorizon(sec)
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    if (newLength > 0L && !mediaReadyFlag) {
                        mediaReadyFlag = true
                        onMediaReadyListener?.invoke()
                    }
                    if (durationSecOverride == null && newLength > 0L) {
                        durationChangedListener?.invoke(newLength / 1000.0)
                    }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    playingStateChangedListener?.invoke(false)
                    endedListener?.invoke()
                }

                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    val isBuf = newCache < 100f
                    bufferingListener?.invoke(isBuf)
                    bufferingStateChangedListener?.invoke(isBuf)
                    val dur = getDurationSeconds()
                    if (dur > 0.0) {
                        val endSec = dur * (newCache.coerceIn(0f, 100f) / 100.0)
                        bufferedRangesState = listOf(0.0 to endSec)
                        bufferedRangesChangedListener?.invoke(bufferedRangesState)
                    }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    playingStateChangedListener?.invoke(false)
                    errorListener?.invoke("vlcj playback error")
                }
            })
        }
    }

    override fun getMediaCapabilities(): MediaCapabilitiesReport = MediaCapabilitiesReport(
        videoCodecs = VideoCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
        audioCodecs = AudioCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
        containers = MediaContainer.entries.toSet(),
        supportsHdr10 = true,
        supportsDolbyVision = true,
        supportsHlg = true,
        supportsSurround51 = true,
        supportsSurround71 = true,
        supportsAudioTrackSelection = true,
        supportsSubtitleTrackSelection = true,
        hardwareAccelerationBackend = "libvlcj (${MediaPlayerFactory::class.java.packageName})"
    )

    override fun isVideoCodecSupported(codec: VideoCodec): Boolean = true
    override fun isAudioCodecSupported(codec: AudioCodec): Boolean = true
    override fun getSupportedVideoCodecs(): Set<VideoCodec> = VideoCodec.entries.toSet()
    override fun getSupportedAudioCodecs(): Set<AudioCodec> = AudioCodec.entries.toSet()

    override fun supportsAudioTrackSelection(): Boolean = true
    override fun supportsSubtitleTrackSelection(): Boolean = true

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        mediaReadyFlag = false
        nativeWidget.putClientProperty("videoUri", url)
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        durationSecOverride = durationOverrideSeconds

        val options = if (seekOffsetSeconds > 0.0) {
            arrayOf(":start-time=$seekOffsetSeconds")
        } else {
            emptyArray()
        }
        vlcjPlayer?.media()?.prepare(url, *options)
        if ((vlcjPlayer == null || durationOverrideSeconds != null) && url.isNotBlank()) {
            mediaReadyFlag = true
            onMediaReadyListener?.invoke()
        }

        if (durationOverrideSeconds != null) {
            durationChangedListener?.invoke(durationOverrideSeconds)
        }
        updateBufferedHorizon(currentSecFallback)
    }

    override fun play() {
        playingFallback = true
        vlcjPlayer?.controls()?.play()
        playingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        playingFallback = false
        vlcjPlayer?.controls()?.setPause(true)
        playingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playingFallback = false
        currentSecFallback = 0.0
        vlcjPlayer?.controls()?.stop()
        playingStateChangedListener?.invoke(false)
        timeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        vlcjPlayer?.controls()?.setTime((clamped * 1000.0).toLong())
        timeUpdateListener?.invoke(clamped)
        updateBufferedHorizon(clamped)
    }

    override fun getCurrentTimeSeconds(): Double {
        val t = vlcjPlayer?.status()?.time() ?: -1L
        return if (t >= 0L) t / 1000.0 else currentSecFallback
    }

    override fun getDurationSeconds(): Double {
        durationSecOverride?.let { return it }
        val len = vlcjPlayer?.status()?.length() ?: -1L
        return if (len > 0L) len / 1000.0 else 0.0
    }

    override fun isPlaying(): Boolean =
        vlcjPlayer?.status()?.isPlaying ?: playingFallback

    override fun setPlaybackSpeed(speed: Float) {
        speedVal = speed.coerceIn(0.1f, 8.0f)
        vlcjPlayer?.controls()?.setRate(speedVal)
    }

    override fun getPlaybackSpeed(): Float =
        vlcjPlayer?.status()?.rate() ?: speedVal

    override fun setVolume(volume: Float) {
        volumeVal = volume.coerceIn(0f, 2f)
        vlcjPlayer?.audio()?.setVolume((volumeVal * 100f).toInt())
    }

    override fun getVolume(): Float = volumeVal

    override fun setMuted(muted: Boolean) {
        mutedFlag = muted
        vlcjPlayer?.audio()?.isMute = muted
    }

    override fun isMuted(): Boolean =
        vlcjPlayer?.audio()?.isMute ?: mutedFlag

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeModeVal = mode
        when (mode) {
            VideoResizeMode.FIT -> {
                vlcjPlayer?.video()?.setAspectRatio(null)
                vlcjPlayer?.video()?.setScale(0f)
            }
            VideoResizeMode.FILL -> {
                vlcjPlayer?.video()?.setScale(0f)
            }
            VideoResizeMode.STRETCH -> {
                val w = nativeWidget.width.coerceAtLeast(16)
                val h = nativeWidget.height.coerceAtLeast(9)
                vlcjPlayer?.video()?.setAspectRatio("$w:$h")
            }
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeModeVal

    override fun getAudioTracks(): List<MediaTrackInfo> {
        val descriptions = vlcjPlayer?.audio()?.trackDescriptions()?.filter { it.id() != -1 } ?: return emptyList()
        val currentTrack = vlcjPlayer?.audio()?.track() ?: -1
        return descriptions.map { desc ->
            MediaTrackInfo(
                id = desc.id().toString(),
                label = desc.description() ?: "Track ${desc.id()}",
                language = "und",
                isSelected = desc.id() == currentTrack
            )
        }
    }

    override fun selectAudioTrack(trackId: String): Boolean {
        val descriptions = vlcjPlayer?.audio()?.trackDescriptions()?.filter { it.id() != -1 }.orEmpty()
        val resolvedId: Int? = if (trackId.startsWith("index:")) {
            val idx = trackId.removePrefix("index:").toIntOrNull()
            if (idx != null) descriptions.getOrNull(idx)?.id() else null
        } else {
            val id = trackId.toIntOrNull()
            if (id != null && descriptions.any { it.id() == id }) id
            else if (id != null) descriptions.getOrNull(id)?.id() ?: id
            else null
        }
        if (resolvedId != null) {
            vlcjPlayer?.audio()?.setTrack(resolvedId)
        }
        tracksChangedListener?.invoke()
        return true
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val currentSpu = vlcjPlayer?.subpictures()?.track() ?: -1
        val vlcSubs = vlcjPlayer?.subpictures()?.trackDescriptions()?.filter { it.id() != -1 }?.map { desc ->
            SubtitleTrackInfo(
                id = desc.id().toString(),
                label = desc.description() ?: "Subtitle ${desc.id()}",
                language = "und",
                isSelected = desc.id() == currentSpu
            )
        }.orEmpty()
        return if (vlcSubs.isNotEmpty()) vlcSubs else subtitles.toList()
    }

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val id = "sub_${subtitles.size}"
        for (i in subtitles.indices) {
            subtitles[i] = subtitles[i].copy(isSelected = false)
        }
        subtitles.add(SubtitleTrackInfo(id, label, language, externalUrl = vttOrSrtUrl, isSelected = true))
        vlcjPlayer?.media()?.addSlave(MediaSlaveType.SUBTITLE, vttOrSrtUrl, true)
        tracksChangedListener?.invoke()
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean {
        val indexPrefix = if (trackId != null && trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        for (i in subtitles.indices) {
            val item = subtitles[i]
            val selected = if (trackId == null) false else if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            subtitles[i] = item.copy(isSelected = selected)
        }
        val descriptions = vlcjPlayer?.subpictures()?.trackDescriptions()?.filter { it.id() != -1 }.orEmpty()
        val id = if (trackId == null) {
            -1
        } else if (indexPrefix != null) {
            descriptions.getOrNull(indexPrefix)?.id() ?: -1
        } else {
            trackId.toIntOrNull() ?: -1
        }
        vlcjPlayer?.subpictures()?.setTrack(id)
        tracksChangedListener?.invoke()
        return true
    }

    override fun supportsPictureInPicture(): Boolean = false
    override fun enterPictureInPicture(): Boolean = false
    override fun exitPictureInPicture() {}

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {}

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { timeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { durationChangedListener = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { endedListener = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { bufferingListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { errorListener = listener }

    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { playingStateChangedListener = listener }
    override fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) { bufferingStateChangedListener = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) { bufferedRangesChangedListener = listener }
    override fun setOnTracksChanged(listener: (() -> Unit)?) { tracksChangedListener = listener }
    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesState

    override fun setAntialiasing(antialiasing: Boolean) {
        antialiasingEnabled = antialiasing
        nativeWidget.putClientProperty("antialiasing", antialiasing)
        nativeWidget.repaint()
    }

    override fun isAntialiasing(): Boolean = antialiasingEnabled

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean = mediaReadyFlag || getDurationSeconds() > 0.0

    private fun updateBufferedHorizon(currentSec: Double) {
        val dur = getDurationSeconds()
        val endSec = if (dur > 0.0) minOf(dur, currentSec + 15.0) else currentSec + 15.0
        if (endSec > 0.0) {
            bufferedRangesState = listOf(0.0 to endSec)
            bufferedRangesChangedListener?.invoke(bufferedRangesState)
        }
    }

    override fun dispose() {
        vlcjComponent?.release()
        super.dispose()
    }
}
