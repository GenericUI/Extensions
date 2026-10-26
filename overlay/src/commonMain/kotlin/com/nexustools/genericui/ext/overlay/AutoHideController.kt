package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.UiTimerScheduler

/**
 * Lifecycle-bound controller managing auto-hide timers, fade transitions (`200ms` default),
 * generic pinning reasons (`setPinned(reason, pinned)`), and Picture-in-Picture suppression.
 */
class AutoHideController(
    var spec: AutoHideSpec = AutoHideSpec(),
    private val targetComponent: AbstractComponent? = null,
    var onVisibilityChanged: ((visible: Boolean, opacity: Float) -> Unit)? = null
) : Disposable {

    private val pinnedReasons = linkedSetOf<String>()
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
            pinnedReasons.remove(normalized)
            if (!isInPictureInPictureMode && !isPinned && isOverlayVisible) {
                scheduleAutoHideTimer()
            }
        }
    }

    fun clearAllPins() {
        if (isDisposed) return
        pinnedReasons.clear()
        if (!isInPictureInPictureMode && isOverlayVisible) {
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
            isOverlayVisible = false
            currentOpacity = 0.0f
            targetComponent?.let { target ->
                target.transition = null
                target.opacity = 0.0f
                target.pointerEventsEnabled = false
                target.isVisible = false
            }
            onVisibilityChanged?.invoke(false, 0.0f)
        } else {
            targetComponent?.isVisible = true
            showOverlay(scheduleHide = !isPinned)
        }
    }

    /**
     * Shows the overlay (with [fadeInMs] CSS transition) and optionally starts the [delayMs] auto-hide timer.
     */
    fun showOverlay(scheduleHide: Boolean = true) {
        if (isDisposed || isInPictureInPictureMode) return
        cancelAutoHideTimer()
        val changed = !isOverlayVisible || currentOpacity < 1.0f
        isOverlayVisible = true
        currentOpacity = 1.0f
        targetComponent?.let { target ->
            target.isVisible = true
            if (spec.fadeInMs > 0) {
                target.transition = "opacity ${spec.fadeInMs}ms ease"
            } else {
                target.transition = null
            }
            target.opacity = 1.0f
            target.pointerEventsEnabled = true
            if (spec.hideCursorWhenOverlayHidden) {
                target.cursor = null
            }
        }
        if (changed) {
            onVisibilityChanged?.invoke(true, 1.0f)
        }
        if (scheduleHide && !isPinned && spec.delayMs > 0) {
            scheduleAutoHideTimer()
        }
    }

    /**
     * Hides the overlay (immediately when [instant] is `true`, or with [fadeOutMs] CSS transition).
     */
    fun hideOverlay(instant: Boolean = false) {
        if (isDisposed) return
        cancelAutoHideTimer()
        if (isPinned && !isInPictureInPictureMode) return
        val changed = isOverlayVisible || currentOpacity > 0.0f
        isOverlayVisible = false
        currentOpacity = 0.0f
        targetComponent?.let { target ->
            if (!instant && spec.fadeOutMs > 0) {
                target.transition = "opacity ${spec.fadeOutMs}ms ease"
            } else {
                target.transition = null
            }
            target.opacity = 0.0f
            target.pointerEventsEnabled = false
            if (spec.hideCursorWhenOverlayHidden) {
                target.cursor = "none"
            }
        }
        if (changed) {
            onVisibilityChanged?.invoke(false, 0.0f)
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

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        cancelAutoHideTimer()
        pinnedReasons.clear()
    }
}
