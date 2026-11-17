package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.window.IWindow

/**
 * Platform-specific native implementation for system media transport controls, taskbar thumbnail
 * preview transport buttons, and hardware keyboard media key hooks.
 */
interface MediaControlsNativeImpl : Disposable {
    val capabilities: MediaControlsCapabilities

    fun bindWindow(window: IWindow?)

    fun setActive(active: Boolean)

    fun isActive(): Boolean

    fun setKeyboardMediaKeysEnabled(enabled: Boolean)

    fun setTaskbarPreviewControlsEnabled(enabled: Boolean)

    fun updateMetadata(metadata: MediaControlMetadata)

    fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    )

    fun updateButtonConfig(config: MediaControlButtonConfig)

    fun setListener(listener: MediaControlListener?)

    /**
     * Deterministically simulates a media control event from [source] and dispatches it to the
     * registered [MediaControlListener] when active.
     */
    fun simulateMediaKeyEvent(
        action: MediaControlAction,
        source: MediaControlEventSource = MediaControlEventSource.KEYBOARD_MEDIA_KEY,
        seekPositionMs: Long? = null
    )
}
