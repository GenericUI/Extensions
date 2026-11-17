package com.nexustools.genericui.ext.mediacontrols.qt

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlListener
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsStateSupport
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.ext.mediacontrols.linux.LinuxMprisDbusBridge
import com.nexustools.genericui.window.IWindow

/**
 * Qt (`Qt::Key_Media*` + `MPRIS D-Bus` via `libgio`) implementation of
 * [MediaControlsNativeImpl].
 */
class QtMediaControlsImpl : MediaControlsNativeImpl {

    companion object {
        const val QT_KEY_VOLUME_DOWN: Int = 0x01000070
        const val QT_KEY_VOLUME_MUTE: Int = 0x01000071
        const val QT_KEY_VOLUME_UP: Int = 0x01000072
        const val QT_KEY_MEDIA_PLAY: Int = 0x01000080
        const val QT_KEY_MEDIA_STOP: Int = 0x01000081
        const val QT_KEY_MEDIA_PREVIOUS: Int = 0x01000082
        const val QT_KEY_MEDIA_NEXT: Int = 0x01000083
        const val QT_KEY_MEDIA_PAUSE: Int = 0x01000085
        const val QT_KEY_MEDIA_TOGGLE_PLAY_PAUSE: Int = 0x01000086
        const val QT_KEY_AUDIO_FORWARD: Int = 0x01000102
        const val QT_KEY_AUDIO_REWIND: Int = 0x010000C5
    }

    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )

    private val state = MediaControlsStateSupport(capabilities)

    private val mprisBridge = LinuxMprisDbusBridge(
        identity = "GenericUI (Qt)",
        isActiveProvider = { isActive() },
        metadataProvider = { state.currentMetadata },
        statusProvider = { state.currentPlaybackStatus },
        positionMsProvider = { state.currentPositionMs },
        durationMsProvider = { state.currentDurationMs },
        rateProvider = { state.currentPlaybackRate },
        buttonConfigProvider = { state.currentButtonConfig },
        onTransportAction = { action, seekPosMs ->
            state.dispatchEvent(action, MediaControlEventSource.SYSTEM_TRANSPORT, seekPosMs)
        }
    )

    val isMprisDbusActive: Boolean
        get() = mprisBridge.isConnectedToDbus

    override val isDisposed: Boolean
        get() = state.isDisposed

    override fun bindWindow(window: IWindow?) {
        state.bindWindow(window)
    }

    override fun setActive(active: Boolean) {
        if (isDisposed) return
        state.setActive(active)
        if (active) {
            mprisBridge.ensureStarted()
            mprisBridge.emitPropertiesChanged()
        } else {
            mprisBridge.emitPropertiesChanged()
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
            mprisBridge.emitPropertiesChanged()
        }
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        val prevPos = state.currentPositionMs
        state.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        if (isActive()) {
            mprisBridge.emitPropertiesChanged()
            if (kotlin.math.abs(positionMs - prevPos) > 1500L) {
                mprisBridge.emitSeeked(positionMs)
            }
        }
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        state.updateButtonConfig(config)
        if (isActive()) {
            mprisBridge.emitPropertiesChanged()
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

    fun handleQtKeyCode(qtKey: Int): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = when (qtKey) {
            QT_KEY_MEDIA_PLAY -> MediaControlAction.PLAY
            QT_KEY_MEDIA_PAUSE -> MediaControlAction.PAUSE
            QT_KEY_MEDIA_TOGGLE_PLAY_PAUSE -> MediaControlAction.PLAY_PAUSE
            QT_KEY_MEDIA_STOP -> MediaControlAction.STOP
            QT_KEY_MEDIA_NEXT -> MediaControlAction.NEXT
            QT_KEY_MEDIA_PREVIOUS -> MediaControlAction.PREVIOUS
            QT_KEY_AUDIO_FORWARD -> MediaControlAction.FAST_FORWARD
            QT_KEY_AUDIO_REWIND -> MediaControlAction.REWIND
            QT_KEY_VOLUME_MUTE -> MediaControlAction.MUTE_TOGGLE
            QT_KEY_VOLUME_UP -> MediaControlAction.VOLUME_UP
            QT_KEY_VOLUME_DOWN -> MediaControlAction.VOLUME_DOWN
            else -> return false
        }
        return state.dispatchEvent(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun handleXf86AudioKeyName(keyName: String): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = when (keyName) {
            "XF86AudioPlay" -> MediaControlAction.PLAY_PAUSE
            "XF86AudioPause" -> MediaControlAction.PAUSE
            "XF86AudioStop" -> MediaControlAction.STOP
            "XF86AudioNext" -> MediaControlAction.NEXT
            "XF86AudioPrev" -> MediaControlAction.PREVIOUS
            "XF86AudioForward" -> MediaControlAction.FAST_FORWARD
            "XF86AudioRewind" -> MediaControlAction.REWIND
            "XF86AudioMute" -> MediaControlAction.MUTE_TOGGLE
            "XF86AudioRaiseVolume" -> MediaControlAction.VOLUME_UP
            "XF86AudioLowerVolume" -> MediaControlAction.VOLUME_DOWN
            else -> return false
        }
        return state.dispatchEvent(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    override fun dispose() {
        if (isDisposed) return
        mprisBridge.dispose()
        state.markDisposed()
    }
}
