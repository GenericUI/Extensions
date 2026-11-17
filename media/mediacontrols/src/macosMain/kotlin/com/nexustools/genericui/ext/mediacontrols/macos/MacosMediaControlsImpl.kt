@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.macos

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlListener
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsStateSupport
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.ext.mediacontrols.apple.AppleNowPlayingBridge
import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.window.IWindow
import platform.AppKit.NSEvent
import platform.AppKit.NSEventMaskSystemDefined

/**
 * macOS implementation of [MediaControlsNativeImpl].
 *
 * Bridges macOS Now Playing (`MPNowPlayingInfoCenter` / `MPRemoteCommandCenter`), Control Center,
 * Touch Bar, and `NSEvent` `NSSystemDefined` (`subtype == 8`, `NX_KEYTYPE_*`) hardware media key
 * monitoring.
 */
class MacosMediaControlsImpl : MediaControlsNativeImpl {

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

    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )

    private val state = MediaControlsStateSupport(capabilities)

    private val nowPlayingBridge = AppleNowPlayingBridge(
        isActiveProvider = { isActive() },
        onRemoteCommand = { action, seekPosMs ->
            handleRemoteCommand(action, seekPosMs)
        }
    )

    val nowPlayingInfoDictionary: MutableMap<String, Any?>
        get() = nowPlayingBridge.nowPlayingInfoDictionary

    private var mediaKeyEventMonitor: Any? = null

    override val isDisposed: Boolean
        get() = state.isDisposed

    override fun bindWindow(window: IWindow?) {
        state.bindWindow(window)
    }

    override fun setActive(active: Boolean) {
        if (isDisposed) return
        state.setActive(active)
        if (active) {
            nowPlayingBridge.ensureRemoteCommandsInstalled(state.currentButtonConfig)
            ensureMediaKeyMonitorInstalled()
            syncNowPlaying()
        } else {
            nowPlayingBridge.clearNowPlayingInfo()
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
        syncNowPlaying()
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        state.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        syncNowPlaying()
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        state.updateButtonConfig(config)
        nowPlayingBridge.syncRemoteCommandButtons(config)
        syncNowPlaying()
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

    override fun dispose() {
        if (isDisposed) return
        mediaKeyEventMonitor?.let { monitor ->
            runCatching { NSEvent.removeMonitor(monitor) }
            mediaKeyEventMonitor = null
        }
        nowPlayingBridge.dispose()
        state.markDisposed()
    }

    /**
     * Handles an `NSEvent` of type `NSSystemDefined` (`subtype == 8`) carrying `data1` encoded
     * with `keyCode = (data1 and 0xFFFF0000) shr 16` and `keyFlags = data1 and 0x0000FFFF`.
     */
    fun handleSystemDefinedEvent(subtype: Int, data1: Int): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        if (subtype != NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS) return false
        val keyCode = (data1 ushr 16) and 0xFFFF
        val keyState = (data1 and 0xFF00) ushr 8
        if (keyState != NX_KEYSTATE_DOWN) return false
        return handleNxKeyType(keyCode)
    }

    fun handleNxKeyType(nxKeyType: Int): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
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
        return state.dispatchEvent(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun handleRemoteCommand(action: MediaControlAction, seekPositionMs: Long? = null): Boolean =
        state.dispatchEvent(action, MediaControlEventSource.SYSTEM_TRANSPORT, seekPositionMs)

    private fun syncNowPlaying() {
        nowPlayingBridge.syncNowPlayingInfo(
            metadata = state.currentMetadata,
            status = state.currentPlaybackStatus,
            positionMs = state.currentPositionMs,
            durationMs = state.currentDurationMs,
            playbackRate = state.currentPlaybackRate
        )
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
}
