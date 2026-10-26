package com.nexustools.genericui.ext.mediacontrols.swing

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * JVM Swing implementation of [com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl].
 *
 * Uses [KeyboardFocusManager.addKeyEventDispatcher] to intercept multimedia keys (`0xAD..0xB3`,
 * `XF86Audio*` extended key codes, and `KeyEvent.VK_AGAIN`) when active.
 */
class SwingMediaControlsImpl : DefaultNoOpMediaControlsNativeImpl(
    capabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )
) {
    companion object {
        const val RAW_VK_VOLUME_MUTE: Int = 0xAD
        const val RAW_VK_VOLUME_DOWN: Int = 0xAE
        const val RAW_VK_VOLUME_UP: Int = 0xAF
        const val RAW_VK_MEDIA_NEXT_TRACK: Int = 0xB0
        const val RAW_VK_MEDIA_PREV_TRACK: Int = 0xB1
        const val RAW_VK_MEDIA_STOP: Int = 0xB2
        const val RAW_VK_MEDIA_PLAY_PAUSE: Int = 0xB3

        const val XF86_AUDIO_LOWER_VOLUME: Int = 0x1008FF11
        const val XF86_AUDIO_MUTE: Int = 0x1008FF12
        const val XF86_AUDIO_RAISE_VOLUME: Int = 0x1008FF13
        const val XF86_AUDIO_PLAY: Int = 0x1008FF14
        const val XF86_AUDIO_STOP: Int = 0x1008FF15
        const val XF86_AUDIO_PREV: Int = 0x1008FF16
        const val XF86_AUDIO_NEXT: Int = 0x1008FF17
        const val XF86_AUDIO_PAUSE: Int = 0x1008FF31
    }

    private var dispatcherInstalled = false

    private val keyEventDispatcher = KeyEventDispatcher { event ->
        if (event.id == KeyEvent.KEY_PRESSED) {
            handleAwtKeyCodes(event.keyCode, event.extendedKeyCode)
        } else {
            false
        }
    }

    override fun setActive(active: Boolean) {
        super.setActive(active)
        syncDispatcherState()
    }

    override fun setKeyboardMediaKeysEnabled(enabled: Boolean) {
        super.setKeyboardMediaKeysEnabled(enabled)
        syncDispatcherState()
    }

    fun handleAwtKeyCodes(keyCode: Int, extendedKeyCode: Int = keyCode): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
        val action = mapKeyCodeToAction(keyCode) ?: mapKeyCodeToAction(extendedKeyCode) ?: return false
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                source = MediaControlEventSource.KEYBOARD_MEDIA_KEY
            )
        )
        return true
    }

    private fun mapKeyCodeToAction(code: Int): MediaControlAction? = when (code) {
        RAW_VK_MEDIA_PLAY_PAUSE, XF86_AUDIO_PLAY, KeyEvent.VK_AGAIN -> MediaControlAction.PLAY_PAUSE
        XF86_AUDIO_PAUSE -> MediaControlAction.PAUSE
        RAW_VK_MEDIA_STOP, XF86_AUDIO_STOP -> MediaControlAction.STOP
        RAW_VK_MEDIA_NEXT_TRACK, XF86_AUDIO_NEXT -> MediaControlAction.NEXT
        RAW_VK_MEDIA_PREV_TRACK, XF86_AUDIO_PREV -> MediaControlAction.PREVIOUS
        RAW_VK_VOLUME_MUTE, XF86_AUDIO_MUTE -> MediaControlAction.MUTE_TOGGLE
        RAW_VK_VOLUME_UP, XF86_AUDIO_RAISE_VOLUME -> MediaControlAction.VOLUME_UP
        RAW_VK_VOLUME_DOWN, XF86_AUDIO_LOWER_VOLUME -> MediaControlAction.VOLUME_DOWN
        else -> null
    }

    private fun syncDispatcherState() {
        val shouldInstall = isActive() && keyboardMediaKeysEnabled
        runCatching {
            val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            if (shouldInstall && !dispatcherInstalled) {
                kfm.addKeyEventDispatcher(keyEventDispatcher)
                dispatcherInstalled = true
            } else if (!shouldInstall && dispatcherInstalled) {
                kfm.removeKeyEventDispatcher(keyEventDispatcher)
                dispatcherInstalled = false
            }
        }
    }

    override fun dispose() {
        if (dispatcherInstalled) {
            runCatching {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher)
            }
            dispatcherInstalled = false
        }
        super.dispose()
    }
}
