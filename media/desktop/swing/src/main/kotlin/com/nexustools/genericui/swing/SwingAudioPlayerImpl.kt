package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.sun.jna.Pointer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

/**
 * Java Swing [AudioPlayerNativeImpl] backed by `libvlcj` ([AudioPlayerComponent]).
 */
class SwingAudioPlayerImpl : AudioPlayerNativeImpl {

    private val vlcjAudioComponent: AudioPlayerComponent? = runCatching {
        AudioPlayerComponent()
    }.getOrNull()

    private val vlcjPlayer: MediaPlayer?
        get() = vlcjAudioComponent?.mediaPlayer()

    private var analyzerTapComponent: AudioPlayerComponent? = null
    private var currentSourceUrl: String = ""
    private var currentSecFallback: Double = 0.0
    private var playingFallback: Boolean = false
    private var volumeVal: Float = 1.0f
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var timeUpdateListener: ((Double) -> Unit)? = null
    private var durationChangedListener: ((Double) -> Unit)? = null
    private var bufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var endedListener: (() -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var mediaReadyListener: (() -> Unit)? = null
    private var mediaReady: Boolean = false

    init {
        vlcjAudioComponent?.mediaPlayer()?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                if (!mediaReady) {
                    mediaReady = true
                    mediaReadyListener?.invoke()
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0L) {
                    durationChangedListener?.invoke(newLength / 1000.0)
                }
            }

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

    private fun ensureAnalyzerTapPlayer(): MediaPlayer? {
        if (attachedAnalyzer == null) return null
        val existing = analyzerTapComponent?.mediaPlayer()
        if (existing != null) return existing
        val comp = runCatching { AudioPlayerComponent() }.getOrNull() ?: return null
        val mp = comp.mediaPlayer()
        runCatching {
            mp.audio().callback("S16N", 44100, 2, object : AudioCallbackAdapter() {
                override fun play(mediaPlayer: MediaPlayer, samples: Pointer, sampleCount: Int, pts: Long) {
                    val analyzer = attachedAnalyzer ?: return
                    val byteCount = (sampleCount * 4).coerceIn(0, 65536)
                    if (byteCount > 0) {
                        val bytes = samples.getByteArray(0L, byteCount)
                        analyzer.pushPcmS16Le(
                            bytes = bytes,
                            byteCount = byteCount,
                            channels = 2,
                            volumeScale = volumeVal
                        )
                    }
                }
            })
        }
        analyzerTapComponent = comp
        return mp
    }

    private fun syncAnalyzerTapMedia(url: String, seekOffsetSeconds: Double) {
        if (attachedAnalyzer == null || url.isBlank()) return
        val tapPlayer = ensureAnalyzerTapPlayer() ?: return
        val options = if (seekOffsetSeconds > 0.0) {
            arrayOf(":no-video", ":start-time=$seekOffsetSeconds")
        } else {
            arrayOf(":no-video")
        }
        runCatching { tapPlayer.media().prepare(url, *options) }
    }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = AudioCodec.entries.toSet()

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean = true

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentSourceUrl = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        mediaReady = false
        val options = if (seekOffsetSeconds > 0.0) {
            arrayOf(":start-time=$seekOffsetSeconds")
        } else {
            emptyArray()
        }
        vlcjPlayer?.media()?.prepare(url, *options)
        if (attachedAnalyzer != null) {
            syncAnalyzerTapMedia(url, seekOffsetSeconds)
        }
        if (url.isNotBlank()) {
            mediaReady = true
            mediaReadyListener?.invoke()
        }
    }

    override fun play() {
        playingFallback = true
        vlcjPlayer?.controls()?.play()
        if (attachedAnalyzer != null && currentSourceUrl.isNotBlank()) {
            ensureAnalyzerTapPlayer()?.controls()?.play()
        }
    }

    override fun pause() {
        playingFallback = false
        vlcjPlayer?.controls()?.setPause(true)
        analyzerTapComponent?.mediaPlayer()?.controls()?.setPause(true)
    }

    override fun stop() {
        playingFallback = false
        currentSecFallback = 0.0
        vlcjPlayer?.controls()?.stop()
        analyzerTapComponent?.mediaPlayer()?.controls()?.stop()
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        val targetMs = (clamped * 1000.0).toLong()
        vlcjPlayer?.controls()?.setTime(targetMs)
        analyzerTapComponent?.mediaPlayer()?.controls()?.setTime(targetMs)
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

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        if (analyzer != null && currentSourceUrl.isNotBlank()) {
            val offset = getCurrentTimeSeconds().coerceAtLeast(0.0)
            syncAnalyzerTapMedia(currentSourceUrl, offset)
            if (isPlaying()) {
                ensureAnalyzerTapPlayer()?.controls()?.play()
            }
        } else if (analyzer == null) {
            analyzerTapComponent?.mediaPlayer()?.controls()?.stop()
        }
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { timeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { durationChangedListener = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        bufferedRangesChangedListener = listener
    }
    override fun setOnEnded(listener: ((() -> Unit))?) { endedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { errorListener = listener }
    override fun setOnMediaReady(listener: (() -> Unit)?) {
        mediaReadyListener = listener
        if (mediaReady) listener?.invoke()
    }
    override fun isMediaReady(): Boolean = mediaReady

    override fun dispose() {
        runCatching { analyzerTapComponent?.release() }
        analyzerTapComponent = null
        vlcjAudioComponent?.release()
    }
}
