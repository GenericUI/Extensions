package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode

interface AudioPlayerNativeImpl : Disposable {
    fun getSupportedAudioCodecs(): Set<AudioCodec>
    fun isAudioCodecSupported(codec: AudioCodec): Boolean = codec in getSupportedAudioCodecs()
    fun setSource(url: String, seekOffsetSeconds: Double = 0.0)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(seconds: Double)
    fun getCurrentTimeSeconds(): Double
    fun getDurationSeconds(): Double
    fun isPlaying(): Boolean
    fun setVolume(volume: Float)
    fun getVolume(): Float
    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?)
    fun setOnTimeUpdate(listener: ((Double) -> Unit)?)
    fun setOnDurationChanged(listener: ((Double) -> Unit)?) {}
    fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {}
    fun setOnEnded(listener: (() -> Unit)?)
    fun setOnError(listener: ((String) -> Unit)?)

    fun setPreloadMode(mode: MediaPreloadMode) {}
    fun getPreloadMode(): MediaPreloadMode = MediaPreloadMode.AUTO
    fun setAdaptivePreloadBufferingEnabled(enabled: Boolean) {}
    fun isAdaptivePreloadBufferingEnabled(): Boolean = true
    fun getBufferedRanges(): List<Pair<Double, Double>> = emptyList()
    fun getEstimatedNetworkSpeedRatio(): Double = 1.0
    fun getRequiredSafeBufferSeconds(): Double = 0.0

    fun setOnMediaReady(listener: (() -> Unit)?) {}
    fun isMediaReady(): Boolean = false
}
