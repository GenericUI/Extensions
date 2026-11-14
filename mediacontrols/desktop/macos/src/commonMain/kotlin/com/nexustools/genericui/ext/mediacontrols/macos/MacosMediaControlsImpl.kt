@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.macos

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.threading.GenericUiThreading
import platform.AppKit.NSEvent
import platform.AppKit.NSEventMaskSystemDefined
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import kotlin.math.roundToLong

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
    private var remoteCommandsInstalled: Boolean = false
    private var mediaKeyEventMonitor: Any? = null

    override fun setActive(active: Boolean) {
        super.setActive(active)
        if (active) {
            ensureRemoteCommandsInstalled()
            ensureMediaKeyMonitorInstalled()
        } else {
            runCatching {
                MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
                MPNowPlayingInfoCenter.defaultCenter().playbackState = MPNowPlayingPlaybackStateStopped
            }
        }
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
        syncRemoteCommandButtons(config)
        syncNowPlayingInfo()
    }

    override fun dispose() {
        mediaKeyEventMonitor?.let { monitor ->
            runCatching { NSEvent.removeMonitor(monitor) }
            mediaKeyEventMonitor = null
        }
        runCatching {
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
            MPNowPlayingInfoCenter.defaultCenter().playbackState = MPNowPlayingPlaybackStateStopped
        }
        super.dispose()
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

    private fun ensureRemoteCommandsInstalled() {
        if (remoteCommandsInstalled) return
        remoteCommandsInstalled = true
        runCatching {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            center.playCommand.addTargetWithHandler { _ ->
                if (!isActive()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                GenericUiThreading.runOnLogic { handleRemoteCommand(MediaControlAction.PLAY) }
                MPRemoteCommandHandlerStatusSuccess
            }
            center.pauseCommand.addTargetWithHandler { _ ->
                if (!isActive()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                GenericUiThreading.runOnLogic { handleRemoteCommand(MediaControlAction.PAUSE) }
                MPRemoteCommandHandlerStatusSuccess
            }
            center.togglePlayPauseCommand.addTargetWithHandler { _ ->
                if (!isActive()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                GenericUiThreading.runOnLogic { handleRemoteCommand(MediaControlAction.PLAY_PAUSE) }
                MPRemoteCommandHandlerStatusSuccess
            }
            center.stopCommand.addTargetWithHandler { _ ->
                if (!isActive()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                GenericUiThreading.runOnLogic { handleRemoteCommand(MediaControlAction.STOP) }
                MPRemoteCommandHandlerStatusSuccess
            }
            center.nextTrackCommand.addTargetWithHandler { _ ->
                if (!isActive()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                GenericUiThreading.runOnLogic { handleRemoteCommand(MediaControlAction.NEXT) }
                MPRemoteCommandHandlerStatusSuccess
            }
            center.previousTrackCommand.addTargetWithHandler { _ ->
                if (!isActive()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                GenericUiThreading.runOnLogic { handleRemoteCommand(MediaControlAction.PREVIOUS) }
                MPRemoteCommandHandlerStatusSuccess
            }
            center.changePlaybackPositionCommand.addTargetWithHandler { event ->
                if (!isActive()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                val posEvent = event as? MPChangePlaybackPositionCommandEvent
                    ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                val targetMs = (posEvent.positionTime * 1000.0).roundToLong().coerceAtLeast(0L)
                GenericUiThreading.runOnLogic {
                    handleRemoteCommand(MediaControlAction.SEEK_TO, seekPositionMs = targetMs)
                }
                MPRemoteCommandHandlerStatusSuccess
            }
            syncRemoteCommandButtons(currentButtonConfig)
        }
    }

    private fun syncRemoteCommandButtons(config: MediaControlButtonConfig) {
        runCatching {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            center.playCommand.enabled = config.playPauseEnabled
            center.pauseCommand.enabled = config.playPauseEnabled
            center.togglePlayPauseCommand.enabled = config.playPauseEnabled
            center.stopCommand.enabled = config.stopEnabled
            center.nextTrackCommand.enabled = config.nextEnabled
            center.previousTrackCommand.enabled = config.previousEnabled
            center.changePlaybackPositionCommand.enabled = config.seekEnabled
        }
    }

    private fun ensureMediaKeyMonitorInstalled() {
        if (mediaKeyEventMonitor != null) return
        runCatching {
            mediaKeyEventMonitor = NSEvent.addLocalMonitorForEventsMatchingMask(NSEventMaskSystemDefined) { event ->
                if (event != null) {
                    var consumed = false
                    GenericUiThreading.runOnLogic {
                        consumed = handleSystemDefinedEvent(event.subtype.toInt(), event.data1.toInt())
                    }
                    if (consumed) null else event
                } else {
                    null
                }
            }
        }
    }

    private fun syncNowPlayingInfo() {
        if (!isActive()) {
            nowPlayingInfoDictionary.clear()
            return
        }
        val durationSec = currentDurationMs / 1000.0
        val elapsedSec = currentPositionMs / 1000.0
        val rate = if (currentPlaybackStatus == MediaPlaybackStatus.PLAYING) currentPlaybackRate.toDouble() else 0.0

        nowPlayingInfoDictionary["MPMediaItemPropertyTitle"] = currentMetadata.title
        nowPlayingInfoDictionary["MPMediaItemPropertyArtist"] = currentMetadata.artist
        nowPlayingInfoDictionary["MPMediaItemPropertyAlbumTitle"] = currentMetadata.album
        nowPlayingInfoDictionary["MPMediaItemPropertyPlaybackDuration"] = durationSec
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyElapsedPlaybackTime"] = elapsedSec
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyPlaybackRate"] = rate
        nowPlayingInfoDictionary["MPNowPlayingPlaybackState"] = currentPlaybackStatus.name

        runCatching {
            val center = MPNowPlayingInfoCenter.defaultCenter()
            val appleMap = LinkedHashMap<Any?, Any?>(8)
            appleMap[MPMediaItemPropertyTitle] = currentMetadata.title
            appleMap[MPMediaItemPropertyArtist] = currentMetadata.artist
            appleMap[MPMediaItemPropertyAlbumTitle] = currentMetadata.album
            if (currentDurationMs > 0L) {
                appleMap[MPMediaItemPropertyPlaybackDuration] = durationSec
            }
            appleMap[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSec
            appleMap[MPNowPlayingInfoPropertyPlaybackRate] = rate
            center.nowPlayingInfo = appleMap
            center.playbackState = when (currentPlaybackStatus) {
                MediaPlaybackStatus.PLAYING -> MPNowPlayingPlaybackStatePlaying
                MediaPlaybackStatus.PAUSED -> MPNowPlayingPlaybackStatePaused
                else -> MPNowPlayingPlaybackStateStopped
            }
        }
    }
}
