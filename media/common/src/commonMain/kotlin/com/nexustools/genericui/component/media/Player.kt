package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode

/**
 * Shared media playback contract implemented by both [VideoPlayer] (visual component)
 * and [AudioPlayer] (non-visual media player).
 */
interface Player : Disposable {
    val sourceUrl: String?
    val seekOffsetSeconds: Double
    val durationOverrideSeconds: Double?
    var volume: Float
    var isMuted: Boolean
    var playbackSpeed: Float
    var preloadMode: MediaPreloadMode
    var adaptivePreloadBufferingEnabled: Boolean
    val isMediaReady: Boolean
    val isBuffering: Boolean
    val bufferedRanges: List<Pair<Double, Double>>
    val bufferedEndSeconds: Double
    val estimatedNetworkSpeedRatio: Double
    val currentTimeSeconds: Double
    val durationSeconds: Double
    val isPlaying: Boolean

    var onMediaReady: (() -> Unit)?
    var onTimeUpdate: ((Double) -> Unit)?
    var onDurationChanged: ((Double) -> Unit)?
    var onPlayingStateChanged: ((Boolean) -> Unit)?
    var onBufferingChanged: ((Boolean) -> Unit)?
    var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)?
    var onEnded: (() -> Unit)?
    var onError: ((String) -> Unit)?

    suspend fun awaitMediaReady(timeoutMs: Long = 8_000L): Boolean
    fun getRequiredSafeBufferSeconds(): Double
    fun getSupportedAudioCodecs(): Set<AudioCodec>
    fun isAudioCodecSupported(codec: AudioCodec): Boolean
    fun setSource(url: String, seekOffsetSeconds: Double = 0.0, durationOverrideSeconds: Double? = null)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun stop()
    fun seekTo(seconds: Double)
    fun skipDelta(deltaSeconds: Double)
    fun setBufferingState(buffering: Boolean)
    fun updateBufferedRanges(ranges: List<Pair<Double, Double>>)
    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?)
}
