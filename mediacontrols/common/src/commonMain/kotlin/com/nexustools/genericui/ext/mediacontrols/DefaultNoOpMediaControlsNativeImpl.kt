package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.window.IWindow

/**
 * Default no-op implementation of [MediaControlsNativeImpl] used in headless tests and on mobile
 * targets where system keyboard media hooks and taskbar thumbnail buttons are unsupported.
 */
open class DefaultNoOpMediaControlsNativeImpl(
    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities()
) : MediaControlsNativeImpl {

    private var boundWindowState: IWindow? = null
    val boundWindow: IWindow?
        get() = boundWindowState

    private var active: Boolean = false

    private var keyboardMediaKeysEnabledState: Boolean = true
    val keyboardMediaKeysEnabled: Boolean
        get() = keyboardMediaKeysEnabledState

    private var taskbarPreviewControlsEnabledState: Boolean = true
    val taskbarPreviewControlsEnabled: Boolean
        get() = taskbarPreviewControlsEnabledState

    private var currentMetadataState: MediaControlMetadata = MediaControlMetadata()
    val currentMetadata: MediaControlMetadata
        get() = currentMetadataState

    private var currentPlaybackStatusState: MediaPlaybackStatus = MediaPlaybackStatus.STOPPED
    val currentPlaybackStatus: MediaPlaybackStatus
        get() = currentPlaybackStatusState

    private var currentPositionMsState: Long = 0L
    val currentPositionMs: Long
        get() = currentPositionMsState

    private var currentDurationMsState: Long = 0L
    val currentDurationMs: Long
        get() = currentDurationMsState

    private var currentPlaybackRateState: Float = 1.0f
    val currentPlaybackRate: Float
        get() = currentPlaybackRateState

    private var currentButtonConfigState: MediaControlButtonConfig = MediaControlButtonConfig()
    val currentButtonConfig: MediaControlButtonConfig
        get() = currentButtonConfigState

    private var listenerState: MediaControlListener? = null
    protected val listener: MediaControlListener?
        get() = listenerState

    private var disposed: Boolean = false

    override val isDisposed: Boolean
        get() = disposed

    override fun bindWindow(window: IWindow?) {
        if (disposed) return
        boundWindowState = window
    }

    override fun setActive(active: Boolean) {
        if (disposed) return
        this.active = active
    }

    override fun isActive(): Boolean = !disposed && active

    override fun setKeyboardMediaKeysEnabled(enabled: Boolean) {
        if (disposed) return
        keyboardMediaKeysEnabledState = enabled
    }

    override fun setTaskbarPreviewControlsEnabled(enabled: Boolean) {
        if (disposed) return
        taskbarPreviewControlsEnabledState = enabled
    }

    override fun updateMetadata(metadata: MediaControlMetadata) {
        if (disposed) return
        currentMetadataState = metadata
        currentPositionMsState = metadata.positionMs
        currentDurationMsState = metadata.durationMs
        currentPlaybackRateState = metadata.playbackRate
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        if (disposed) return
        currentPlaybackStatusState = status
        currentPositionMsState = positionMs
        currentDurationMsState = durationMs
        currentPlaybackRateState = playbackRate
        currentMetadataState = currentMetadataState.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            playbackRate = playbackRate
        )
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        if (disposed) return
        currentButtonConfigState = config
    }

    override fun setListener(listener: MediaControlListener?) {
        this.listenerState = listener
    }

    override fun simulateMediaKeyEvent(
        action: MediaControlAction,
        source: MediaControlEventSource,
        seekPositionMs: Long?
    ) {
        if (disposed || !active) return
        if (source == MediaControlEventSource.KEYBOARD_MEDIA_KEY && !keyboardMediaKeysEnabledState) return
        if (source == MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR && !taskbarPreviewControlsEnabledState) return
        listenerState?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                seekPositionMs = seekPositionMs,
                source = source
            )
        )
    }

    override fun dispose() {
        if (disposed) return
        active = false
        listenerState = null
        boundWindowState = null
        disposed = true
    }
}
