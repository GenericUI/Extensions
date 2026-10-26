package com.nexustools.genericui.ext.mediacontrols.gtk

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities

/**
 * Linux GTK / X11 / Wayland (`MPRIS D-Bus` + `GDK_KEY_Audio*` / `XF86Audio*`) implementation of
 * [com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl].
 */
class GtkMediaControlsImpl : DefaultNoOpMediaControlsNativeImpl(
    capabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )
) {
    companion object {
        const val GDK_KEY_AUDIO_LOWER_VOLUME: Int = 0x1008FF11
        const val GDK_KEY_AUDIO_MUTE: Int = 0x1008FF12
        const val GDK_KEY_AUDIO_RAISE_VOLUME: Int = 0x1008FF13
        const val GDK_KEY_AUDIO_PLAY: Int = 0x1008FF14
        const val GDK_KEY_AUDIO_STOP: Int = 0x1008FF15
        const val GDK_KEY_AUDIO_PREV: Int = 0x1008FF16
        const val GDK_KEY_AUDIO_NEXT: Int = 0x1008FF17
        const val GDK_KEY_AUDIO_PAUSE: Int = 0x1008FF31
        const val GDK_KEY_AUDIO_REWIND: Int = 0x1008FF3E
        const val GDK_KEY_AUDIO_FORWARD: Int = 0x1008FF97
    }

    fun handleGdkKeySym(keySym: Int): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
        val action = when (keySym) {
            GDK_KEY_AUDIO_PLAY -> MediaControlAction.PLAY_PAUSE
            GDK_KEY_AUDIO_PAUSE -> MediaControlAction.PAUSE
            GDK_KEY_AUDIO_STOP -> MediaControlAction.STOP
            GDK_KEY_AUDIO_NEXT -> MediaControlAction.NEXT
            GDK_KEY_AUDIO_PREV -> MediaControlAction.PREVIOUS
            GDK_KEY_AUDIO_FORWARD -> MediaControlAction.FAST_FORWARD
            GDK_KEY_AUDIO_REWIND -> MediaControlAction.REWIND
            GDK_KEY_AUDIO_MUTE -> MediaControlAction.MUTE_TOGGLE
            GDK_KEY_AUDIO_RAISE_VOLUME -> MediaControlAction.VOLUME_UP
            GDK_KEY_AUDIO_LOWER_VOLUME -> MediaControlAction.VOLUME_DOWN
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

    fun handleXf86AudioKeyName(keyName: String): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
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
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                source = MediaControlEventSource.KEYBOARD_MEDIA_KEY
            )
        )
        return true
    }
}
