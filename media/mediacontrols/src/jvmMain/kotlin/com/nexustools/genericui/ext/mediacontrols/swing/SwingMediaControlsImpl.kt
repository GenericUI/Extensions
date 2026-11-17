package com.nexustools.genericui.ext.mediacontrols.swing

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlListener
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsStateSupport
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.swing.SwingWindowImpl
import com.nexustools.genericui.window.IWindow
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * JVM Swing implementation of [MediaControlsNativeImpl].
 *
 * Manages an AWT/Swing media session state bound to the target [SwingWindowImpl] and installs a
 * [KeyboardFocusManager.addKeyEventDispatcher] hook to intercept multimedia keys (`0xAD..0xB3`,
 * `XF86Audio*` extended key codes, and `KeyEvent.VK_AGAIN`) when active.
 */
class SwingMediaControlsImpl : MediaControlsNativeImpl {

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

        private val sessionLock = Any()
        private val activeSessionRegistry = mutableListOf<SwingMediaControlsImpl>()

        fun activeSessions(): List<SwingMediaControlsImpl> = synchronized(sessionLock) {
            activeSessionRegistry.toList()
        }

        private fun registerActiveSession(session: SwingMediaControlsImpl) {
            synchronized(sessionLock) {
                if (!activeSessionRegistry.contains(session)) {
                    activeSessionRegistry.add(session)
                }
            }
        }

        private fun unregisterActiveSession(session: SwingMediaControlsImpl) {
            synchronized(sessionLock) {
                activeSessionRegistry.remove(session)
            }
        }
    }

    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )

    private val state = MediaControlsStateSupport(capabilities)
    private var dispatcherInstalled = false

    val boundWindow: IWindow?
        get() = state.boundWindow

    val currentMetadata: MediaControlMetadata
        get() = state.currentMetadata

    val currentPlaybackStatus: MediaPlaybackStatus
        get() = state.currentPlaybackStatus

    val currentPositionMs: Long
        get() = state.currentPositionMs

    val currentDurationMs: Long
        get() = state.currentDurationMs

    val currentPlaybackRate: Float
        get() = state.currentPlaybackRate

    val currentButtonConfig: MediaControlButtonConfig
        get() = state.currentButtonConfig

    val keyboardMediaKeysEnabled: Boolean
        get() = state.keyboardMediaKeysEnabled

    val taskbarPreviewControlsEnabled: Boolean
        get() = state.taskbarPreviewControlsEnabled

    override val isDisposed: Boolean
        get() = state.isDisposed

    private val keyEventDispatcher = KeyEventDispatcher { event ->
        if (event.id == KeyEvent.KEY_PRESSED) {
            handleAwtKeyCodes(event.keyCode, event.extendedKeyCode)
        } else {
            false
        }
    }

    override fun bindWindow(window: IWindow?) {
        state.bindWindow(window)
        syncAwtWindowMediaSessionState()
    }

    override fun setActive(active: Boolean) {
        if (isDisposed) return
        state.setActive(active)
        if (active) {
            registerActiveSession(this)
        } else {
            unregisterActiveSession(this)
        }
        syncDispatcherState()
        syncAwtWindowMediaSessionState()
    }

    override fun isActive(): Boolean = state.isActive()

    override fun setKeyboardMediaKeysEnabled(enabled: Boolean) {
        state.setKeyboardMediaKeysEnabled(enabled)
        syncDispatcherState()
    }

    override fun setTaskbarPreviewControlsEnabled(enabled: Boolean) {
        state.setTaskbarPreviewControlsEnabled(enabled)
    }

    override fun updateMetadata(metadata: MediaControlMetadata) {
        state.updateMetadata(metadata)
        syncAwtWindowMediaSessionState()
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        state.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        syncAwtWindowMediaSessionState()
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        state.updateButtonConfig(config)
        syncAwtWindowMediaSessionState()
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

    fun handleAwtKeyCodes(keyCode: Int, extendedKeyCode: Int = keyCode): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = mapKeyCodeToAction(keyCode) ?: mapKeyCodeToAction(extendedKeyCode) ?: return false
        return state.dispatchEvent(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun dispatchTransportAction(
        action: MediaControlAction,
        seekPositionMs: Long? = null
    ): Boolean {
        if (!isActive()) return false
        val cfg = state.currentButtonConfig
        val allowed = when (action) {
            MediaControlAction.PLAY,
            MediaControlAction.PAUSE,
            MediaControlAction.PLAY_PAUSE -> cfg.playPauseEnabled
            MediaControlAction.NEXT -> cfg.nextEnabled
            MediaControlAction.PREVIOUS -> cfg.previousEnabled
            MediaControlAction.STOP -> cfg.stopEnabled
            MediaControlAction.SEEK_TO,
            MediaControlAction.FAST_FORWARD,
            MediaControlAction.REWIND -> cfg.seekEnabled
            MediaControlAction.MUTE_TOGGLE,
            MediaControlAction.VOLUME_UP,
            MediaControlAction.VOLUME_DOWN -> true
        }
        if (!allowed) return false
        return state.dispatchEvent(action, MediaControlEventSource.SYSTEM_TRANSPORT, seekPositionMs)
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
        val shouldInstall = isActive() && state.keyboardMediaKeysEnabled
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

    private fun syncAwtWindowMediaSessionState() {
        val swingWin = state.boundWindow as? SwingWindowImpl ?: return
        val activeSnapshot = isActive()
        val metadataSnapshot = state.currentMetadata
        val statusSnapshot = state.currentPlaybackStatus
        val positionSnapshot = state.currentPositionMs
        val durationSnapshot = state.currentDurationMs
        val buttonSnapshot = state.currentButtonConfig
        val applyProps: () -> Unit = {
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.active", activeSnapshot)
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.title", metadataSnapshot.title)
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.artist", metadataSnapshot.artist)
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.album", metadataSnapshot.album)
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.status", statusSnapshot.name)
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.positionMs", positionSnapshot)
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.durationMs", durationSnapshot)
            swingWin.nativeWidget.putClientProperty("genericui.mediaSession.buttons", buttonSnapshot)
            swingWin.jFrame?.rootPane?.putClientProperty("genericui.mediaSession.active", activeSnapshot)
            swingWin.jFrame?.rootPane?.putClientProperty("genericui.mediaSession.status", statusSnapshot.name)
        }
        runCatching {
            if (SwingUtilities.isEventDispatchThread()) {
                applyProps()
            } else {
                applyProps()
            }
        }
    }

    override fun dispose() {
        if (isDisposed) return
        unregisterActiveSession(this)
        if (dispatcherInstalled) {
            runCatching {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher)
            }
            dispatcherInstalled = false
        }
        state.markDisposed()
    }
}
