@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

/**
 * Apple iOS & iPadOS (`BackendTarget.IOS` and `BackendTarget.IPadOS`) implementation of
 * [AudioPlayerNativeImpl] backed by `AVPlayer` and `AVAudioSession`.
 */
class IosAudioPlayerImpl : AudioPlayerNativeImpl {
    val avPlayer: AVPlayer = AVPlayer()

    private var timeObserverToken: Any? = null
    private var endedNotificationObserver: Any? = null
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

    init {
        runCatching {
            val interval = CMTimeMakeWithSeconds(0.25, 600)
            timeObserverToken = avPlayer.addPeriodicTimeObserverForInterval(interval, queue = null) { cmTime ->
                if (nativePlayerDisposed) return@addPeriodicTimeObserverForInterval
                val sec = CMTimeGetSeconds(cmTime)
                if (!sec.isNaN() && !sec.isInfinite() && sec >= 0.0) {
                    currentTimeSec = sec
                    onTimeUpdateListener?.invoke(sec)
                }
                avPlayer.currentItem?.let { item ->
                    val dur = CMTimeGetSeconds(item.duration)
                    if (!dur.isNaN() && !dur.isInfinite() && dur > 0.0 && dur != durationSec) {
                        durationSec = dur
                        onDurationChangedListener?.invoke(dur)
                    }
                }
            }
        }
    }

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && playing
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
    }

    private fun configurePlaybackAudioSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        }
    }

    private fun resolveNsUrl(url: String): NSURL? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
            val localPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
            NSURL.fileURLWithPath(localPath)
        } else {
            NSURL.URLWithString(trimmed) ?: NSURL.fileURLWithPath(trimmed)
        }
    }

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        endedNotificationObserver?.let { obs ->
            runCatching { NSNotificationCenter.defaultCenter.removeObserver(obs) }
            endedNotificationObserver = null
        }

        val nsUrl = resolveNsUrl(url)
        mediaReady = nsUrl != null
        if (nsUrl != null) {
            runCatching {
                val item = AVPlayerItem.playerItemWithURL(nsUrl)
                avPlayer.replaceCurrentItemWithPlayerItem(item)
                if (currentTimeSec > 0.0) {
                    avPlayer.seekToTime(CMTimeMakeWithSeconds(currentTimeSec, 600))
                }
                endedNotificationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVPlayerItemDidPlayToEndTimeNotification,
                    `object` = item,
                    queue = NSOperationQueue.mainQueue
                ) { _ ->
                    playing = false
                    syncWakeLockState()
                    onPlayingStateChangedListener?.invoke(false)
                    onEndedListener?.invoke()
                }
            }
            onMediaReadyListener?.invoke()
        } else {
            runCatching { avPlayer.replaceCurrentItemWithPlayerItem(null) }
        }
    }

    override fun play() {
        configurePlaybackAudioSession()
        playing = true
        runCatching { avPlayer.play() }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        playing = false
        runCatching { avPlayer.pause() }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
        runCatching {
            avPlayer.pause()
            avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
        }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun seekTo(seconds: Double) {
        currentTimeSec = seconds.coerceAtLeast(0.0)
        runCatching { avPlayer.seekToTime(CMTimeMakeWithSeconds(currentTimeSec, 600)) }
        onTimeUpdateListener?.invoke(currentTimeSec)
    }

    override fun getCurrentTimeSeconds(): Double {
        val nativeSec = runCatching { CMTimeGetSeconds(avPlayer.currentTime()) }.getOrNull()
        if (nativeSec != null && !nativeSec.isNaN() && !nativeSec.isInfinite() && nativeSec > 0.0) {
            currentTimeSec = nativeSec
        }
        return currentTimeSec
    }

    override fun getDurationSeconds(): Double = durationSec
    override fun isPlaying(): Boolean = playing

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 1f)
        runCatching { avPlayer.volume = volumeLevel }
    }

    override fun getVolume(): Float = volumeLevel

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
    }

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
        if (nativePlayerDisposed) return
        nativePlayerDisposed = true
        playing = false
        timeObserverToken?.let { tok ->
            runCatching { avPlayer.removeTimeObserver(tok) }
            timeObserverToken = null
        }
        endedNotificationObserver?.let { obs ->
            runCatching { NSNotificationCenter.defaultCenter.removeObserver(obs) }
            endedNotificationObserver = null
        }
        runCatching {
            avPlayer.pause()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
        }
        MediaScreenWakeController.releaseToken(screenWakeToken)
        attachedAnalyzer = null
    }
}
