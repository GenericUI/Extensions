package com.nexustools.genericui.ext.mediacontrols.web

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus

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
 * Web (`js` and `wasmJs`) implementation of [com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl].
 *
 * Bridges the W3C Media Session API (`navigator.mediaSession.metadata`, `playbackState`,
 * `setPositionState`, and `setActionHandler` for `"play"`, `"pause"`, `"stop"`, `"previoustrack"`,
 * `"nexttrack"`, `"seekto"`, `"seekbackward"`, `"seekforward"`) as well as DOM `keydown` listeners
 * for `"MediaPlayPause"`, `"MediaStop"`, `"MediaTrackNext"`, and `"MediaTrackPrevious"`.
 */
class WebMediaControlsImpl : DefaultNoOpMediaControlsNativeImpl(
    capabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )
) {
    override fun setActive(active: Boolean) {
        val wasActive = isActive()
        super.setActive(active)
        if (active && !wasActive) {
            webInstallMediaSessionAndKeyListeners(this)
            syncWebMediaSession()
        } else if (!active && wasActive) {
            webUninstallMediaSessionAndKeyListeners(this)
        }
    }

    override fun updateMetadata(metadata: MediaControlMetadata) {
        super.updateMetadata(metadata)
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
        super.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        if (isActive()) {
            syncWebMediaSession()
        }
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        super.updateButtonConfig(config)
        if (isActive()) {
            webInstallMediaSessionAndKeyListeners(this)
        }
    }

    fun handleDomMediaKey(key: String): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
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
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                source = MediaControlEventSource.KEYBOARD_MEDIA_KEY
            )
        )
        return true
    }

    fun handleMediaSessionAction(actionName: String, seekTimeSeconds: Double? = null): Boolean {
        if (!isActive()) return false
        val cfg = currentButtonConfig
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
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                seekPositionMs = seekMs,
                source = MediaControlEventSource.SYSTEM_TRANSPORT
            )
        )
        return true
    }

    private fun syncWebMediaSession() {
        val meta = currentMetadata
        webSyncMediaSessionMetadata(
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            artworkUrl = meta.artworkUrl ?: ""
        )
        val stateStr = when (currentPlaybackStatus) {
            MediaPlaybackStatus.PLAYING -> "playing"
            MediaPlaybackStatus.PAUSED -> "paused"
            MediaPlaybackStatus.STOPPED, MediaPlaybackStatus.LOADING -> "none"
        }
        webSyncMediaSessionPlaybackState(
            playbackState = stateStr,
            positionSeconds = currentPositionMs.coerceAtLeast(0L) / 1000.0,
            durationSeconds = currentDurationMs.coerceAtLeast(0L) / 1000.0,
            playbackRate = currentPlaybackRate.toDouble().coerceAtLeast(0.1)
        )
    }

    override fun dispose() {
        if (isActive()) {
            webUninstallMediaSessionAndKeyListeners(this)
        }
        super.dispose()
    }
}
