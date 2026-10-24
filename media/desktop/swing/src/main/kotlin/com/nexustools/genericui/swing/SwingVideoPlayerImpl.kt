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
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing [VideoPlayerNativeImpl] backed by `libvlcj` ([CallbackMediaPlayerComponent] / [MediaPlayerFactory]).
 */
class SwingVideoPlayerImpl : SwingBaseNativeImpl(JPanel(BorderLayout()), "vlcj-video-player"), VideoPlayerNativeImpl<JComponent> {
    override val nativeWidget: JPanel = super.nativeWidget as JPanel

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
    private val subtitles = mutableListOf<SubtitleTrackInfo>()

    private var timeUpdateListener: ((Double) -> Unit)? = null
    private var durationChangedListener: ((Double) -> Unit)? = null
    private var endedListener: (() -> Unit)? = null
    private var bufferingListener: ((Boolean) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null

    init {
        if (vlcjComponent != null) {
            nativeWidget.add(vlcjComponent, BorderLayout.CENTER)
            vlcjComponent.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    val sec = newTime / 1000.0
                    currentSecFallback = sec
                    timeUpdateListener?.invoke(sec)
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    if (durationSecOverride == null && newLength > 0L) {
                        durationChangedListener?.invoke(newLength / 1000.0)
                    }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    endedListener?.invoke()
                }

                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    bufferingListener?.invoke(newCache < 100f)
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    playingFallback = false
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
        hardwareAccelerationBackend = "libvlcj (${MediaPlayerFactory::class.java.packageName})"
    )

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        nativeWidget.putClientProperty("videoUri", url)
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        durationSecOverride = durationOverrideSeconds

        val options = if (seekOffsetSeconds > 0.0) {
            arrayOf(":start-time=$seekOffsetSeconds")
        } else {
            emptyArray()
        }
        vlcjPlayer?.media()?.prepare(url, *options)

        if (durationOverrideSeconds != null) {
            durationChangedListener?.invoke(durationOverrideSeconds)
        }
    }

    override fun play() {
        playingFallback = true
        vlcjPlayer?.controls()?.play()
    }

    override fun pause() {
        playingFallback = false
        vlcjPlayer?.controls()?.setPause(true)
    }

    override fun stop() {
        playingFallback = false
        currentSecFallback = 0.0
        vlcjPlayer?.controls()?.stop()
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        vlcjPlayer?.controls()?.setTime((clamped * 1000.0).toLong())
        timeUpdateListener?.invoke(clamped)
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
        val descriptions = vlcjPlayer?.audio()?.trackDescriptions() ?: return emptyList()
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

    override fun selectAudioTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        vlcjPlayer?.audio()?.setTrack(id)
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val vlcSubs = vlcjPlayer?.subpictures()?.trackDescriptions()?.map { desc ->
            SubtitleTrackInfo(
                id = desc.id().toString(),
                label = desc.description() ?: "Subtitle ${desc.id()}",
                language = "und"
            )
        }.orEmpty()
        return if (vlcSubs.isNotEmpty()) vlcSubs else subtitles.toList()
    }

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        subtitles.add(SubtitleTrackInfo("sub_${subtitles.size}", label, language, externalUrl = vttOrSrtUrl))
        vlcjPlayer?.media()?.addSlave(MediaSlaveType.SUBTITLE, vttOrSrtUrl, true)
    }

    override fun selectSubtitleTrack(trackId: String?) {
        val id = trackId?.toIntOrNull() ?: -1
        vlcjPlayer?.subpictures()?.setTrack(id)
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

    override fun dispose() {
        vlcjComponent?.release()
        super.dispose()
    }
}
