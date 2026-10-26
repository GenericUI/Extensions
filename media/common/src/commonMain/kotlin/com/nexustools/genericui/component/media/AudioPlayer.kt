package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl

/**
 * Cross-platform audio player component supporting seek offsets, volume control,
 * and real-time [AudioAnalyzer] attachment.
 */
class AudioPlayer(
    sourceUrl: String? = null
) : AbstractComponent() {
    var sourceUrl: String? = sourceUrl
        private set

    var seekOffsetSeconds: Double = 0.0
        private set

    private var playingFallbackState: Boolean = false

    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2f)
            typedNative<AudioPlayerNativeImpl<*>>()?.setVolume(field)
        }

    val currentTimeSeconds: Double
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.getCurrentTimeSeconds() ?: seekOffsetSeconds

    val durationSeconds: Double
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.getDurationSeconds() ?: 0.0

    val isPlaying: Boolean
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.isPlaying() ?: playingFallbackState

    var onTimeUpdate: ((Double) -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var attachedAnalyzer: AudioAnalyzer? = null

    fun getSupportedAudioCodecs(): Set<AudioCodec> =
        typedNative<AudioPlayerNativeImpl<*>>()?.getSupportedAudioCodecs() ?: emptySet()

    fun setSource(url: String, seekOffsetSeconds: Double = 0.0) {
        this.sourceUrl = url
        this.seekOffsetSeconds = seekOffsetSeconds
        typedNative<AudioPlayerNativeImpl<*>>()?.setSource(url, seekOffsetSeconds)
    }

    fun play() {
        playingFallbackState = true
        typedNative<AudioPlayerNativeImpl<*>>()?.play()
    }

    fun pause() {
        playingFallbackState = false
        typedNative<AudioPlayerNativeImpl<*>>()?.pause()
    }

    fun stop() {
        playingFallbackState = false
        seekOffsetSeconds = 0.0
        typedNative<AudioPlayerNativeImpl<*>>()?.stop()
    }

    fun seekTo(seconds: Double) {
        seekOffsetSeconds = seconds
        typedNative<AudioPlayerNativeImpl<*>>()?.seekTo(seconds)
    }

    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        typedNative<AudioPlayerNativeImpl<*>>()?.attachAudioAnalyzer(analyzer)
    }

    override fun createNativeImpl(factory: NativeFactory): AudioPlayerNativeImpl<*> =
        MediaExtension.createAudioPlayerNativeOrFallback()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<AudioPlayerNativeImpl<*>>() ?: return
        impl.setVolume(volume)
        impl.setOnTimeUpdate(onTimeUpdate)
        impl.setOnEnded(onEnded)
        impl.setOnError(onError)
        attachedAnalyzer?.let { impl.attachAudioAnalyzer(it) }
        sourceUrl?.let { impl.setSource(it, seekOffsetSeconds) }
    }
}
