package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.window.IWindow

/**
 * Shared internal state and event-dispatch helper used by platform [MediaControlsNativeImpl]
 * implementations so common lifecycle, metadata, button configuration, and toggle-flag semantics
 * are never duplicated across backends.
 */
internal class MediaControlsStateSupport(
    val capabilities: MediaControlsCapabilities
) {
    var boundWindow: IWindow? = null
        private set

    var active: Boolean = false
        private set

    var keyboardMediaKeysEnabled: Boolean = true
        private set

    var taskbarPreviewControlsEnabled: Boolean = true
        private set

    var currentMetadata: MediaControlMetadata = MediaControlMetadata()
        private set

    var currentPlaybackStatus: MediaPlaybackStatus = MediaPlaybackStatus.STOPPED
        private set

    var currentPositionMs: Long = 0L
        private set

    var currentDurationMs: Long = 0L
        private set

    var currentPlaybackRate: Float = 1.0f
        private set

    var currentButtonConfig: MediaControlButtonConfig = MediaControlButtonConfig()
        private set

    var listener: MediaControlListener? = null
        private set

    var isDisposed: Boolean = false
        private set

    fun bindWindow(window: IWindow?) {
        if (isDisposed) return
        boundWindow = window
    }

    fun setActive(active: Boolean): Boolean {
        if (isDisposed) return false
        val previous = this.active
        this.active = active
        return previous
    }

    fun isActive(): Boolean = !isDisposed && active

    fun setKeyboardMediaKeysEnabled(enabled: Boolean) {
        if (isDisposed) return
        keyboardMediaKeysEnabled = enabled
    }

    fun setTaskbarPreviewControlsEnabled(enabled: Boolean) {
        if (isDisposed) return
        taskbarPreviewControlsEnabled = enabled
    }

    fun updateMetadata(metadata: MediaControlMetadata) {
        if (isDisposed) return
        currentMetadata = metadata
        currentPositionMs = metadata.positionMs
        currentDurationMs = metadata.durationMs
        currentPlaybackRate = metadata.playbackRate
    }

    fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        if (isDisposed) return
        currentPlaybackStatus = status
        currentPositionMs = positionMs
        currentDurationMs = durationMs
        currentPlaybackRate = playbackRate
        currentMetadata = currentMetadata.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            playbackRate = playbackRate
        )
    }

    fun updateButtonConfig(config: MediaControlButtonConfig) {
        if (isDisposed) return
        currentButtonConfig = config
    }

    fun setListener(listener: MediaControlListener?) {
        this.listener = listener
    }

    fun dispatchEvent(
        action: MediaControlAction,
        source: MediaControlEventSource,
        seekPositionMs: Long? = null
    ): Boolean {
        if (!isActive()) return false
        if (source == MediaControlEventSource.KEYBOARD_MEDIA_KEY && !keyboardMediaKeysEnabled) return false
        if (source == MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR && !taskbarPreviewControlsEnabled) return false
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                seekPositionMs = seekPositionMs,
                source = source
            )
        )
        return true
    }

    fun markDisposed() {
        if (isDisposed) return
        active = false
        listener = null
        boundWindow = null
        isDisposed = true
    }
}
