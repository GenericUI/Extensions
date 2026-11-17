@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.ios

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
import com.nexustools.genericui.window.IWindow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive

/**
 * Apple iOS & iPadOS (`BackendTarget.IOS` and `BackendTarget.IPadOS`) implementation of
 * [MediaControlsNativeImpl] backed by real `MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`,
 * and `AVAudioSession`.
 */
class IosMediaControlsImpl : MediaControlsNativeImpl {

    companion object {
        const val UI_EVENT_SUBTYPE_REMOTE_PLAY: Int = 100
        const val UI_EVENT_SUBTYPE_REMOTE_PAUSE: Int = 101
        const val UI_EVENT_SUBTYPE_REMOTE_STOP: Int = 102
        const val UI_EVENT_SUBTYPE_REMOTE_TOGGLE_PLAY_PAUSE: Int = 103
        const val UI_EVENT_SUBTYPE_REMOTE_NEXT: Int = 104
        const val UI_EVENT_SUBTYPE_REMOTE_PREVIOUS: Int = 105
        const val UI_EVENT_SUBTYPE_REMOTE_BEGIN_SEEK_BACKWARD: Int = 106
        const val UI_EVENT_SUBTYPE_REMOTE_BEGIN_SEEK_FORWARD: Int = 108
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

    override val isDisposed: Boolean
        get() = state.isDisposed

    override fun bindWindow(window: IWindow?) {
        state.bindWindow(window)
    }

    override fun setActive(active: Boolean) {
        if (isDisposed) return
        state.setActive(active)
        if (active) {
            configureAudioSessionForPlayback(true)
            nowPlayingBridge.ensureRemoteCommandsInstalled(state.currentButtonConfig)
            syncNowPlaying()
        } else {
            nowPlayingBridge.clearNowPlayingInfo()
            configureAudioSessionForPlayback(false)
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

    fun handleRemoteCommand(action: MediaControlAction, seekPositionMs: Long? = null): Boolean =
        state.dispatchEvent(action, MediaControlEventSource.SYSTEM_TRANSPORT, seekPositionMs)

    fun handleRemoteControlEventSubtype(subtype: Int): Boolean {
        if (!isActive()) return false
        val action = when (subtype) {
            UI_EVENT_SUBTYPE_REMOTE_PLAY -> MediaControlAction.PLAY
            UI_EVENT_SUBTYPE_REMOTE_PAUSE -> MediaControlAction.PAUSE
            UI_EVENT_SUBTYPE_REMOTE_STOP -> MediaControlAction.STOP
            UI_EVENT_SUBTYPE_REMOTE_TOGGLE_PLAY_PAUSE -> MediaControlAction.PLAY_PAUSE
            UI_EVENT_SUBTYPE_REMOTE_NEXT -> MediaControlAction.NEXT
            UI_EVENT_SUBTYPE_REMOTE_PREVIOUS -> MediaControlAction.PREVIOUS
            UI_EVENT_SUBTYPE_REMOTE_BEGIN_SEEK_BACKWARD -> MediaControlAction.REWIND
            UI_EVENT_SUBTYPE_REMOTE_BEGIN_SEEK_FORWARD -> MediaControlAction.FAST_FORWARD
            else -> return false
        }
        return state.dispatchEvent(action, MediaControlEventSource.SYSTEM_TRANSPORT)
    }

    fun handleHardwareMediaKeyInput(input: String): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = when (input) {
            "UIKeyInputPlayPause", "MediaPlayPause", " " -> MediaControlAction.PLAY_PAUSE
            "MediaPlay" -> MediaControlAction.PLAY
            "MediaPause" -> MediaControlAction.PAUSE
            "MediaStop" -> MediaControlAction.STOP
            "MediaTrackNext" -> MediaControlAction.NEXT
            "MediaTrackPrevious" -> MediaControlAction.PREVIOUS
            "MediaFastForward" -> MediaControlAction.FAST_FORWARD
            "MediaRewind" -> MediaControlAction.REWIND
            else -> return false
        }
        return state.dispatchEvent(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    override fun dispose() {
        if (isDisposed) return
        nowPlayingBridge.dispose()
        configureAudioSessionForPlayback(false)
        state.markDisposed()
    }

    private fun syncNowPlaying() {
        nowPlayingBridge.syncNowPlayingInfo(
            metadata = state.currentMetadata,
            status = state.currentPlaybackStatus,
            positionMs = state.currentPositionMs,
            durationMs = state.currentDurationMs,
            playbackRate = state.currentPlaybackRate
        )
    }

    private fun configureAudioSessionForPlayback(active: Boolean) {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            if (active) {
                session.setCategory(AVAudioSessionCategoryPlayback, error = null)
                session.setActive(true, error = null)
            } else {
                session.setActive(false, error = null)
            }
        }
    }
}
