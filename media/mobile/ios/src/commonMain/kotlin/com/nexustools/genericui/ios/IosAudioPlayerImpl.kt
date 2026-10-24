package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl

/**
 * iOS `AVPlayer` / `AVAudioEngine` implementation of [AudioPlayerNativeImpl].
 */
class IosAudioPlayerImpl : IosBaseNativeImpl("AVAudioPlayerView", "audio-player"), AudioPlayerNativeImpl<IosNativeView> {
    private var currentTimeSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var playing: Boolean = false
    private var volumeLevel: Float = 1.0f
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = setOf(
        AudioCodec.AAC,
        AudioCodec.MP3,
        AudioCodec.ALAC,
        AudioCodec.FLAC,
        AudioCodec.OPUS,
        AudioCodec.AC3,
        AudioCodec.EAC3,
        AudioCodec.PCM
    )

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        nativeWidget.metadata["audioSourceUrl"] = url
    }

    override fun play() { playing = true }
    override fun pause() { playing = false }
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

    override fun setVolume(volume: Float) { volumeLevel = volume.coerceIn(0f, 1f) }
    override fun getVolume(): Float = volumeLevel

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) { attachedAnalyzer = analyzer }
    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdateListener = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEndedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }
}
