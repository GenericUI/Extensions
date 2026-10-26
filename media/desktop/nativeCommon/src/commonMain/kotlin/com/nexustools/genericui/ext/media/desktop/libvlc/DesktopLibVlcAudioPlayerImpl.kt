@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlinx.cinterop.COpaquePointer

/**
 * Shared desktop [AudioPlayerNativeImpl] backed directly by native `libvlc` ([LibVlcBindings]),
 * shared by `:extensions:media:desktop:qt`, `:extensions:media:desktop:gtk`,
 * `:extensions:media:desktop:windows`, and `:extensions:media:desktop:macos`.
 */
open class DesktopLibVlcAudioPlayerImpl<out W : DesktopNativeWidget>(
    nativeWidget: W
) : DesktopBaseNativeImpl<W>(nativeWidget, "libvlc-audio-player"), AudioPlayerNativeImpl<W> {

    protected val vlcInstance: COpaquePointer? = LibVlcBindings.libvlc_new(listOf("--no-video", "--quiet"))
    protected val vlcMediaPlayer: COpaquePointer? = LibVlcBindings.libvlc_media_player_new(vlcInstance)
    protected var currentVlcMedia: COpaquePointer? = null

    private var currentSecFallback: Double = 0.0
    private var playingFallback: Boolean = false
    private var volumeLevel: Float = 1.0f
    private var attachedAnalyzer: AudioAnalyzer? = null

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = AudioCodec.entries.toSet()

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        nativeWidget.attributes["audioSource"] = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)

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
        LibVlcBindings.libvlc_media_player_set_media(vlcMediaPlayer, media)
    }

    override fun play() {
        playingFallback = true
        val res = LibVlcBindings.libvlc_media_player_play(vlcMediaPlayer)
        if (res < 0 && LibVlcBindings.isAvailable) {
            onErrorListener?.invoke(LibVlcBindings.libvlc_errmsg() ?: "libvlc_media_player_play failed")
        }
    }

    override fun pause() {
        playingFallback = false
        LibVlcBindings.libvlc_media_player_set_pause(vlcMediaPlayer, 1)
    }

    override fun stop() {
        playingFallback = false
        currentSecFallback = 0.0
        LibVlcBindings.libvlc_media_player_stop(vlcMediaPlayer)
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        LibVlcBindings.libvlc_media_player_set_time(vlcMediaPlayer, (clamped * 1000.0).toLong())
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

    override fun isPlaying(): Boolean =
        if (LibVlcBindings.isAvailable) {
            LibVlcBindings.libvlc_media_player_is_playing(vlcMediaPlayer)
        } else {
            playingFallback
        }

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 2f)
        LibVlcBindings.libvlc_audio_set_volume(vlcMediaPlayer, (volumeLevel * 100f).toInt())
    }

    override fun getVolume(): Float = volumeLevel

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        if (analyzer != null) {
            LibVlcBindings.libvlc_audio_set_format(vlcMediaPlayer, "S16N", 44100, 2)
        }
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdateListener = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEndedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }

    override fun dispose() {
        LibVlcBindings.libvlc_media_player_stop(vlcMediaPlayer)
        LibVlcBindings.libvlc_media_release(currentVlcMedia)
        currentVlcMedia = null
        LibVlcBindings.libvlc_media_player_release(vlcMediaPlayer)
        LibVlcBindings.libvlc_release(vlcInstance)
        super.dispose()
    }
}
