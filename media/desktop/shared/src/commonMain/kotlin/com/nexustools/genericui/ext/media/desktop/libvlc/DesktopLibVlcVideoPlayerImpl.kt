@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.toCPointer

/**
 * Shared desktop [VideoPlayerNativeImpl] backed directly by native `libvlc` ([LibVlcBindings]),
 * shared by `:extensions:media:desktop:qt`, `:extensions:media:desktop:gtk`,
 * `:extensions:media:desktop:windows`, and `:extensions:media:desktop:macos`.
 */
open class DesktopLibVlcVideoPlayerImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    val platformName: String = "Desktop",
    libVlcArgs: List<String> = listOf("--no-video-title-show", "--quiet")
) : DesktopBaseNativeImpl<W>(nativeWidget, "libvlc-video-player"), VideoPlayerNativeImpl<W> {

    protected val vlcInstance: COpaquePointer? = LibVlcBindings.libvlc_new(libVlcArgs)
    protected val vlcMediaPlayer: COpaquePointer? = LibVlcBindings.libvlc_media_player_new(vlcInstance)
    protected var currentVlcMedia: COpaquePointer? = null

    private var currentSecFallback: Double = 0.0
    private var durationSecOverride: Double? = null
    private var playingFallback: Boolean = false
    private var playbackRate: Float = 1.0f
    private var volumeLevel: Float = 1.0f
    private var mutedState: Boolean = false
    private var resizeModeState: VideoResizeMode = VideoResizeMode.FIT

    private val audioTracksList = mutableListOf(
        MediaTrackInfo(id = "1", label = "Default Audio", language = "und", isSelected = true)
    )
    private val subtitleTracksList = mutableListOf<SubtitleTrackInfo>()
    private var attachedAnalyzer: AudioAnalyzer? = null

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onBufferingChangedListener: ((Boolean) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    /**
     * Binds a Win32 `HWND` window handle to the underlying `libvlc_media_player_t*` (`libvlc_media_player_set_hwnd`).
     */
    fun bindWin32Hwnd(hwndAddress: Long) {
        if (hwndAddress != 0L) {
            LibVlcBindings.libvlc_media_player_set_hwnd(vlcMediaPlayer, hwndAddress.toCPointer())
        }
    }

    /**
     * Binds a macOS `NSView*` / `VLCVideoView*` pointer to the underlying `libvlc_media_player_t*` (`libvlc_media_player_set_nsobject`).
     */
    fun bindMacosNsView(nsViewAddress: Long) {
        if (nsViewAddress != 0L) {
            LibVlcBindings.libvlc_media_player_set_nsobject(vlcMediaPlayer, nsViewAddress.toCPointer())
        }
    }

    /**
     * Binds an X11 Window drawable ID to the underlying `libvlc_media_player_t*` (`libvlc_media_player_set_xwindow`).
     */
    fun bindXWindow(xWindowId: UInt) {
        if (xWindowId != 0u) {
            LibVlcBindings.libvlc_media_player_set_xwindow(vlcMediaPlayer, xWindowId)
        }
    }

    override fun getMediaCapabilities(): MediaCapabilitiesReport = MediaCapabilitiesReport(
        videoCodecs = VideoCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
        audioCodecs = AudioCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
        containers = MediaContainer.entries.toSet(),
        supportsHdr10 = true,
        supportsDolbyVision = true,
        supportsHlg = true,
        supportsSurround51 = true,
        supportsSurround71 = true,
        hardwareAccelerationBackend = "libvlc (${LibVlcBindings.libvlc_get_version()}) [$platformName]"
    )

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        nativeWidget.attributes["videoSource"] = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        durationSecOverride = durationOverrideSeconds

        LibVlcBindings.libvlc_media_release(currentVlcMedia)
        val isRemoteOrUri = url.contains("://")
        val media = if (isRemoteOrUri) {
            LibVlcBindings.libvlc_media_new_location(vlcInstance, url)
        } else {
            LibVlcBindings.libvlc_media_new_path(vlcInstance, url)
        }
        if (seekOffsetSeconds > 0.0) {
            LibVlcBindings.libvlc_media_add_option(media, ":start-time=$seekOffsetSeconds")
        }
        currentVlcMedia = media
        LibVlcBindings.libvlc_media_player_set_media(vlcMediaPlayer, media)

        if (durationOverrideSeconds != null) {
            onDurationChangedListener?.invoke(durationOverrideSeconds)
        }
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
        durationSecOverride?.let { return it }
        val vlcLenMs = LibVlcBindings.libvlc_media_player_get_length(vlcMediaPlayer)
        return if (vlcLenMs > 0L) vlcLenMs / 1000.0 else 0.0
    }

    override fun isPlaying(): Boolean =
        if (LibVlcBindings.isAvailable) {
            LibVlcBindings.libvlc_media_player_is_playing(vlcMediaPlayer)
        } else {
            playingFallback
        }

    override fun setPlaybackSpeed(speed: Float) {
        playbackRate = speed.coerceIn(0.1f, 8.0f)
        LibVlcBindings.libvlc_media_player_set_rate(vlcMediaPlayer, playbackRate)
    }

    override fun getPlaybackSpeed(): Float =
        if (LibVlcBindings.isAvailable) {
            LibVlcBindings.libvlc_media_player_get_rate(vlcMediaPlayer)
        } else {
            playbackRate
        }

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 2f)
        LibVlcBindings.libvlc_audio_set_volume(vlcMediaPlayer, (volumeLevel * 100f).toInt())
    }

    override fun getVolume(): Float = volumeLevel

    override fun setMuted(muted: Boolean) {
        mutedState = muted
        LibVlcBindings.libvlc_audio_set_mute(vlcMediaPlayer, if (muted) 1 else 0)
    }

    override fun isMuted(): Boolean =
        if (LibVlcBindings.isAvailable) {
            LibVlcBindings.libvlc_audio_get_mute(vlcMediaPlayer)
        } else {
            mutedState
        }

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeModeState = mode
        when (mode) {
            VideoResizeMode.FIT -> {
                LibVlcBindings.libvlc_video_set_aspect_ratio(vlcMediaPlayer, null)
                LibVlcBindings.libvlc_video_set_scale(vlcMediaPlayer, 0f)
            }
            VideoResizeMode.FILL -> {
                LibVlcBindings.libvlc_video_set_scale(vlcMediaPlayer, 0f)
            }
            VideoResizeMode.STRETCH -> {
                val w = nativeWidget.bounds.width.toInt().coerceAtLeast(16)
                val h = nativeWidget.bounds.height.toInt().coerceAtLeast(9)
                LibVlcBindings.libvlc_video_set_aspect_ratio(vlcMediaPlayer, "$w:$h")
            }
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeModeState

    override fun getAudioTracks(): List<MediaTrackInfo> = audioTracksList.toList()

    override fun selectAudioTrack(trackId: String) {
        val idInt = trackId.toIntOrNull() ?: return
        LibVlcBindings.libvlc_audio_set_track(vlcMediaPlayer, idInt)
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracksList.toList()

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val trackId = (subtitleTracksList.size + 1).toString()
        subtitleTracksList.add(SubtitleTrackInfo(trackId, label, language, externalUrl = vttOrSrtUrl))
        LibVlcBindings.libvlc_media_player_add_slave(
            player = vlcMediaPlayer,
            type = LibVlcBindings.SLAVE_TYPE_SUBTITLE,
            uri = vttOrSrtUrl,
            select = true
        )
    }

    override fun selectSubtitleTrack(trackId: String?) {
        val spuId = trackId?.toIntOrNull() ?: -1
        LibVlcBindings.libvlc_video_set_spu(vlcMediaPlayer, spuId)
    }

    override fun supportsPictureInPicture(): Boolean = true
    override fun enterPictureInPicture(): Boolean = true
    override fun exitPictureInPicture() {}

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        if (analyzer != null) {
            LibVlcBindings.libvlc_audio_set_format(vlcMediaPlayer, "S16N", 44100, 2)
        }
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChangedListener = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEndedListener = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { onBufferingChangedListener = listener }
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
