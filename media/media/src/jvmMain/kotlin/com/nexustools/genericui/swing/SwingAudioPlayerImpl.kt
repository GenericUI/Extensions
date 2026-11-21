package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.ext.media.swing.SwingMediaExtensionProvider
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcPlatformCapabilities
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.sun.jna.Pointer
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import uk.co.caprica.vlcj.player.component.MediaPlayerSpecs

/**
 * Java Swing [AudioPlayerNativeImpl] backed by `libvlcj` ([AudioPlayerComponent]).
 */
class SwingAudioPlayerImpl : AudioPlayerNativeImpl {

    private val initialConfig: VlcConfiguration = MediaExtension.vlcConfiguration
    private val vlcjFactory: MediaPlayerFactory? = SwingMediaExtensionProvider.createMediaPlayerFactorySafely(
        VlcPlatformCapabilities.mergeLibVlcArgs(
            baseArgs = listOf("--quiet", "--intf=dummy", "--no-video"),
            config = initialConfig,
            includeVideoOptions = false,
            allowWindowVoutFlag = false
        )
    )
    private val vlcjAudioComponent: AudioPlayerComponent? = vlcjFactory?.let { factory ->
        runCatching {
            MediaPlayerSpecs.audioPlayerSpec()
                .withFactory(factory)
                .audioPlayer()
        }.getOrNull()
    }

    private val vlcjPlayer: MediaPlayer?
        get() = vlcjAudioComponent?.mediaPlayer()

    private var analyzerTapComponent: AudioPlayerComponent? = null
    private var currentSourceUrl: String = ""
    private var currentSecFallback: Double = 0.0
    private var playingFallback: Boolean = false
    private var volumeVal: Float = 1.0f
    private var keepScreenOnState: Boolean = true
    private var screenWakeToken: Long = MediaScreenWakeController.allocateToken()
    @Volatile
    private var nativePlayerDisposed: Boolean = false
    override val isDisposed: Boolean
        get() = nativePlayerDisposed
    private var lastAppliedAudioOutputModule: String = ""
    private var lastAppliedAudioDeviceId: String = ""
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var timeUpdateListener: ((Double) -> Unit)? = null
    private var durationChangedListener: ((Double) -> Unit)? = null
    private var bufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var endedListener: (() -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var playingStateChangedListener: ((Boolean) -> Unit)? = null
    private var mediaReadyListener: (() -> Unit)? = null
    private var mediaReady: Boolean = false

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && isPlaying()
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
    }

    internal fun applyVlcConfiguration(config: VlcConfiguration) {
        if (nativePlayerDisposed) return
        val player = vlcjPlayer ?: return
        val (nextModule, nextDeviceId) = SwingMediaExtensionProvider.applyAudioConfiguration(
            mediaPlayer = player,
            config = config,
            previousModule = lastAppliedAudioOutputModule,
            previousDeviceId = lastAppliedAudioDeviceId
        )
        lastAppliedAudioOutputModule = nextModule
        lastAppliedAudioDeviceId = nextDeviceId
    }

    init {
        SwingMediaExtensionProvider.registerAudioPlayer(this)
        applyVlcConfiguration(initialConfig)
        vlcjAudioComponent?.mediaPlayer()?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                playingFallback = true
                syncWakeLockState()
                playingStateChangedListener?.invoke(true)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                playingFallback = false
                syncWakeLockState()
                playingStateChangedListener?.invoke(false)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                playingFallback = false
                syncWakeLockState()
                playingStateChangedListener?.invoke(false)
            }

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
                syncWakeLockState()
                playingStateChangedListener?.invoke(false)
                endedListener?.invoke()
            }

            override fun error(mediaPlayer: MediaPlayer) {
                playingFallback = false
                syncWakeLockState()
                playingStateChangedListener?.invoke(false)
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

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentSourceUrl = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        mediaReady = false
        val config = MediaExtension.vlcConfiguration
        val options = VlcPlatformCapabilities.buildMediaOptions(
            config = config,
            seekOffsetSeconds = seekOffsetSeconds,
            includeVideoOptions = false,
            allowWindowVoutOption = false
        ).toTypedArray()
        applyVlcConfiguration(config)
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
        syncWakeLockState()
        playingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        playingFallback = false
        vlcjPlayer?.controls()?.setPause(true)
        analyzerTapComponent?.mediaPlayer()?.controls()?.setPause(true)
        syncWakeLockState()
        playingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playingFallback = false
        currentSecFallback = 0.0
        vlcjPlayer?.controls()?.stop()
        analyzerTapComponent?.mediaPlayer()?.controls()?.stop()
        syncWakeLockState()
        playingStateChangedListener?.invoke(false)
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
        !nativePlayerDisposed && (playingFallback || vlcjPlayer?.status()?.isPlaying == true)

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
    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { playingStateChangedListener = listener }
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
        mediaReadyListener = listener
        if (mediaReady) listener?.invoke()
    }
    override fun isMediaReady(): Boolean = mediaReady

    override fun dispose() {
        nativePlayerDisposed = true
        SwingMediaExtensionProvider.unregisterAudioPlayer(this)
        playingFallback = false
        MediaScreenWakeController.releaseToken(screenWakeToken)
        runCatching { analyzerTapComponent?.release() }
        analyzerTapComponent = null
        runCatching { vlcjAudioComponent?.release() }
        runCatching { vlcjFactory?.release() }
    }
}
