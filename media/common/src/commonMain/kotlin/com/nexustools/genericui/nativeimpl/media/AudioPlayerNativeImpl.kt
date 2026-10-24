package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.media.AudioCodec

interface AudioPlayerNativeImpl<out W> : NativeImpl<W> {
    fun getSupportedAudioCodecs(): Set<AudioCodec>
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
    fun setOnEnded(listener: (() -> Unit)?)
    fun setOnError(listener: ((String) -> Unit)?)
}
