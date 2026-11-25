package com.nexustools.genericui.ext.mediacontrols.gtk

import com.nexustools.genericui.event.KeyEvent
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
import com.nexustools.genericui.gtk.GtkWindowNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.window.IWindow

/**
 * Linux GTK / X11 / Wayland (`MPRIS D-Bus` via `libgio` + `GDK_KEY_Audio*` / `XF86Audio*`)
 * implementation of [MediaControlsNativeImpl].
 */
class GtkMediaControlsImpl : MediaControlsNativeImpl {

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

    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )

    private val state = MediaControlsStateSupport(capabilities)
    private var windowHookRegistered: Boolean = false

    private val windowMediaKeyHook: (GtkWindowNativeImpl, KeyEvent) -> Boolean = { _, event ->
        interceptWindowKeyEvent(event)
    }

    private val mprisBridge = LinuxMprisDbusBridge(
        identity = "GenericUI (GTK)",
        isActiveProvider = { isActive() },
        metadataProvider = { state.currentMetadata },
        statusProvider = { state.currentPlaybackStatus },
        positionMsProvider = { state.currentPositionMs },
        durationMsProvider = { state.currentDurationMs },
        rateProvider = { state.currentPlaybackRate },
        buttonConfigProvider = { state.currentButtonConfig },
        onTransportAction = { action, seekPosMs ->
            handleMprisTransportAction(action, seekPosMs)
        }
    )

    val isMprisDbusActive: Boolean
        get() = mprisBridge.isConnectedToDbus

    override val isDisposed: Boolean
        get() = state.isDisposed

    override fun bindWindow(window: IWindow?) {
        state.bindWindow(window)
        syncWindowMediaKeyHook()
    }

    override fun setActive(active: Boolean) {
        if (isDisposed) return
        state.setActive(active)
        syncWindowMediaKeyHook()
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
        syncWindowMediaKeyHook()
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

    fun handleGdkKeySym(keySym: Int): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = mapGdkKeySym(keySym) ?: return false
        return state.dispatchWithCrossSourceDedupe(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun handleXf86AudioKeyName(keyName: String): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = mapXf86AudioKeyName(keyName) ?: return false
        return state.dispatchWithCrossSourceDedupe(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun handleWindowKeyEvent(event: KeyEvent): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled || event.isRepeat) return false
        val action = resolveWindowKeyAction(event) ?: return false
        return state.dispatchWithCrossSourceDedupe(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun handleMprisTransportAction(action: MediaControlAction, seekPositionMs: Long? = null): Boolean =
        state.dispatchWithCrossSourceDedupe(action, MediaControlEventSource.SYSTEM_TRANSPORT, seekPositionMs)

    override fun dispose() {
        if (isDisposed) return
        unregisterWindowMediaKeyHook()
        mprisBridge.dispose()
        state.markDisposed()
    }

    private fun interceptWindowKeyEvent(event: KeyEvent): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled || event.isRepeat) return false
        val action = resolveWindowKeyAction(event) ?: return false
        GenericUiThreading.runOnLogic {
            state.dispatchWithCrossSourceDedupe(action, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
        }
        return true
    }

    private fun resolveWindowKeyAction(event: KeyEvent): MediaControlAction? =
        mapGdkKeySym(event.keyCode)
            ?: mapXf86AudioKeyName(event.code)
            ?: mapXf86AudioKeyName(event.key)

    private fun mapGdkKeySym(keySym: Int): MediaControlAction? = when (keySym) {
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
        else -> null
    }

    private fun mapXf86AudioKeyName(keyName: String): MediaControlAction? = when (keyName) {
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
        else -> null
    }

    private fun syncWindowMediaKeyHook() {
        val shouldHook = isActive() && state.keyboardMediaKeysEnabled
        if (shouldHook && !windowHookRegistered) {
            GtkWindowNativeImpl.addGlobalMediaKeyHook(windowMediaKeyHook)
            windowHookRegistered = true
        } else if (!shouldHook && windowHookRegistered) {
            unregisterWindowMediaKeyHook()
        }
    }

    private fun unregisterWindowMediaKeyHook() {
        if (!windowHookRegistered) return
        GtkWindowNativeImpl.removeGlobalMediaKeyHook(windowMediaKeyHook)
        windowHookRegistered = false
    }
}
