package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import platform.AVFoundation.AVURLAsset

/**
 * iOS `AVPlayer` / `AVAudioEngine` implementation of [AudioPlayerNativeImpl].
 */
class IosAudioPlayerImpl : AudioPlayerNativeImpl {
    private var currentTimeSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var playing: Boolean = false
    private var volumeLevel: Float = 1.0f
    private var keepScreenOnState: Boolean = true
    private var screenWakeToken: Long = MediaScreenWakeController.allocateToken()
    @kotlin.concurrent.Volatile
    private var nativePlayerDisposed: Boolean = false
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onBufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPlayingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onMediaReadyListener: (() -> Unit)? = null
    private var mediaReady: Boolean = false

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && playing
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
    }

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean =
        codec.html5CanPlayProbes.any { probe ->
            runCatching { AVURLAsset.isPlayableExtendedMIMEType(probe) }.getOrDefault(false)
        }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        AudioCodec.entries.filterTo(linkedSetOf()) { isAudioCodecSupported(it) }

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        mediaReady = url.isNotBlank()
        if (mediaReady) {
            onMediaReadyListener?.invoke()
        }
    }

    override fun play() {
        playing = true
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        playing = false
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
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
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChangedListener = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        onBufferedRangesChangedListener = listener
    }
    override fun setOnEnded(listener: (() -> Unit)?) { onEndedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }
    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { onPlayingStateChangedListener = listener }
    override fun setKeepScreenOn(keepScreenOn: Boolean) {
        keepScreenOnState = keepScreenOn
        syncWakeLockState()
    }
    override fun isKeepScreenOn(): Boolean = keepScreenOnState
    override fun bindScreenWakeToken(token: Long) {
        if (token > 0L && token != screenWakeToken) {
            val previous = screenWakeToken
            screenWakeToken = token
            MediaScreenWakeController.releaseToken(previous)
            syncWakeLockState()
        }
    }
    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
        if (mediaReady) listener?.invoke()
    }
    override fun isMediaReady(): Boolean = mediaReady

    override fun dispose() {
        nativePlayerDisposed = true
        stop()
        MediaScreenWakeController.releaseToken(screenWakeToken)
        attachedAnalyzer = null
    }
}
