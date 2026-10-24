package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing [AudioPlayerNativeImpl] backed by `libvlcj` ([AudioPlayerComponent]).
 */
class SwingAudioPlayerImpl : SwingBaseNativeImpl(JPanel(), "vlcj-audio-player"), AudioPlayerNativeImpl<JComponent> {
    override val nativeWidget: JPanel = super.nativeWidget as JPanel

    private val vlcjAudioComponent: AudioPlayerComponent? = runCatching {
        AudioPlayerComponent()
    }.getOrNull()

    private val vlcjPlayer: MediaPlayer?
        get() = vlcjAudioComponent?.mediaPlayer()

    private var currentSecFallback: Double = 0.0
    private var playingFallback: Boolean = false
    private var volumeVal: Float = 1.0f
    private var timeUpdateListener: ((Double) -> Unit)? = null
    private var endedListener: (() -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null

    init {
        vlcjAudioComponent?.mediaPlayer()?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                val sec = newTime / 1000.0
                currentSecFallback = sec
                timeUpdateListener?.invoke(sec)
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                playingFallback = false
                endedListener?.invoke()
            }

            override fun error(mediaPlayer: MediaPlayer) {
                playingFallback = false
                errorListener?.invoke("vlcj audio playback error")
            }
        })
    }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = AudioCodec.entries.toSet()

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        nativeWidget.putClientProperty("audioUri", url)
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        val options = if (seekOffsetSeconds > 0.0) {
            arrayOf(":start-time=$seekOffsetSeconds")
        } else {
            emptyArray()
        }
        vlcjPlayer?.media()?.prepare(url, *options)
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
        val len = vlcjPlayer?.status()?.length() ?: -1L
        return if (len > 0L) len / 1000.0 else 0.0
    }

    override fun isPlaying(): Boolean =
        vlcjPlayer?.status()?.isPlaying ?: playingFallback

    override fun setVolume(volume: Float) {
        volumeVal = volume.coerceIn(0f, 2f)
        vlcjPlayer?.audio()?.setVolume((volumeVal * 100f).toInt())
    }

    override fun getVolume(): Float = volumeVal

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {}

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { timeUpdateListener = listener }
    override fun setOnEnded(listener: ((() -> Unit))?) { endedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { errorListener = listener }

    override fun dispose() {
        vlcjAudioComponent?.release()
        super.dispose()
    }
}
