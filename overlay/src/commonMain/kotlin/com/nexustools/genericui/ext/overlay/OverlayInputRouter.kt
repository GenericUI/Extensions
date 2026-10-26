package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent

/**
 * App-wide keyboard shortcut and pointer click/double-click/tap router for an [AbstractOverlay].
 *
 * - Debounces single-click play/pause by [clickDebounceMs] (`250ms` default) on System and Web targets
 *   so double-click fullscreen toggles do not inadvertently trigger play/pause.
 * - On touch-primary targets (`isTouchPrimaryMode == true`), single tap toggles overlay visibility
 *   via [AutoHideController.toggleVisibilityByTap].
 * - Ignores keyboard shortcuts whenever a text input is focused ([isTextInputFocusedProvider]).
 */
class OverlayInputRouter(
    var autoHideController: AutoHideController? = null,
    val clickDebounceMs: Int = DEFAULT_CLICK_DEBOUNCE_MS,
    var isTouchPrimaryMode: Boolean = GenericUI.activeFactory?.backendTarget?.isTouchPrimary == true,
    var isTextInputFocusedProvider: () -> Boolean = { false }
) : Disposable {

    private val shortcuts = mutableListOf<OverlayShortcutBinding>()
    private var pendingClickTimerId: Int = -1

    override var isDisposed: Boolean = false
        private set

    init {
        resetDefaultShortcuts()
    }

    val registeredShortcuts: List<OverlayShortcutBinding>
        get() = shortcuts.toList()

    fun registerShortcut(binding: OverlayShortcutBinding) {
        shortcuts.removeAll { it.id == binding.id }
        shortcuts.add(binding)
    }

    fun unregisterShortcut(id: String): Boolean =
        shortcuts.removeAll { it.id == id }

    fun clearShortcuts() {
        shortcuts.clear()
    }

    /**
     * Installs the standard generic media/overlay keyboard shortcuts:
     * - `Space` / `K`: Play/Pause
     * - `ArrowLeft` / `J`: Skip backward `10s`
     * - `ArrowRight` / `L`: Skip forward `10s`
     * - `ArrowUp`: Volume `+5%`
     * - `ArrowDown`: Volume `-5%`
     * - `M`: Toggle mute
     * - `F`: Toggle fullscreen
     */
    fun resetDefaultShortcuts() {
        shortcuts.clear()
        registerShortcut(
            OverlayShortcutBinding(id = "play_pause_space", key = "Space", allowRepeat = false) { state, actions ->
                actions.triggerPlayPause(state.isPlaying)
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "play_pause_k", key = "k", allowRepeat = false) { state, actions ->
                actions.triggerPlayPause(state.isPlaying)
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "seek_backward_left", key = "ArrowLeft") { state, actions ->
                if (actions.onSkipDeltaSec != null) {
                    actions.onSkipDeltaSec.invoke(-DEFAULT_SKIP_DELTA_SEC)
                } else {
                    actions.onSeekToSec?.invoke((state.positionSec - DEFAULT_SKIP_DELTA_SEC).coerceAtLeast(0.0))
                }
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "seek_backward_j", key = "j") { state, actions ->
                if (actions.onSkipDeltaSec != null) {
                    actions.onSkipDeltaSec.invoke(-DEFAULT_SKIP_DELTA_SEC)
                } else {
                    actions.onSeekToSec?.invoke((state.positionSec - DEFAULT_SKIP_DELTA_SEC).coerceAtLeast(0.0))
                }
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "seek_forward_right", key = "ArrowRight") { state, actions ->
                if (actions.onSkipDeltaSec != null) {
                    actions.onSkipDeltaSec.invoke(DEFAULT_SKIP_DELTA_SEC)
                } else {
                    val nextPos = state.positionSec + DEFAULT_SKIP_DELTA_SEC
                    val clamped = if (state.durationSec > 0.0) nextPos.coerceAtMost(state.durationSec) else nextPos
                    actions.onSeekToSec?.invoke(clamped)
                }
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "seek_forward_l", key = "l") { state, actions ->
                if (actions.onSkipDeltaSec != null) {
                    actions.onSkipDeltaSec.invoke(DEFAULT_SKIP_DELTA_SEC)
                } else {
                    val nextPos = state.positionSec + DEFAULT_SKIP_DELTA_SEC
                    val clamped = if (state.durationSec > 0.0) nextPos.coerceAtMost(state.durationSec) else nextPos
                    actions.onSeekToSec?.invoke(clamped)
                }
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "volume_up", key = "ArrowUp") { state, actions ->
                actions.onVolumeChange?.invoke((state.volumePercent + DEFAULT_VOLUME_STEP).coerceIn(0, 100))
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "volume_down", key = "ArrowDown") { state, actions ->
                actions.onVolumeChange?.invoke((state.volumePercent - DEFAULT_VOLUME_STEP).coerceIn(0, 100))
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "mute_toggle", key = "m", allowRepeat = false) { _, actions ->
                actions.onMuteToggle?.invoke()
            }
        )
        registerShortcut(
            OverlayShortcutBinding(id = "fullscreen_toggle", key = "f", allowRepeat = false) { _, actions ->
                actions.onToggleFullscreen?.invoke()
            }
        )
    }

    /**
     * Handles a primary pointer click or touch tap on the overlay background surface.
     */
    fun handlePointerClick(state: OverlayState, actions: OverlayActions) {
        if (isDisposed || autoHideController?.isInPictureInPictureMode == true) return
        if (isTouchPrimaryMode) {
            autoHideController?.toggleVisibilityByTap()
            return
        }
        autoHideController?.notifyUserActivity()
        cancelPendingClickTimer()
        if (clickDebounceMs <= 0) {
            actions.triggerPlayPause(state.isPlaying)
        } else {
            pendingClickTimerId = UiTimerScheduler.setTimeout(clickDebounceMs) {
                pendingClickTimerId = -1
                if (!isDisposed) {
                    actions.triggerPlayPause(state.isPlaying)
                }
            }
        }
    }

    /**
     * Handles a pointer double-click on the overlay surface, cancelling any pending debounced single-click
     * and toggling fullscreen.
     */
    fun handlePointerDoubleClick(state: OverlayState, actions: OverlayActions) {
        if (isDisposed || autoHideController?.isInPictureInPictureMode == true) return
        cancelPendingClickTimer()
        autoHideController?.notifyUserActivity()
        actions.onToggleFullscreen?.invoke()
    }

    fun handlePointerMove(event: PointerEvent = PointerEvent()) {
        if (isDisposed) return
        autoHideController?.notifyUserActivity()
    }

    fun handlePointerEnter(event: PointerEvent = PointerEvent()) {
        if (isDisposed) return
        autoHideController?.notifyUserActivity()
    }

    fun handlePointerLeave(event: PointerEvent = PointerEvent()) {
        if (isDisposed) return
        autoHideController?.notifyPointerLeave()
    }

    /**
     * Dispatches a [KeyEvent] against registered [OverlayShortcutBinding]s unless a text input is focused.
     * Returns `true` if a shortcut matched and was executed.
     */
    fun handleKeyDown(
        event: KeyEvent,
        state: OverlayState,
        actions: OverlayActions,
        isTextInputFocused: Boolean = isTextInputFocusedProvider()
    ): Boolean {
        if (isDisposed || isTextInputFocused || autoHideController?.isInPictureInPictureMode == true) {
            return false
        }
        val matched = shortcuts.firstOrNull { it.matches(event) } ?: return false
        autoHideController?.notifyUserActivity()
        matched.action(state, actions)
        return true
    }

    /**
     * Binds pointer and keyboard listeners on [component] to route events through this [OverlayInputRouter].
     */
    fun bindToComponent(
        component: AbstractComponent,
        stateProvider: () -> OverlayState,
        actionsProvider: () -> OverlayActions
    ) {
        component.onClick = {
            handlePointerClick(stateProvider(), actionsProvider())
        }
        component.onDoubleClick = {
            handlePointerDoubleClick(stateProvider(), actionsProvider())
        }
        component.onPointerMove = { event ->
            handlePointerMove(event)
        }
        component.onPointerEnter = { event ->
            handlePointerEnter(event)
        }
        component.onPointerLeave = { event ->
            handlePointerLeave(event)
        }
        component.onKeyDown = { event ->
            handleKeyDown(event, stateProvider(), actionsProvider())
        }
    }

    fun cancelPendingClickTimer() {
        val id = pendingClickTimerId
        pendingClickTimerId = -1
        if (id > 0) {
            UiTimerScheduler.clearTimer(id)
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        cancelPendingClickTimer()
    }

    companion object {
        const val DEFAULT_CLICK_DEBOUNCE_MS: Int = 250
        const val DEFAULT_SKIP_DELTA_SEC: Double = 10.0
        const val DEFAULT_VOLUME_STEP: Int = 5
    }
}
