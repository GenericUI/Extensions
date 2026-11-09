@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/**
 * Shared desktop [AudioPlayerNativeImpl] backed directly by native `libvlc` ([LibVlcBindings]),
 * shared by `:extensions:media:desktop:qt`, `:extensions:media:desktop:gtk`,
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
    private var playingFallback: Boolean = false
    private var volumeLevel: Float = 1.0f
    private var attachedAnalyzer: AudioAnalyzer? = null

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onBufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onMediaReadyListener: (() -> Unit)? = null
    private var mediaReady: Boolean = false

    private fun ensureVlcPlayer(): COpaquePointer? {
        if (!vlcInitAttempted) {
            vlcInitAttempted = true
            val inst = LibVlcBindings.libvlc_new(listOf("--no-video", "--quiet"))
            initializedVlcInstance = inst
            val player = LibVlcBindings.libvlc_media_player_new(inst)
            initializedVlcMediaPlayer = player
            if (player != null) {
                LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
            }
        }
        return initializedVlcMediaPlayer
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
        LibVlcBindings.libvlc_audio_set_format(tapPlayer, "S16N", 44100, 2)
        LibVlcBindings.libvlc_audio_set_callbacks(
            player = tapPlayer,
            playCb = vlcAudioPlayCallback.reinterpret(),
            pauseCb = null,
            resumeCb = null,
            flushCb = null,
            drainCb = null,
            opaque = selfStableRef?.asCPointer()
        )
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

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = AudioCodec.entries.toSet()

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean = true

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentSourceUrl = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        playingFallback = false
        mediaReady = false

        if (url.isNotBlank()) {
            val player = ensureVlcPlayer()
            LibVlcBindings.libvlc_media_release(currentVlcMedia)
            val media = if (url.contains("://")) {
                LibVlcBindings.libvlc_media_new_location(vlcInstance, url)
            } else {
                LibVlcBindings.libvlc_media_new_path(vlcInstance, url)
            }
            if (seekOffsetSeconds > 0.0) {
                LibVlcBindings.libvlc_media_add_option(media, ":start-time=$seekOffsetSeconds")
            }
            currentVlcMedia = media
            LibVlcBindings.libvlc_media_player_set_media(player, media)
            if (attachedAnalyzer != null) {
                syncAnalyzerMedia(url, seekOffsetSeconds)
            }
            mediaReady = true
            onMediaReadyListener?.invoke()
        }
    }

    override fun play() {
        playingFallback = true
        val player = vlcMediaPlayer ?: ensureVlcPlayer()
        if (player != null) {
            val res = LibVlcBindings.libvlc_media_player_play(player)
            if (res < 0 && LibVlcBindings.isAvailable) {
                onErrorListener?.invoke(LibVlcBindings.libvlc_errmsg() ?: "libvlc_media_player_play failed")
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
    }

    override fun pause() {
        playingFallback = false
        LibVlcBindings.libvlc_media_player_set_pause(vlcMediaPlayer, 1)
        analyzerVlcMediaPlayer?.let { tapPlayer ->
            LibVlcBindings.libvlc_media_player_set_pause(tapPlayer, 1)
        }
    }

    override fun stop() {
        playingFallback = false
        currentSecFallback = 0.0
        LibVlcBindings.libvlc_media_player_stop(vlcMediaPlayer)
        analyzerVlcMediaPlayer?.let { tapPlayer ->
            LibVlcBindings.libvlc_media_player_stop(tapPlayer)
        }
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
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
        return if (player != null && LibVlcBindings.isAvailable) {
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
    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
        if (mediaReady) listener?.invoke()
    }
    override fun isMediaReady(): Boolean = mediaReady

    override fun dispose() {
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
