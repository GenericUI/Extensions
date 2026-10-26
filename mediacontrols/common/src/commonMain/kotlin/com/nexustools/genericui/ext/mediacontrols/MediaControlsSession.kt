package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.window.IWindow

/**
 * High-level session controller for system media transport controls, Windows taskbar thumbnail
 * preview buttons, and hardware keyboard media key hooks.
 *
 * Depends strictly on `:common` and can be used independently of `:extensions:media` for any
 * audio, video, animation, slideshow, or presentation playback workflow.
 */
class MediaControlsSession(
    window: IWindow? = null,
    val nativeImpl: MediaControlsNativeImpl = MediaControlsRegistry.createNativeImpl()
) : Disposable {

    private val listeners = mutableListOf<MediaControlListener>()
    private var disposed = false

    val capabilities: MediaControlsCapabilities
        get() = nativeImpl.capabilities

    var boundWindow: IWindow? = window
        set(value) {
            field = value
            if (!disposed) {
                nativeImpl.bindWindow(value)
            }
        }

    var metadata: MediaControlMetadata = MediaControlMetadata()
        set(value) {
            field = value
            if (!disposed) {
                nativeImpl.updateMetadata(value)
            }
        }

    var playbackStatus: MediaPlaybackStatus = MediaPlaybackStatus.STOPPED
        set(value) {
            field = value
            if (!disposed) {
                nativeImpl.updatePlaybackStatus(
                    status = value,
                    positionMs = metadata.positionMs,
                    durationMs = metadata.durationMs,
                    playbackRate = metadata.playbackRate
                )
            }
        }

    var buttonConfig: MediaControlButtonConfig = MediaControlButtonConfig()
        set(value) {
            field = value
            if (!disposed) {
                nativeImpl.updateButtonConfig(value)
            }
        }

    var keyboardMediaKeysEnabled: Boolean = true
        set(value) {
            field = value
            if (!disposed) {
                nativeImpl.setKeyboardMediaKeysEnabled(value)
            }
        }

    var taskbarPreviewControlsEnabled: Boolean = true
        set(value) {
            field = value
            if (!disposed) {
                nativeImpl.setTaskbarPreviewControlsEnabled(value)
            }
        }

    val isActive: Boolean
        get() = !disposed && nativeImpl.isActive()

    override val isDisposed: Boolean
        get() = disposed

    init {
        nativeImpl.setListener { event ->
            dispatchToListeners(event)
        }
        nativeImpl.setKeyboardMediaKeysEnabled(keyboardMediaKeysEnabled)
        nativeImpl.setTaskbarPreviewControlsEnabled(taskbarPreviewControlsEnabled)
        nativeImpl.updateButtonConfig(buttonConfig)
        nativeImpl.updateMetadata(metadata)
        if (window != null) {
            nativeImpl.bindWindow(window)
        }
    }

    fun bindWindow(window: IWindow?) {
        boundWindow = window
    }

    fun activate() {
        if (disposed) return
        nativeImpl.setActive(true)
    }

    fun deactivate() {
        if (disposed) return
        nativeImpl.setActive(false)
    }

    fun updateMetadata(metadata: MediaControlMetadata) {
        this.metadata = metadata
    }

    fun updatePlaybackState(
        status: MediaPlaybackStatus,
        positionMs: Long = metadata.positionMs,
        durationMs: Long = metadata.durationMs,
        playbackRate: Float = metadata.playbackRate
    ) {
        if (disposed) return
        val updatedMeta = metadata.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            playbackRate = playbackRate
        )
        metadata = updatedMeta
        playbackStatus = status
    }

    fun updateButtonConfig(config: MediaControlButtonConfig) {
        this.buttonConfig = config
    }

    fun addListener(listener: MediaControlListener) {
        if (disposed) return
        if (listener !in listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: MediaControlListener) {
        listeners.remove(listener)
    }

    fun dispatchEvent(event: MediaControlEvent) {
        if (disposed || !isActive) return
        if (event.source == MediaControlEventSource.KEYBOARD_MEDIA_KEY && !keyboardMediaKeysEnabled) return
        if (event.source == MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR && !taskbarPreviewControlsEnabled) return
        dispatchToListeners(event)
    }

    fun simulateMediaKey(
        action: MediaControlAction,
        source: MediaControlEventSource = MediaControlEventSource.KEYBOARD_MEDIA_KEY,
        seekPositionMs: Long? = null
    ) {
        if (disposed) return
        nativeImpl.simulateMediaKeyEvent(action, source, seekPositionMs)
    }

    private fun dispatchToListeners(event: MediaControlEvent) {
        val snapshot = listeners.toList()
        for (l in snapshot) {
            l.onMediaControlEvent(event)
        }
    }

    override fun dispose() {
        if (disposed) return
        if (nativeImpl.isActive()) {
            nativeImpl.setActive(false)
        }
        listeners.clear()
        nativeImpl.setListener(null)
        nativeImpl.bindWindow(null)
        nativeImpl.dispose()
        boundWindow = null
        disposed = true
    }
}
