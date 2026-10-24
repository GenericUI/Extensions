package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl

/**
 * Android `android.media.MediaPlayer` / `AudioTrack` implementation of [AudioPlayerNativeImpl].
 */
class AndroidAudioPlayerImpl : AndroidBaseNativeImpl("android.view.View", "audio-player"), AudioPlayerNativeImpl<AndroidViewHandle> {
    private var sourceUrl: String = ""
    private var currentPositionSeconds: Double = 0.0
    private var durationSeconds: Double = 120.0
    private var playing: Boolean = false
    private var volume: Float = 1f
    private var analyzer: AudioAnalyzer? = null
    private var onTimeUpdate: ((Double) -> Unit)? = null
    private var onEnded: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        setOf(AudioCodec.MP3, AudioCodec.AAC, AudioCodec.VORBIS, AudioCodec.OPUS, AudioCodec.PCM, AudioCodec.FLAC)

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        sourceUrl = url
        currentPositionSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        nativeWidget.attributes["src"] = url
    }

    override fun play() {
        playing = true
    }

    override fun pause() {
        playing = false
    }

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

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun getVolume(): Float = volume

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        this.analyzer = analyzer
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdate = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEnded = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onError = listener }
}
