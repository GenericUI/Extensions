@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.ext.media.desktop.DesktopVlcMediaExtensionProvider
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcPlatformCapabilities
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.posix.memcpy
import kotlin.math.abs

/**
 * Shared desktop [AudioPlayerNativeImpl] backed directly by native `libvlc` ([LibVlcBindings]),
 * shared by `:extensions:media:desktop:kde`, `:extensions:media:desktop:gtk`,
 * `:extensions:media:desktop:windows`, and `:extensions:media:desktop:macos`.
 */
open class DesktopLibVlcAudioPlayerImpl : AudioPlayerNativeImpl {

    private var initializedVlcInstance: COpaquePointer? = null
    private var initializedVlcMediaPlayer: COpaquePointer? = null
    private var analyzerVlcMediaPlayer: COpaquePointer? = null
    private var currentAnalyzerVlcMedia: COpaquePointer? = null
    private var analyzerLoadedUrl: String = ""
    private var selfStableRef: StableRef<DesktopLibVlcAudioPlayerImpl>? = null
    private var vlcInitAttempted: Boolean = false

    protected val vlcInstance: COpaquePointer?
        get() = initializedVlcInstance

    protected val vlcMediaPlayer: COpaquePointer?
        get() = initializedVlcMediaPlayer

    protected var currentVlcMedia: COpaquePointer? = null

    private var currentSourceUrl: String = ""
    private var currentSecFallback: Double = 0.0
    @kotlin.concurrent.Volatile
    private var playingFallback: Boolean = false
    private var volumeLevel: Float = 1.0f
    private var keepScreenOnState: Boolean = true
    private var screenWakeToken: Long = MediaScreenWakeController.allocateToken()
    @kotlin.concurrent.Volatile
    private var nativePlayerDisposed: Boolean = false
    override val isDisposed: Boolean
        get() = nativePlayerDisposed
    private var lastAppliedAudioOutputModule: String = ""
    private var lastAppliedAudioDeviceId: String = ""
    private val pollScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackMonitorJob: Job? = null
    private var attachedAnalyzer: AudioAnalyzer? = null

    init {
        DesktopVlcMediaExtensionProvider.registerAudioPlayer(this)
    }

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onBufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPlayingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onMediaReadyListener: (() -> Unit)? = null
    private var mediaReady: Boolean = false
    private var lastReportedTimeSec: Double = -1.0
    private var lastReportedDurationSec: Double = -1.0
    private var clockAnchorVlcMs: Long = -1L
    private var clockAnchorTimeSec: Double = 0.0
    private var clockAnchorMark: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && isPlaying()
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
    }

    private fun stopPlaybackMonitor() {
        playbackMonitorJob?.cancel()
        playbackMonitorJob = null
    }

    private fun resolveInterpolatedAudioTimeSec(): Double {
        var curSec = clockAnchorTimeSec
        val mark = clockAnchorMark
        if (mark != null && playingFallback) {
            val delta = (mark.elapsedNow().inWholeNanoseconds / 1_000_000_000.0).coerceAtMost(5.0)
            curSec += delta
        }
        val dur = lastReportedDurationSec
        return if (dur > 0.0) curSec.coerceIn(0.0, dur) else curSec.coerceAtLeast(0.0)
    }

    private fun startPlaybackMonitorIfNeeded() {
        stopPlaybackMonitor()
        if (nativePlayerDisposed || !playingFallback || currentSourceUrl.isBlank() || !LibVlcBindings.isAvailable) return
        playbackMonitorJob = pollScope.launch {
            while (isActive && !nativePlayerDisposed && playingFallback) {
                delay(150L)
                if (nativePlayerDisposed || !playingFallback) break
                val player = vlcMediaPlayer ?: continue
                val state = LibVlcBindings.libvlc_media_player_get_state(player)
                if (lastReportedDurationSec <= 0.0) {
                    val vlcLenMs = LibVlcBindings.libvlc_media_player_get_length(player)
                    if (vlcLenMs > 0L) {
                        val durSec = vlcLenMs / 1000.0
                        lastReportedDurationSec = durSec
                        onDurationChangedListener?.invoke(durSec)
                    }
                }
                if (state == 3) {
                    val vlcMs = LibVlcBindings.libvlc_media_player_get_time(player)
                    val nowMark = kotlin.time.TimeSource.Monotonic.markNow()
                    if (vlcMs >= 0L && vlcMs != clockAnchorVlcMs) {
                        val vlcSec = vlcMs / 1000.0
                        val anchoredSec = if (clockAnchorMark != null && vlcSec > 0.0) {
                            val prev = resolveInterpolatedAudioTimeSec()
                            if (vlcSec >= prev - 0.5) maxOf(vlcSec, prev) else vlcSec
                        } else if (vlcMs > 0L) {
                            vlcSec
                        } else {
                            currentSecFallback
                        }
                        clockAnchorVlcMs = vlcMs
                        clockAnchorTimeSec = anchoredSec
                        clockAnchorMark = nowMark
                    } else if (clockAnchorMark == null) {
                        clockAnchorVlcMs = vlcMs
                        clockAnchorTimeSec = if (vlcMs > 0L) vlcMs / 1000.0 else currentSecFallback
                        clockAnchorMark = nowMark
                    }
                    val curSec = resolveInterpolatedAudioTimeSec()
                    currentSecFallback = curSec
                    val crossedWholeSecond = lastReportedTimeSec >= 0.0 && curSec.toInt() != lastReportedTimeSec.toInt()
                    if (lastReportedTimeSec < 0.0 || crossedWholeSecond || abs(curSec - lastReportedTimeSec) >= 0.25) {
                        lastReportedTimeSec = curSec
                        onTimeUpdateListener?.invoke(curSec)
                    }
                } else if (state == 6) {
                    playingFallback = false
                    syncWakeLockState()
                    onPlayingStateChangedListener?.invoke(false)
                    onEndedListener?.invoke()
                    break
                } else if (state == 7) {
                    playingFallback = false
                    syncWakeLockState()
                    onPlayingStateChangedListener?.invoke(false)
                    onErrorListener?.invoke(LibVlcBindings.libvlc_errmsg() ?: "libvlc audio playback error")
                    break
                }
            }
        }
    }

    private fun ensureVlcPlayer(): COpaquePointer? {
        if (!vlcInitAttempted) {
            vlcInitAttempted = true
            val config = MediaExtension.vlcConfiguration
            val args = VlcPlatformCapabilities.mergeLibVlcArgs(
                baseArgs = listOf("--no-video", "--quiet"),
                config = config,
                includeVideoOptions = false,
                allowWindowVoutFlag = false
            )
            val inst = LibVlcBindings.libvlc_new(args)
            initializedVlcInstance = inst
            val player = LibVlcBindings.libvlc_media_player_new(inst)
            initializedVlcMediaPlayer = player
            if (player != null) {
                LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
                applyVlcConfiguration(config)
            }
        }
        return initializedVlcMediaPlayer
    }

    internal fun applyVlcConfiguration(config: VlcConfiguration) {
        if (nativePlayerDisposed) return
        val player = vlcMediaPlayer ?: return
        val (nextModule, nextDeviceId) = LibVlcBindings.applyAudioConfiguration(
            player = player,
            config = config,
            previousModule = lastAppliedAudioOutputModule,
            previousDeviceId = lastAppliedAudioDeviceId
        )
        lastAppliedAudioOutputModule = nextModule
        lastAppliedAudioDeviceId = nextDeviceId
    }

    private fun ensureAnalyzerVlcPlayer(): COpaquePointer? {
        ensureVlcPlayer()
        val inst = initializedVlcInstance ?: return null
        val existing = analyzerVlcMediaPlayer
        if (existing != null) return existing
        val tapPlayer = LibVlcBindings.libvlc_media_player_new(inst) ?: return null
        if (selfStableRef == null) {
            selfStableRef = StableRef.create(this)
        }
        LibVlcBindings.libvlc_audio_set_callbacks(
            player = tapPlayer,
            playCb = vlcAudioPlayCallback.reinterpret(),
            pauseCb = null,
            resumeCb = null,
            flushCb = null,
            drainCb = null,
            opaque = selfStableRef?.asCPointer()
        )
        LibVlcBindings.libvlc_audio_set_format(tapPlayer, "S16N", 44100, 2)
        analyzerVlcMediaPlayer = tapPlayer
        return tapPlayer
    }

    private fun syncAnalyzerMedia(url: String, seekOffsetSeconds: Double) {
        if (attachedAnalyzer == null || url.isBlank()) return
        val tapPlayer = ensureAnalyzerVlcPlayer() ?: return
        LibVlcBindings.libvlc_media_release(currentAnalyzerVlcMedia)
        val media = if (url.contains("://")) {
            LibVlcBindings.libvlc_media_new_location(vlcInstance, url)
        } else {
            LibVlcBindings.libvlc_media_new_path(vlcInstance, url)
        }
        LibVlcBindings.libvlc_media_add_option(media, ":no-video")
        if (seekOffsetSeconds > 0.0) {
            LibVlcBindings.libvlc_media_add_option(media, ":start-time=$seekOffsetSeconds")
        }
        currentAnalyzerVlcMedia = media
        analyzerLoadedUrl = url
        LibVlcBindings.libvlc_media_player_set_media(tapPlayer, media)
    }

    internal fun onVlcAudioSamples(samples: COpaquePointer, frameCount: Int) {
        val analyzer = attachedAnalyzer ?: return
        val byteCount = (frameCount * 4).coerceIn(0, 65536)
        if (byteCount <= 0) return
        val pcmBytes = ByteArray(byteCount)
        pcmBytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), samples, byteCount.toULong())
        }
        analyzer.pushPcmS16Le(
            bytes = pcmBytes,
            byteCount = byteCount,
            channels = 2,
            volumeScale = volumeLevel.coerceIn(0f, 2f)
        )
    }

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        stopPlaybackMonitor()
        currentSourceUrl = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        playingFallback = false
        mediaReady = false
        lastReportedTimeSec = -1.0
        lastReportedDurationSec = -1.0
        clockAnchorVlcMs = -1L
        clockAnchorTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        clockAnchorMark = null

        if (url.isNotBlank()) {
            val player = ensureVlcPlayer()
            val config = MediaExtension.vlcConfiguration
            LibVlcBindings.libvlc_media_release(currentVlcMedia)
            val media = if (url.contains("://")) {
                LibVlcBindings.libvlc_media_new_location(vlcInstance, url)
            } else {
                LibVlcBindings.libvlc_media_new_path(vlcInstance, url)
            }
            for (option in VlcPlatformCapabilities.buildMediaOptions(
                config = config,
                seekOffsetSeconds = seekOffsetSeconds,
                includeVideoOptions = false,
                allowWindowVoutOption = false
            )) {
                LibVlcBindings.libvlc_media_add_option(media, option)
            }
            currentVlcMedia = media
            applyVlcConfiguration(config)
            LibVlcBindings.libvlc_media_player_set_media(player, media)
            if (attachedAnalyzer != null) {
                syncAnalyzerMedia(url, seekOffsetSeconds)
            }
            mediaReady = true
            onMediaReadyListener?.invoke()
        }
        syncWakeLockState()
    }

    override fun play() {
        lastReportedTimeSec = -1.0
        clockAnchorVlcMs = -1L
        clockAnchorTimeSec = currentSecFallback.coerceAtLeast(0.0)
        clockAnchorMark = null
        playingFallback = true
        val player = if (currentSourceUrl.isNotBlank()) (vlcMediaPlayer ?: ensureVlcPlayer()) else null
        if (player != null) {
            val res = LibVlcBindings.libvlc_media_player_play(player)
            if (res < 0 && LibVlcBindings.isAvailable) {
                playingFallback = false
                syncWakeLockState()
                onPlayingStateChangedListener?.invoke(false)
                onErrorListener?.invoke(LibVlcBindings.libvlc_errmsg() ?: "libvlc_media_player_play failed")
                return
            }
        }
        if (attachedAnalyzer != null && currentSourceUrl.isNotBlank()) {
            if (analyzerLoadedUrl != currentSourceUrl || analyzerVlcMediaPlayer == null) {
                syncAnalyzerMedia(currentSourceUrl, currentSecFallback)
            }
            analyzerVlcMediaPlayer?.let { tapPlayer ->
                LibVlcBindings.libvlc_media_player_play(tapPlayer)
            }
        }
        syncWakeLockState()
        startPlaybackMonitorIfNeeded()
        onPlayingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        val frozenSec = resolveInterpolatedAudioTimeSec()
        playingFallback = false
        stopPlaybackMonitor()
        clockAnchorTimeSec = frozenSec
        currentSecFallback = frozenSec
        clockAnchorMark = null
        LibVlcBindings.libvlc_media_player_set_pause(vlcMediaPlayer, 1)
        analyzerVlcMediaPlayer?.let { tapPlayer ->
            LibVlcBindings.libvlc_media_player_set_pause(tapPlayer, 1)
        }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playingFallback = false
        stopPlaybackMonitor()
        lastReportedTimeSec = -1.0
        currentSecFallback = 0.0
        clockAnchorVlcMs = -1L
        clockAnchorTimeSec = 0.0
        clockAnchorMark = null
        LibVlcBindings.libvlc_media_player_stop(vlcMediaPlayer)
        analyzerVlcMediaPlayer?.let { tapPlayer ->
            LibVlcBindings.libvlc_media_player_stop(tapPlayer)
        }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        clockAnchorVlcMs = -1L
        clockAnchorTimeSec = clamped
        clockAnchorMark = null
        val targetMs = (clamped * 1000.0).toLong()
        LibVlcBindings.libvlc_media_player_set_time(vlcMediaPlayer, targetMs)
        analyzerVlcMediaPlayer?.let { tapPlayer ->
            LibVlcBindings.libvlc_media_player_set_time(tapPlayer, targetMs)
        }
        onTimeUpdateListener?.invoke(clamped)
    }

    override fun getCurrentTimeSeconds(): Double {
        val vlcMs = LibVlcBindings.libvlc_media_player_get_time(vlcMediaPlayer)
        return if (vlcMs >= 0L) vlcMs / 1000.0 else currentSecFallback
    }

    override fun getDurationSeconds(): Double {
        val vlcLenMs = LibVlcBindings.libvlc_media_player_get_length(vlcMediaPlayer)
        return if (vlcLenMs > 0L) vlcLenMs / 1000.0 else 0.0
    }

    override fun isPlaying(): Boolean {
        if (!playingFallback) return false
        val player = vlcMediaPlayer
        return if (player != null && currentSourceUrl.isNotBlank() && LibVlcBindings.isAvailable) {
            val state = LibVlcBindings.libvlc_media_player_get_state(player)
            when {
                state == 3 -> true
                state in 4..7 -> false
                state in 0..2 -> true
                else -> LibVlcBindings.libvlc_media_player_is_playing(player)
            }
        } else {
            playingFallback
        }
    }

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 2f)
        LibVlcBindings.libvlc_audio_set_volume(vlcMediaPlayer, (volumeLevel * 100f).toInt())
    }

    override fun getVolume(): Float = volumeLevel

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        if (analyzer != null) {
            if (currentSourceUrl.isNotBlank()) {
                val offset = getCurrentTimeSeconds().coerceAtLeast(0.0)
                syncAnalyzerMedia(currentSourceUrl, offset)
                if (isPlaying()) {
                    analyzerVlcMediaPlayer?.let { tapPlayer ->
                        LibVlcBindings.libvlc_media_player_play(tapPlayer)
                        if (offset > 0.0) {
                            LibVlcBindings.libvlc_media_player_set_time(tapPlayer, (offset * 1000.0).toLong())
                        }
                    }
                }
            }
        } else {
            analyzerVlcMediaPlayer?.let { tapPlayer ->
                LibVlcBindings.libvlc_media_player_stop(tapPlayer)
            }
        }
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
        nativePlayerDisposed = true
        DesktopVlcMediaExtensionProvider.unregisterAudioPlayer(this)
        playingFallback = false
        stopPlaybackMonitor()
        pollScope.cancel()
        MediaScreenWakeController.releaseToken(screenWakeToken)
        analyzerVlcMediaPlayer?.let { tapPlayer ->
            LibVlcBindings.libvlc_media_player_stop(tapPlayer)
            LibVlcBindings.libvlc_audio_set_callbacks(tapPlayer, null)
            LibVlcBindings.libvlc_media_player_release(tapPlayer)
        }
        analyzerVlcMediaPlayer = null
        LibVlcBindings.libvlc_media_release(currentAnalyzerVlcMedia)
        currentAnalyzerVlcMedia = null
        selfStableRef?.dispose()
        selfStableRef = null

        LibVlcBindings.libvlc_media_player_stop(vlcMediaPlayer)
        LibVlcBindings.libvlc_media_release(currentVlcMedia)
        currentVlcMedia = null
        LibVlcBindings.libvlc_media_player_release(vlcMediaPlayer)
        LibVlcBindings.libvlc_release(vlcInstance)
    }
}

private val vlcAudioPlayCallback = staticCFunction { opaque: COpaquePointer?, samples: COpaquePointer?, count: UInt, _: Long ->
    val impl = opaque?.asStableRef<DesktopLibVlcAudioPlayerImpl>()?.get()
    if (impl != null && samples != null && count > 0u) {
        impl.onVlcAudioSamples(samples, count.toInt())
    }
    Unit
}
