package com.nexustools.genericui.ext.mediacontrols.kde

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
import com.nexustools.genericui.kde.KdeWindowNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.window.IWindow

/**
 * KDE (KDE multimedia key codes + `MPRIS D-Bus` via `libgio`) implementation of
 * [MediaControlsNativeImpl].
 */
class KdeMediaControlsImpl : MediaControlsNativeImpl {

    companion object {
        const val KDE_KEY_VOLUME_DOWN: Int = 0x01000070
        const val KDE_KEY_VOLUME_MUTE: Int = 0x01000071
        const val KDE_KEY_VOLUME_UP: Int = 0x01000072
        const val KDE_KEY_MEDIA_PLAY: Int = 0x01000080
        const val KDE_KEY_MEDIA_STOP: Int = 0x01000081
        const val KDE_KEY_MEDIA_PREVIOUS: Int = 0x01000082
        const val KDE_KEY_MEDIA_NEXT: Int = 0x01000083
        const val KDE_KEY_MEDIA_PAUSE: Int = 0x01000085
        const val KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE: Int = 0x01000086
        const val KDE_KEY_AUDIO_FORWARD: Int = 0x01000102
        const val KDE_KEY_AUDIO_REWIND: Int = 0x010000C5
    }

    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )

    private val state = MediaControlsStateSupport(capabilities)
    private var windowHookRegistered: Boolean = false

    private val windowMediaKeyHook: (KdeWindowNativeImpl, KeyEvent) -> Boolean = { _, event ->
        interceptWindowKeyEvent(event)
    }

    private val mprisBridge = LinuxMprisDbusBridge(
        identity = "GenericUI (KDE)",
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

    fun handleKdeKeyCode(keyCode: Int): Boolean {
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val action = mapKdeKeyCode(keyCode) ?: return false
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
        mapKdeKeyCode(event.keyCode)
            ?: mapXf86AudioKeyName(event.code)
            ?: mapXf86AudioKeyName(event.key)

    private fun mapKdeKeyCode(keyCode: Int): MediaControlAction? = when (keyCode) {
        KDE_KEY_MEDIA_PLAY -> MediaControlAction.PLAY
        KDE_KEY_MEDIA_PAUSE -> MediaControlAction.PAUSE
        KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE -> MediaControlAction.PLAY_PAUSE
        KDE_KEY_MEDIA_STOP -> MediaControlAction.STOP
        KDE_KEY_MEDIA_NEXT -> MediaControlAction.NEXT
        KDE_KEY_MEDIA_PREVIOUS -> MediaControlAction.PREVIOUS
        KDE_KEY_AUDIO_FORWARD -> MediaControlAction.FAST_FORWARD
        KDE_KEY_AUDIO_REWIND -> MediaControlAction.REWIND
        KDE_KEY_VOLUME_MUTE -> MediaControlAction.MUTE_TOGGLE
        KDE_KEY_VOLUME_UP -> MediaControlAction.VOLUME_UP
        KDE_KEY_VOLUME_DOWN -> MediaControlAction.VOLUME_DOWN
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
            KdeWindowNativeImpl.addGlobalMediaKeyHook(windowMediaKeyHook)
            windowHookRegistered = true
        } else if (!shouldHook && windowHookRegistered) {
            unregisterWindowMediaKeyHook()
        }
    }

    private fun unregisterWindowMediaKeyHook() {
        if (!windowHookRegistered) return
        KdeWindowNativeImpl.removeGlobalMediaKeyHook(windowMediaKeyHook)
        windowHookRegistered = false
    }
}
