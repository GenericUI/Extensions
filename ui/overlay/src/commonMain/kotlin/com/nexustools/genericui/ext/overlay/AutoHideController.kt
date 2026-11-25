package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.UiTimerScheduler

/**
 * Lifecycle-bound controller managing auto-hide timers, `.hidden` CSS class toggling on
 * [targetComponent] (driving `@keyframes overlayFadeIn` / `overlayFadeOut` in `overlay/style.scss`),
 * generic pinning reasons (`setPinned(reason, pinned)`), and Picture-in-Picture suppression.
 */
class AutoHideController(
    var spec: AutoHideSpec = AutoHideSpec(),
    var targetComponent: AbstractComponent? = null,
    var onVisibilityChanged: ((visible: Boolean, opacity: Float) -> Unit)? = null
) : Disposable {

    private val pinnedReasons = linkedSetOf<String>()
    internal val visibilityObservers = mutableListOf<(visible: Boolean, opacity: Float) -> Unit>()
    private var activeTimerId: Int = -1

    override var isDisposed: Boolean = false
        private set

    val delayMs: Int
        get() = spec.delayMs

    val fadeInMs: Int
        get() = spec.fadeInMs

    val fadeOutMs: Int
        get() = spec.fadeOutMs

    var isOverlayVisible: Boolean = true
        private set

    var currentOpacity: Float = 1.0f
        private set

    var isInPictureInPictureMode: Boolean = false
        private set

    internal var lastTransitionDurationMs: Int = 0
        private set

    val isPinned: Boolean
        get() = pinnedReasons.isNotEmpty()

    val activePinReasons: Set<String>
        get() = pinnedReasons.toSet()

    /**
     * Pins or unpins the overlay for a generic [reason] (such as `"popover-open"`, `"scrubbing"`, `"curtain"`).
     * While any pin reason is active, the auto-hide timer is paused and the overlay remains visible
     * (unless [isInPictureInPictureMode] is `true`).
     */
    fun setPinned(reason: String, pinned: Boolean) {
        if (isDisposed) return
        val normalized = reason.trim()
        if (normalized.isEmpty()) return
        if (pinned) {
            pinnedReasons.add(normalized)
            cancelAutoHideTimer()
            if (!isInPictureInPictureMode && !isOverlayVisible) {
                showOverlay(scheduleHide = false)
            }
        } else {
            val removed = pinnedReasons.remove(normalized)
            if (removed && !isInPictureInPictureMode && !isPinned && isOverlayVisible) {
                scheduleAutoHideTimer()
            }
        }
    }

    fun clearAllPins() {
        if (isDisposed) return
        val hadPins = pinnedReasons.isNotEmpty()
        pinnedReasons.clear()
        if (hadPins && !isInPictureInPictureMode && isOverlayVisible) {
            scheduleAutoHideTimer()
        }
    }

    /**
     * Enables or disables Picture-in-Picture mode.
     * While in PiP mode (`inPip == true`), the overlay is immediately hidden and disabled.
     */
    fun setPictureInPictureMode(inPip: Boolean) {
        if (isDisposed) return
        isInPictureInPictureMode = inPip
        if (inPip) {
            cancelAutoHideTimer()
            lastTransitionDurationMs = 0
            isOverlayVisible = false
            currentOpacity = 0.0f
            targetComponent?.let { target ->
                target.addClass(INSTANT_FADE_CLASS)
                target.addClass(HIDDEN_CLASS)
                target.setAttribute("data-hud-opacity", "0.0")
                target.isVisible = false
            }
            notifyVisibilityUpdate(visible = false, opacity = 0.0f)
        } else {
            targetComponent?.isVisible = true
            showOverlay(scheduleHide = !isPinned)
        }
    }

    /**
     * Shows [targetComponent] by removing `"hidden"` cleanly and optionally starts the [delayMs]
     * auto-hide countdown.
     */
    fun showOverlay(scheduleHide: Boolean = true) {
        if (isDisposed || isInPictureInPictureMode) return
        cancelAutoHideTimer()
        val changed = !isOverlayVisible || currentOpacity < 1.0f || targetComponent?.hasClass(HIDDEN_CLASS) == true
        val fadeInDuration = spec.fadeInMs.coerceAtLeast(0)
        lastTransitionDurationMs = fadeInDuration
        isOverlayVisible = true
        currentOpacity = 1.0f
        targetComponent?.let { target ->
            target.isVisible = true
            if (fadeInDuration <= 0) {
                target.addClass(INSTANT_FADE_CLASS)
            } else {
                target.removeClass(INSTANT_FADE_CLASS)
            }
            if (!spec.hideCursorWhenOverlayHidden) {
                target.addClass(KEEP_CURSOR_CLASS)
            } else {
                target.removeClass(KEEP_CURSOR_CLASS)
            }
            target.removeClass(HIDDEN_CLASS)
            target.setAttribute("data-hud-opacity", "1.0")
        }
        if (changed) {
            notifyVisibilityUpdate(visible = true, opacity = 1.0f)
        }
        if (scheduleHide && !isPinned && spec.delayMs > 0) {
            scheduleAutoHideTimer()
        }
    }

    /**
     * Hides [targetComponent] by adding `"hidden"`, letting CSS `@keyframes overlayFadeOut`
     * (or instant snap when [instant] is `true` or [AutoHideSpec.fadeOutMs] <= 0) handle opacity,
     * pointer-events, and cursor hiding declaratively.
     */
    fun hideOverlay(instant: Boolean = false) {
        if (isDisposed) return
        cancelAutoHideTimer()
        if (isPinned && !isInPictureInPictureMode) return
        val changed = isOverlayVisible || currentOpacity > 0.0f || targetComponent?.hasClass(HIDDEN_CLASS) != true
        val fadeOutDuration = if (instant) 0 else spec.fadeOutMs.coerceAtLeast(0)
        lastTransitionDurationMs = fadeOutDuration
        isOverlayVisible = false
        currentOpacity = 0.0f
        targetComponent?.let { target ->
            target.isVisible = true
            if (fadeOutDuration <= 0) {
                target.addClass(INSTANT_FADE_CLASS)
            } else {
                target.removeClass(INSTANT_FADE_CLASS)
            }
            if (!spec.hideCursorWhenOverlayHidden) {
                target.addClass(KEEP_CURSOR_CLASS)
            } else {
                target.removeClass(KEEP_CURSOR_CLASS)
            }
            target.addClass(HIDDEN_CLASS)
            target.setAttribute("data-hud-opacity", "0.0")
        }
        if (changed) {
            notifyVisibilityUpdate(visible = false, opacity = 0.0f)
        }
    }

    /**
     * Notifies the controller of user pointer/keyboard activity, showing the overlay and resetting
     * the [delayMs] auto-hide countdown.
     */
    fun notifyUserActivity() {
        if (isDisposed || isInPictureInPictureMode) return
        showOverlay(scheduleHide = !isPinned)
    }

    /**
     * Notifies the controller that the pointer left the overlay host bounds.
     */
    fun notifyPointerLeave() {
        if (isDisposed || isInPictureInPictureMode) return
        if (!isPinned && spec.hideImmediatelyOnPointerLeave) {
            hideOverlay(instant = false)
        }
    }

    /**
     * Toggles overlay visibility in response to a touch tap (respecting [AutoHideSpec.instantHideOnTap]).
     */
    fun toggleVisibilityByTap() {
        if (isDisposed || isInPictureInPictureMode) return
        if (isOverlayVisible) {
            if (!isPinned) {
                hideOverlay(instant = spec.instantHideOnTap)
            }
        } else {
            showOverlay(scheduleHide = !isPinned)
        }
    }

    private fun notifyVisibilityUpdate(visible: Boolean, opacity: Float) {
        onVisibilityChanged?.invoke(visible, opacity)
        notifyVisibilityObservers(visible, opacity)
    }

    private fun scheduleAutoHideTimer() {
        cancelAutoHideTimer()
        if (isDisposed || isInPictureInPictureMode || isPinned || spec.delayMs <= 0) return
        val timerCallback = {
            activeTimerId = -1
            if (!isDisposed && !isPinned) {
                hideOverlay(instant = false)
            }
        }
        activeTimerId = targetComponent?.setTimeout(spec.delayMs, timerCallback)
            ?: UiTimerScheduler.setTimeout(spec.delayMs, timerCallback)
    }

    fun cancelAutoHideTimer() {
        val id = activeTimerId
        activeTimerId = -1
        if (id > 0) {
            targetComponent?.clearTimeout(id)
            UiTimerScheduler.clearTimer(id)
        }
    }

    private fun notifyVisibilityObservers(visible: Boolean, opacity: Float) {
        for (observer in visibilityObservers.toList()) {
            observer(visible, opacity)
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        cancelAutoHideTimer()
        targetComponent?.cancelAnimations()
        visibilityObservers.clear()
        pinnedReasons.clear()
    }

    companion object {
        const val HIDDEN_CLASS: String = "hidden"
        const val INSTANT_FADE_CLASS: String = "overlay-instant-fade"
        const val KEEP_CURSOR_CLASS: String = "overlay-keep-cursor"
    }
}
