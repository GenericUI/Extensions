package com.nexustools.genericui.ext.mediacontrols.qt

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities

/**
 * Qt (`Qt::Key_Media*` + `MPRIS D-Bus`) implementation of
 * [com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl].
 */
class QtMediaControlsImpl : DefaultNoOpMediaControlsNativeImpl(
    capabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )
) {
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

    fun handleQtKeyCode(qtKey: Int): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
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
