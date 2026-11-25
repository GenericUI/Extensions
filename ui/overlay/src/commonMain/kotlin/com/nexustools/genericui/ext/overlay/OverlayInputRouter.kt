package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerEvent
import kotlin.math.hypot

/**
 * App-wide keyboard shortcut and pointer drag/click/double-click/tap router for an [AbstractOverlay].
 *
 * - Disambiguates stage window-drag gestures (`>= dragSlopPx` movement on primary pointer press)
 *   from debounced single-click play/pause (`250ms` default) and double-click fullscreen toggles.
 * - On touch-primary targets (`isTouchPrimaryMode == true`), single tap toggles overlay visibility
 *   via [AutoHideController.toggleVisibilityByTap] and never triggers stage window drag.
 * - Isolates interactive child controls ([isolateInteractiveComponent] / [notifyIsolatedInteraction])
 *   so button clicks, scrubbing, and embedded titlebar actions never trigger stage drag, play/pause,
 *   or fullscreen toggles.
 * - Ignores keyboard shortcuts whenever a text input is focused ([isTextInputFocusedProvider]).
 */
class OverlayInputRouter(
    var autoHideController: AutoHideController? = null,
    val clickDebounceMs: Int = DEFAULT_CLICK_DEBOUNCE_MS,
    var isTouchPrimaryMode: Boolean = GenericUI.activeFactory?.backendTarget?.isTouchPrimary == true,
    var isTextInputFocusedProvider: () -> Boolean = { false }
) : Disposable {

    var windowDragEnabledProvider: () -> Boolean = { false }
    var onWindowDragMove: (() -> Unit)? = null
    var dragSlopPx: Float = DEFAULT_DRAG_SLOP_PX
    var allowInputInPipOverlay: Boolean = false
    var isInteractiveDragActiveProvider: () -> Boolean = { PointerDragManager.isDragging }

    private val shortcuts = mutableListOf<OverlayShortcutBinding>()
    private var pendingClickTimerId: Int = -1
    private var suppressNextDoubleClickOnce: Boolean = false
    private var suppressDoubleClickResetTimerId: Int = -1

    private var stagePressActive: Boolean = false
    private var stageDragTriggered: Boolean = false
    private var suppressClickAfterStageDrag: Boolean = false
    private var dragClickSuppressResetTimerId: Int = -1
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var downScreenX: Float = 0f
    private var downScreenY: Float = 0f
    private var hasScreenOrigin: Boolean = false

    private var isolatedInteractionActive: Boolean = false
    private var isolatedInteractionResetTimerId: Int = -1
    private var isolatedClickSuppressed: Boolean = false
    private var isolatedClickResetTimerId: Int = -1
    private var isolatedDoubleClickSuppressed: Boolean = false

    private var clickHandledInCurrentTurn: Boolean = false
    private var lastClickSource: AbstractComponent? = null
    private var clickTurnResetTimerId: Int = -1
    private var doubleClickHandledInCurrentTurn: Boolean = false
    private var lastDoubleClickSource: AbstractComponent? = null
    private var doubleClickTurnResetTimerId: Int = -1

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
     * - `ArrowLeft` / `J`: Skip backward `5s`
     * - `ArrowRight` / `L`: Skip forward `5s`
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

    private fun isInputBlockedByPip(): Boolean =
        autoHideController?.isInPictureInPictureMode == true && !allowInputInPipOverlay

    /**
     * Records a primary pointer press on the stage surface for drag-vs-click disambiguation.
     * Never initiates window drag directly on pointer down; movement must exceed [dragSlopPx].
     */
    fun handlePointerDown(event: PointerEvent = PointerEvent()) {
        if (isDisposed) return
        if (isInputBlockedByPip()) {
            stagePressActive = false
            return
        }
        if (!isTouchPrimaryMode || autoHideController?.isOverlayVisible == true) {
            autoHideController?.notifyUserActivity()
        }
        if (isolatedInteractionActive || event.button > 0) {
            stagePressActive = false
            return
        }
        if (event.clickCount >= 2) {
            stagePressActive = false
            stageDragTriggered = false
            suppressClickAfterStageDrag = false
            clearDragClickSuppressResetTimer()
            return
        }
        clearDragClickSuppressResetTimer()
        stagePressActive = true
        stageDragTriggered = false
        suppressClickAfterStageDrag = false
        downX = event.x
        downY = event.y
        downScreenX = event.screenX
        downScreenY = event.screenY
        hasScreenOrigin = (event.screenX != 0f || event.screenY != 0f)
    }

    /**
     * Wakes the HUD and initiates a window drag once primary press movement exceeds [dragSlopPx].
     */
    fun handlePointerMove(event: PointerEvent = PointerEvent()) {
        if (isDisposed) return
        if (!isInputBlockedByPip() && (!isTouchPrimaryMode || autoHideController?.isOverlayVisible == true)) {
            autoHideController?.notifyUserActivity()
        }
        if (stagePressActive &&
            !stageDragTriggered &&
            !isTouchPrimaryMode &&
            !isolatedInteractionActive &&
            !isInputBlockedByPip() &&
            windowDragEnabledProvider() &&
            !isInteractiveDragActiveProvider()
        ) {
            val useScreen = hasScreenOrigin && (event.screenX != 0f || event.screenY != 0f)
            val dx = if (useScreen) event.screenX - downScreenX else event.x - downX
            val dy = if (useScreen) event.screenY - downScreenY else event.y - downY
            if (hypot(dx.toDouble(), dy.toDouble()) >= dragSlopPx.toDouble()) {
                stageDragTriggered = true
                stagePressActive = false
                suppressClickAfterStageDrag = true
                clearDragClickSuppressResetTimer()
                cancelPendingClickTimer()
                onWindowDragMove?.invoke()
            }
        }
    }

    /**
     * Ends the active stage press tracking and ensures a post-drag release click is suppressed.
     */
    fun handlePointerUp(event: PointerEvent = PointerEvent()) {
        if (isDisposed) return
        stagePressActive = false
        if (stageDragTriggered) {
            suppressClickAfterStageDrag = true
            stageDragTriggered = false
            scheduleDragClickSuppressResetTimer(0)
        }
        if (isolatedInteractionActive && !isInteractiveDragActiveProvider()) {
            endIsolatedInteraction()
        }
    }

    /**
     * Handles a primary pointer click or touch tap on the overlay background surface.
     */
    fun handlePointerClick(state: OverlayState, actions: OverlayActions) {
        handlePointerClickInternal(source = null, stateProvider = { state }, actionsProvider = { actions })
    }

    /**
     * Handles a primary pointer click or touch tap on the overlay background surface using live
     * [stateProvider] and [actionsProvider] evaluated when the debounce timer fires.
     */
    fun handlePointerClick(stateProvider: () -> OverlayState, actionsProvider: () -> OverlayActions) {
        handlePointerClickInternal(source = null, stateProvider = stateProvider, actionsProvider = actionsProvider)
    }

    private fun handlePointerClickInternal(
        source: AbstractComponent?,
        stateProvider: () -> OverlayState,
        actionsProvider: () -> OverlayActions
    ) {
        if (isDisposed || isInputBlockedByPip()) return
        if (clickHandledInCurrentTurn && (source == null || lastClickSource !== source)) return
        markClickHandledInCurrentTurn(source)
        if (stageDragTriggered || suppressClickAfterStageDrag) {
            if (!stageDragTriggered) {
                suppressClickAfterStageDrag = false
                clearDragClickSuppressResetTimer()
            }
            return
        }
        if (isolatedClickSuppressed) {
            return
        }
        if (isTouchPrimaryMode) {
            autoHideController?.toggleVisibilityByTap()
            return
        }
        autoHideController?.notifyUserActivity()
        cancelPendingClickTimer()
        if (clickDebounceMs <= 0) {
            val liveState = stateProvider()
            actionsProvider().triggerPlayPause(liveState.isPlaying)
        } else {
            pendingClickTimerId = UiTimerScheduler.setTimeout(clickDebounceMs) {
                pendingClickTimerId = -1
                if (!isDisposed && !isInputBlockedByPip()) {
                    val liveState = stateProvider()
                    actionsProvider().triggerPlayPause(liveState.isPlaying)
                }
            }
        }
    }

    /**
     * Handles a pointer double-click on the overlay surface, cancelling any pending debounced single-click
     * and toggling fullscreen.
     */
    fun handlePointerDoubleClick(state: OverlayState, actions: OverlayActions) {
        handlePointerDoubleClickInternal(source = null, stateProvider = { state }, actionsProvider = { actions })
    }

    /**
     * Handles a pointer double-click on the overlay surface using live [stateProvider] and
     * [actionsProvider], cancelling any pending debounced single-click and toggling fullscreen.
     */
    fun handlePointerDoubleClick(stateProvider: () -> OverlayState, actionsProvider: () -> OverlayActions) {
        handlePointerDoubleClickInternal(source = null, stateProvider = stateProvider, actionsProvider = actionsProvider)
    }

    private fun handlePointerDoubleClickInternal(
        source: AbstractComponent?,
        stateProvider: () -> OverlayState,
        actionsProvider: () -> OverlayActions
    ) {
        if (isDisposed || isInputBlockedByPip()) return
        if (doubleClickHandledInCurrentTurn && (source == null || lastDoubleClickSource !== source)) return
        markDoubleClickHandledInCurrentTurn(source)
        val activelyDragging = stageDragTriggered
        val suppressedByStageDrag = activelyDragging || suppressClickAfterStageDrag
        stagePressActive = false
        if (!activelyDragging) {
            stageDragTriggered = false
            suppressClickAfterStageDrag = false
            clearDragClickSuppressResetTimer()
        }
        cancelPendingClickTimer()
        if (suppressedByStageDrag) {
            return
        }
        if (suppressNextDoubleClickOnce || isolatedDoubleClickSuppressed) {
            suppressNextDoubleClickOnce = false
            isolatedDoubleClickSuppressed = false
            clearDoubleClickSuppressResetTimer()
            return
        }
        autoHideController?.notifyUserActivity()
        actionsProvider().onToggleFullscreen?.invoke()
    }

    /**
     * Isolates an interaction handled by embedded chrome (such as [OverlayWindowTitleBar] or its
     * buttons) so stage drag tracking, pending debounced single-click play/pause toggles, and
     * bubbled click / double-click events do not fire on the underlying overlay surface.
     */
    fun notifyIsolatedInteraction(isDoubleClick: Boolean = false, wakeHud: Boolean = true) {
        if (isDisposed) return
        isolatedInteractionActive = true
        stagePressActive = false
        stageDragTriggered = false
        suppressClickAfterStageDrag = false
        clearDragClickSuppressResetTimer()
        cancelPendingClickTimer()
        if (wakeHud && !isInputBlockedByPip()) {
            autoHideController?.notifyUserActivity()
        }
        scheduleIsolatedClickSuppressForCurrentTurn()
        scheduleIsolatedInteractionSafetyResetTimer()
        if (isDoubleClick) {
            suppressNextDoubleClickOnce = true
            isolatedDoubleClickSuppressed = true
            clearDoubleClickSuppressResetTimer()
            suppressDoubleClickResetTimerId = UiTimerScheduler.setTimeout(0) {
                suppressDoubleClickResetTimerId = -1
                suppressNextDoubleClickOnce = false
                isolatedDoubleClickSuppressed = false
            }
        }
    }

    internal fun endIsolatedInteraction() {
        if (isDisposed) return
        isolatedInteractionActive = false
        clearIsolatedInteractionResetTimer()
    }

    fun handlePointerEnter(event: PointerEvent = PointerEvent()) {
        if (isDisposed || isInputBlockedByPip()) return
        autoHideController?.notifyUserActivity()
    }

    fun handlePointerLeave(event: PointerEvent = PointerEvent()) {
        if (isDisposed || isInputBlockedByPip()) return
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
        if (isDisposed || isTextInputFocused || isInputBlockedByPip()) {
            return false
        }
        val matched = shortcuts.firstOrNull { it.matches(event) } ?: return false
        autoHideController?.notifyUserActivity()
        matched.action(state, actions)
        return true
    }

    /**
     * Binds pointer stage-gesture listeners (drag, click, double-click, hover) on [component]
     * without overwriting keyboard listeners. Idempotent per component.
     */
    fun bindStageSurface(
        component: AbstractComponent,
        stateProvider: () -> OverlayState,
        actionsProvider: () -> OverlayActions
    ) {
        if (component.getAttribute(STAGE_SURFACE_ATTR) == "true") return
        component.setAttribute(STAGE_SURFACE_ATTR, "true")

        component.onPointerDown = { event ->
            handlePointerDown(event)
        }
        component.onPointerMove = { event ->
            handlePointerMove(event)
        }
        component.onPointerUp = { event ->
            handlePointerUp(event)
        }
        component.onClick = {
            handlePointerClickInternal(component, stateProvider, actionsProvider)
        }
        component.onDoubleClick = {
            handlePointerDoubleClickInternal(component, stateProvider, actionsProvider)
        }
        component.onPointerEnter = { event ->
            handlePointerEnter(event)
        }
        component.onPointerLeave = { event ->
            handlePointerLeave(event)
        }
    }

    /**
     * Idempotently wraps an interactive control [component] (button, seek bar, volume slider,
     * titlebar, or popover) so pointer presses, moves, releases, clicks, and double-clicks wake
     * the HUD without triggering stage window drag, play/pause, or fullscreen toggles.
     */
    fun isolateInteractiveComponent(component: AbstractComponent) {
        if (component.getAttribute(ISOLATED_ATTR) == "true") return
        component.setAttribute(ISOLATED_ATTR, "true")

        val prevDown = component.onPointerDown
        val prevMove = component.onPointerMove
        val prevUp = component.onPointerUp
        val prevClick = component.onClick
        val prevDoubleClick = component.onDoubleClick

        component.onPointerDown = { event ->
            notifyIsolatedInteraction(isDoubleClick = false, wakeHud = true)
            prevDown?.invoke(event)
        }
        component.onPointerMove = { event ->
            if (!isInputBlockedByPip()) {
                autoHideController?.notifyUserActivity()
            }
            prevMove?.invoke(event)
        }
        component.onPointerUp = { event ->
            try {
                prevUp?.invoke(event)
            } finally {
                scheduleIsolatedClickSuppressForCurrentTurn()
                endIsolatedInteraction()
            }
        }
        component.onClick = {
            notifyIsolatedInteraction(isDoubleClick = false, wakeHud = true)
            try {
                prevClick?.invoke()
            } finally {
                endIsolatedInteraction()
            }
        }
        component.onDoubleClick = {
            notifyIsolatedInteraction(isDoubleClick = true, wakeHud = true)
            try {
                prevDoubleClick?.invoke()
            } finally {
                endIsolatedInteraction()
            }
        }
        if (component is Composite) {
            for (child in component.internalCompositeChildren()) {
                isolateInteractiveComponent(child)
            }
        }
    }

    /**
     * Binds pointer and keyboard listeners on [component] to route events through this [OverlayInputRouter].
     */
    fun bindToComponent(
        component: AbstractComponent,
        stateProvider: () -> OverlayState,
        actionsProvider: () -> OverlayActions
    ) {
        bindStageSurface(component, stateProvider, actionsProvider)
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

    private fun scheduleIsolatedClickSuppressForCurrentTurn() {
        isolatedClickSuppressed = true
        clearIsolatedClickResetTimer()
        isolatedClickResetTimerId = UiTimerScheduler.setTimeout(0) {
            isolatedClickResetTimerId = -1
            isolatedClickSuppressed = false
        }
    }

    private fun scheduleIsolatedInteractionSafetyResetTimer() {
        clearIsolatedInteractionResetTimer()
        isolatedInteractionResetTimerId = UiTimerScheduler.setTimeout(ISOLATED_INTERACTION_FALLBACK_MS) {
            isolatedInteractionResetTimerId = -1
            if (!isInteractiveDragActiveProvider()) {
                isolatedInteractionActive = false
            }
        }
    }

    private fun scheduleDragClickSuppressResetTimer(delayMs: Int) {
        clearDragClickSuppressResetTimer()
        dragClickSuppressResetTimerId = UiTimerScheduler.setTimeout(delayMs) {
            dragClickSuppressResetTimerId = -1
            suppressClickAfterStageDrag = false
            stageDragTriggered = false
        }
    }

    private fun markClickHandledInCurrentTurn(source: AbstractComponent?) {
        clickHandledInCurrentTurn = true
        lastClickSource = source
        val existingId = clickTurnResetTimerId
        clickTurnResetTimerId = -1
        if (existingId > 0) {
            UiTimerScheduler.clearTimer(existingId)
        }
        clickTurnResetTimerId = UiTimerScheduler.setTimeout(0) {
            clickTurnResetTimerId = -1
            clickHandledInCurrentTurn = false
            lastClickSource = null
        }
    }

    private fun markDoubleClickHandledInCurrentTurn(source: AbstractComponent?) {
        doubleClickHandledInCurrentTurn = true
        lastDoubleClickSource = source
        val existingId = doubleClickTurnResetTimerId
        doubleClickTurnResetTimerId = -1
        if (existingId > 0) {
            UiTimerScheduler.clearTimer(existingId)
        }
        doubleClickTurnResetTimerId = UiTimerScheduler.setTimeout(0) {
            doubleClickTurnResetTimerId = -1
            doubleClickHandledInCurrentTurn = false
            lastDoubleClickSource = null
        }
    }

    private fun clearDoubleClickSuppressResetTimer() {
        val id = suppressDoubleClickResetTimerId
        suppressDoubleClickResetTimerId = -1
        if (id > 0) {
            UiTimerScheduler.clearTimer(id)
        }
    }

    private fun clearDragClickSuppressResetTimer() {
        val id = dragClickSuppressResetTimerId
        dragClickSuppressResetTimerId = -1
        if (id > 0) {
            UiTimerScheduler.clearTimer(id)
        }
    }

    private fun clearIsolatedClickResetTimer() {
        val id = isolatedClickResetTimerId
        isolatedClickResetTimerId = -1
        if (id > 0) {
            UiTimerScheduler.clearTimer(id)
        }
    }

    private fun clearIsolatedInteractionResetTimer() {
        val id = isolatedInteractionResetTimerId
        isolatedInteractionResetTimerId = -1
        if (id > 0) {
            UiTimerScheduler.clearTimer(id)
        }
    }

    private fun clearTurnResetTimers() {
        val cId = clickTurnResetTimerId
        clickTurnResetTimerId = -1
        if (cId > 0) UiTimerScheduler.clearTimer(cId)
        val dId = doubleClickTurnResetTimerId
        doubleClickTurnResetTimerId = -1
        if (dId > 0) UiTimerScheduler.clearTimer(dId)
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        stagePressActive = false
        stageDragTriggered = false
        suppressClickAfterStageDrag = false
        isolatedInteractionActive = false
        isolatedClickSuppressed = false
        isolatedDoubleClickSuppressed = false
        suppressNextDoubleClickOnce = false
        clickHandledInCurrentTurn = false
        lastClickSource = null
        doubleClickHandledInCurrentTurn = false
        lastDoubleClickSource = null
        clearDoubleClickSuppressResetTimer()
        clearDragClickSuppressResetTimer()
        clearIsolatedClickResetTimer()
        clearIsolatedInteractionResetTimer()
        clearTurnResetTimers()
        cancelPendingClickTimer()
    }

    companion object {
        const val DEFAULT_CLICK_DEBOUNCE_MS: Int = 250
        const val DEFAULT_DRAG_SLOP_PX: Float = 5f
        const val DEFAULT_SKIP_DELTA_SEC: Double = 5.0
        const val DEFAULT_VOLUME_STEP: Int = 5
        private const val ISOLATED_INTERACTION_FALLBACK_MS: Int = 300
        private const val ISOLATED_ATTR: String = "data-overlay-input-isolated"
        private const val STAGE_SURFACE_ATTR: String = "data-overlay-stage-surface"
    }
}
