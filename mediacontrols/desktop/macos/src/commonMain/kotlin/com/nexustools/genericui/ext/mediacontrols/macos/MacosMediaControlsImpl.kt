package com.nexustools.genericui.ext.mediacontrols.macos

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus

/**
 * macOS implementation of [com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl].
 *
 * Bridges macOS Now Playing (`MPNowPlayingInfoCenter` / `MPRemoteCommandCenter`), Control Center,
 * Touch Bar, and `NSEvent` `NSSystemDefined` (`subtype == 8`, `NX_KEYTYPE_*`) hardware media key
 * monitoring.
 */
class MacosMediaControlsImpl : DefaultNoOpMediaControlsNativeImpl(
    capabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )
) {
    companion object {
        const val NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS: Int = 8
        const val NX_KEYSTATE_DOWN: Int = 0x0A
        const val NX_KEYTYPE_SOUND_UP: Int = 0
        const val NX_KEYTYPE_SOUND_DOWN: Int = 1
        const val NX_KEYTYPE_MUTE: Int = 7
        const val NX_KEYTYPE_PLAY: Int = 16
        const val NX_KEYTYPE_NEXT: Int = 17
        const val NX_KEYTYPE_PREVIOUS: Int = 18
        const val NX_KEYTYPE_FAST: Int = 19
        const val NX_KEYTYPE_REWIND: Int = 20
    }

    val nowPlayingInfoDictionary: MutableMap<String, Any?> = mutableMapOf()

    override fun setActive(active: Boolean) {
        super.setActive(active)
        syncNowPlayingInfo()
    }

    override fun updateMetadata(metadata: MediaControlMetadata) {
        super.updateMetadata(metadata)
        syncNowPlayingInfo()
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        super.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        syncNowPlayingInfo()
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        super.updateButtonConfig(config)
        syncNowPlayingInfo()
    }

    /**
     * Handles an `NSEvent` of type `NSSystemDefined` (`subtype == 8`) carrying `data1` encoded
     * with `keyCode = (data1 and 0xFFFF0000) shr 16` and `keyFlags = data1 and 0x0000FFFF`.
     */
    fun handleSystemDefinedEvent(subtype: Int, data1: Int): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
        if (subtype != NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS) return false
        val keyCode = (data1 ushr 16) and 0xFFFF
        val keyState = (data1 and 0xFF00) ushr 8
        if (keyState != NX_KEYSTATE_DOWN) return false
        return handleNxKeyType(keyCode)
    }

    fun handleNxKeyType(nxKeyType: Int): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
        val action = when (nxKeyType) {
            NX_KEYTYPE_PLAY -> MediaControlAction.PLAY_PAUSE
            NX_KEYTYPE_NEXT -> MediaControlAction.NEXT
            NX_KEYTYPE_PREVIOUS -> MediaControlAction.PREVIOUS
            NX_KEYTYPE_FAST -> MediaControlAction.FAST_FORWARD
            NX_KEYTYPE_REWIND -> MediaControlAction.REWIND
            NX_KEYTYPE_MUTE -> MediaControlAction.MUTE_TOGGLE
            NX_KEYTYPE_SOUND_UP -> MediaControlAction.VOLUME_UP
            NX_KEYTYPE_SOUND_DOWN -> MediaControlAction.VOLUME_DOWN
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

    fun handleRemoteCommand(action: MediaControlAction, seekPositionMs: Long? = null): Boolean {
        if (!isActive()) return false
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                seekPositionMs = seekPositionMs,
                source = MediaControlEventSource.SYSTEM_TRANSPORT
            )
        )
        return true
    }

    private fun syncNowPlayingInfo() {
        if (!isActive()) {
            nowPlayingInfoDictionary.clear()
            return
        }
        nowPlayingInfoDictionary["MPMediaItemPropertyTitle"] = currentMetadata.title
        nowPlayingInfoDictionary["MPMediaItemPropertyArtist"] = currentMetadata.artist
        nowPlayingInfoDictionary["MPMediaItemPropertyAlbumTitle"] = currentMetadata.album
        nowPlayingInfoDictionary["MPMediaItemPropertyPlaybackDuration"] = currentDurationMs / 1000.0
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyElapsedPlaybackTime"] = currentPositionMs / 1000.0
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyPlaybackRate"] =
            if (currentPlaybackStatus == MediaPlaybackStatus.PLAYING) currentPlaybackRate.toDouble() else 0.0
        nowPlayingInfoDictionary["MPNowPlayingPlaybackState"] = currentPlaybackStatus.name
    }
}
