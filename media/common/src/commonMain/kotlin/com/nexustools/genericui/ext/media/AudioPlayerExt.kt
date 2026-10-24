package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl

/**
 * Extended cross-platform audio player component in `:extensions:media` integrating with
 * [MediaExtension] and [AudioAnalyzerExt].
 */
class AudioPlayerExt(
    sourceUrl: String? = null
) : AbstractComponent() {

    val player: AudioPlayer = AudioPlayer(sourceUrl)

    val sourceUrl: String?
        get() = player.sourceUrl

    val seekOffsetSeconds: Double
        get() = player.seekOffsetSeconds

    var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
            typedNative<AudioPlayerNativeImpl<*>>()?.setVolume(player.volume)
        }

    val currentTimeSeconds: Double
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.getCurrentTimeSeconds() ?: player.currentTimeSeconds

    val durationSeconds: Double
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.getDurationSeconds() ?: player.durationSeconds

    val isPlaying: Boolean
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.isPlaying() ?: player.isPlaying

    fun getSupportedAudioCodecs(): Set<AudioCodec> =
        typedNative<AudioPlayerNativeImpl<*>>()?.getSupportedAudioCodecs() ?: player.getSupportedAudioCodecs()

    fun setSource(url: String, seekOffsetSeconds: Double = 0.0) {
        player.setSource(url, seekOffsetSeconds)
        typedNative<AudioPlayerNativeImpl<*>>()?.setSource(url, seekOffsetSeconds)
    }

    fun play() {
        player.play()
        typedNative<AudioPlayerNativeImpl<*>>()?.play()
    }

    fun pause() {
        player.pause()
        typedNative<AudioPlayerNativeImpl<*>>()?.pause()
    }

    fun stop() {
        player.stop()
        typedNative<AudioPlayerNativeImpl<*>>()?.stop()
    }

    fun seekTo(seconds: Double) {
        player.seekTo(seconds)
        typedNative<AudioPlayerNativeImpl<*>>()?.seekTo(seconds)
    }

    fun attachAudioAnalyzer(analyzerExt: AudioAnalyzerExt?) {
        player.attachAudioAnalyzer(analyzerExt?.analyzer)
        typedNative<AudioPlayerNativeImpl<*>>()?.attachAudioAnalyzer(analyzerExt?.analyzer)
    }

    override fun createNativeImpl(factory: NativeFactory): AudioPlayerNativeImpl<*> =
        MediaExtension.createAudioPlayerNativeOrFallback()
}
