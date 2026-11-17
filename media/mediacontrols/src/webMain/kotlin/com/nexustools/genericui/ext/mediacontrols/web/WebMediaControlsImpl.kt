package com.nexustools.genericui.ext.mediacontrols.web

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlListener
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsStateSupport
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.window.IWindow

internal expect fun webInstallMediaSessionAndKeyListeners(impl: WebMediaControlsImpl)
internal expect fun webUninstallMediaSessionAndKeyListeners(impl: WebMediaControlsImpl)
internal expect fun webSyncMediaSessionMetadata(
    title: String,
    artist: String,
    album: String,
    artworkUrl: String
)
internal expect fun webSyncMediaSessionPlaybackState(
    playbackState: String,
    positionSeconds: Double,
    durationSeconds: Double,
    playbackRate: Double
)

/**
 * Web (`js` and `wasmJs`) implementation of [MediaControlsNativeImpl].
 *
 * Bridges the W3C Media Session API (`navigator.mediaSession.metadata`, `playbackState`,
 * `setPositionState`, and `setActionHandler` for `"play"`, `"pause"`, `"stop"`, `"previoustrack"`,
 * `"nexttrack"`, `"seekto"`, `"seekbackward"`, `"seekforward"`) as well as DOM `keydown` listeners
 * for `"MediaPlayPause"`, `"MediaStop"`, `"MediaTrackNext"`, and `"MediaTrackPrevious"`.
 */
class WebMediaControlsImpl : MediaControlsNativeImpl {

    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )

    private val state = MediaControlsStateSupport(capabilities)

    val currentMetadata: MediaControlMetadata
        get() = state.currentMetadata

    val currentPlaybackStatus: MediaPlaybackStatus
        get() = state.currentPlaybackStatus

    val currentPositionMs: Long
        get() = state.currentPositionMs

    val currentDurationMs: Long
        get() = state.currentDurationMs

    val currentPlaybackRate: Float
        get() = state.currentPlaybackRate

    val currentButtonConfig: MediaControlButtonConfig
        get() = state.currentButtonConfig

    val keyboardMediaKeysEnabled: Boolean
        get() = state.keyboardMediaKeysEnabled

    override val isDisposed: Boolean
        get() = state.isDisposed

    override fun bindWindow(window: IWindow?) {
        state.bindWindow(window)
    }

    override fun setActive(active: Boolean) {
        if (isDisposed) return
        val wasActive = state.isActive()
        state.setActive(active)
        if (active && !wasActive) {
            webInstallMediaSessionAndKeyListeners(this)
            syncWebMediaSession()
        } else if (!active && wasActive) {
            webUninstallMediaSessionAndKeyListeners(this)
        }
    }

    override fun isActive(): Boolean = state.isActive()

    override fun setKeyboardMediaKeysEnabled(enabled: Boolean) {
        state.setKeyboardMediaKeysEnabled(enabled)
    }

    override fun setTaskbarPreviewControlsEnabled(enabled: Boolean) {
        state.setTaskbarPreviewControlsEnabled(enabled)
    }

    override fun updateMetadata(metadata: MediaControlMetadata) {
        state.updateMetadata(metadata)
        if (isActive()) {
            syncWebMediaSession()
        }
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        state.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        if (isActive()) {
            syncWebMediaSession()
        }
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        state.updateButtonConfig(config)
        if (isActive()) {
            webInstallMediaSessionAndKeyListeners(this)
        }
    }

    override fun setListener(listener: MediaControlListener?) {
        state.setListener(listener)
    }

    override fun simulateMediaKeyEvent(
        action: MediaControlAction,
        source: MediaControlEventSource,
        seekPositionMs: Long?
    ) {
        state.dispatchEvent(action, source, seekPositionMs)
    }

    fun handleDomMediaKey(key: String): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = when (key) {
            "MediaPlayPause" -> MediaControlAction.PLAY_PAUSE
            "MediaPlay" -> MediaControlAction.PLAY
            "MediaPause" -> MediaControlAction.PAUSE
            "MediaStop" -> MediaControlAction.STOP
            "MediaTrackNext" -> MediaControlAction.NEXT
            "MediaTrackPrevious" -> MediaControlAction.PREVIOUS
            "MediaFastForward" -> MediaControlAction.FAST_FORWARD
            "MediaRewind" -> MediaControlAction.REWIND
            "AudioVolumeMute" -> MediaControlAction.MUTE_TOGGLE
            "AudioVolumeUp" -> MediaControlAction.VOLUME_UP
            "AudioVolumeDown" -> MediaControlAction.VOLUME_DOWN
            else -> return false
        }
        return state.dispatchEvent(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun handleMediaSessionAction(actionName: String, seekTimeSeconds: Double? = null): Boolean {
        if (!isActive()) return false
        val cfg = state.currentButtonConfig
        val (action, seekMs) = when (actionName) {
            "play" -> if (cfg.playPauseEnabled) MediaControlAction.PLAY to null else return false
            "pause" -> if (cfg.playPauseEnabled) MediaControlAction.PAUSE to null else return false
            "stop" -> if (cfg.stopEnabled) MediaControlAction.STOP to null else return false
            "previoustrack" -> if (cfg.previousEnabled) MediaControlAction.PREVIOUS to null else return false
            "nexttrack" -> if (cfg.nextEnabled) MediaControlAction.NEXT to null else return false
            "seekbackward" -> if (cfg.seekEnabled) MediaControlAction.REWIND to null else return false
            "seekforward" -> if (cfg.seekEnabled) MediaControlAction.FAST_FORWARD to null else return false
            "seekto" -> if (cfg.seekEnabled) {
                MediaControlAction.SEEK_TO to ((seekTimeSeconds ?: 0.0) * 1000.0).toLong()
            } else {
                return false
            }
            else -> return false
        }
        return state.dispatchEvent(action, MediaControlEventSource.SYSTEM_TRANSPORT, seekMs)
    }

    private fun syncWebMediaSession() {
        val meta = state.currentMetadata
        webSyncMediaSessionMetadata(
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            artworkUrl = meta.artworkUrl ?: ""
        )
        val stateStr = when (state.currentPlaybackStatus) {
            MediaPlaybackStatus.PLAYING -> "playing"
            MediaPlaybackStatus.PAUSED -> "paused"
            MediaPlaybackStatus.STOPPED, MediaPlaybackStatus.LOADING -> "none"
        }
        webSyncMediaSessionPlaybackState(
            playbackState = stateStr,
            positionSeconds = state.currentPositionMs.coerceAtLeast(0L) / 1000.0,
            durationSeconds = state.currentDurationMs.coerceAtLeast(0L) / 1000.0,
            playbackRate = state.currentPlaybackRate.toDouble().coerceAtLeast(0.1)
        )
    }

    override fun dispose() {
        if (isDisposed) return
        if (isActive()) {
            webUninstallMediaSessionAndKeyListeners(this)
        }
        state.markDisposed()
    }
}
