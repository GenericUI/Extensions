package com.nexustools.genericui.ext.gpu

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.component.media.VBlankTimer

/**
 * Global VBlank / VSync coordinator and frame watcher for GenericUI applications and surfaces.
 *
 * Provides:
 * - [watch]: Register a continuous [VBlankFrameListener] across any component or application logic.
 * - [requestNextVBlank]: Schedule a one-shot callback to run on the next VBlank tick.
 * - [isInVBlankCallback]: Indicates whether execution is currently inside a VBlank callback
 *   (either a global [VBlankWatcher] callback or an `AbstractCanvas.registerVBlankRender` callback).
 * - [doAtEndOfCurrentVBlank]: Queues an action (such as presenting a dirtied double-buffered
 *   `Canvas2D` surface) to run immediately after the current VBlank callback finishes.
 * - [notifySurfaceDirty]: Delegates to [surfaceSynchronizer] so System targets present at the end of
 *   the current VBlank callback (when `isInVBlankCallback == true`) or on the next VBlank tick
 *   (when `isInVBlankCallback == false`), while Web targets perform no extra work.
 */
object VBlankWatcher {
    val timer: VBlankTimer = VBlankTimer()

    /**
     * `true` while executing inside any VBlank callback (`AbstractCanvas.dispatchVBlankFrame`
     * or [stepFrame]).
     */
    var isInVBlankCallback: Boolean = false
        internal set

    /**
     * Active platform surface synchronizer. Defaults to standard system synchronization behavior
     * (end-of-callback when inside VBlank, or next-VBlank when outside VBlank) and is overridden
     * as a no-op on `:web`.
     */
    var surfaceSynchronizer: VBlankSurfaceSynchronizer = VBlankSurfaceSynchronizer { inVBlank, presentAction ->
        if (inVBlank) {
            doAtEndOfCurrentVBlank(presentAction)
        } else {
            requestNextVBlank { _, _, _ -> presentAction() }
        }
    }

    private val listeners = mutableListOf<RegisteredWatcherEntry>()
    private val oneShotCallbacks = mutableListOf<(Double, Double, Long) -> Unit>()
    private val endOfCallbackActions = mutableListOf<() -> Unit>()
    private val activeCanvases = mutableListOf<AbstractCanvas<*>>()
    private var animatorVBlankId: Int = -1
    private var lastSyncedActiveState: Boolean = false

    /**
     * Optional platform hook invoked whenever the watcher transitions between idle and active.
     */
    var onActiveStateChanged: ((Boolean) -> Unit)? = null

    val hasActiveWatchers: Boolean
        get() = listeners.any { it.active && !it.disposed } ||
            oneShotCallbacks.isNotEmpty() ||
            activeCanvases.any { it.hasActiveHeadlessVBlankRenders }

    val isAnimatorWatchActive: Boolean
        get() = animatorVBlankId > 0

    internal fun registerCanvas(canvas: AbstractCanvas<*>) {
        if (!activeCanvases.contains(canvas)) {
            activeCanvases.add(canvas)
        }
        if (!isInVBlankCallback) {
            syncActiveStateAndAnimator()
        }
    }

    internal fun unregisterCanvas(canvas: AbstractCanvas<*>) {
        if (activeCanvases.remove(canvas) && !isInVBlankCallback) {
            syncActiveStateAndAnimator()
        }
    }

    /**
     * Registers a continuous [VBlankFrameListener] that is invoked on every VBlank tick.
     */
    fun watch(listener: VBlankFrameListener): VBlankSubscription {
        val entry = RegisteredWatcherEntry(listener)
        listeners.add(entry)
        if (!isInVBlankCallback) {
            syncActiveStateAndAnimator()
        }
        return object : VBlankSubscription {
            override val isActive: Boolean
                get() = entry.active && !entry.disposed

            override fun pause() {
                entry.active = false
                if (!isInVBlankCallback) {
                    syncActiveStateAndAnimator()
                }
            }

            override fun resume() {
                if (!entry.disposed) {
                    entry.active = true
                    if (!isInVBlankCallback) {
                        syncActiveStateAndAnimator()
                    }
                }
            }

            override fun dispose() {
                entry.disposed = true
                entry.active = false
                listeners.remove(entry)
                if (!isInVBlankCallback) {
                    syncActiveStateAndAnimator()
                }
            }
        }
    }

    /**
     * Schedules a one-shot callback to run on the next VBlank tick.
     */
    fun requestNextVBlank(callback: (deltaMs: Double, totalElapsedMs: Double, frameNumber: Long) -> Unit) {
        oneShotCallbacks.add(callback)
        if (!isInVBlankCallback) {
            syncActiveStateAndAnimator()
        }
    }

    /**
     * Schedules [action] to execute immediately at the end of the current VBlank callback.
     * If not currently inside a VBlank callback, executes [action] on the next VBlank tick.
     */
    fun doAtEndOfCurrentVBlank(action: () -> Unit) {
        if (isInVBlankCallback) {
            endOfCallbackActions.add(action)
        } else {
            requestNextVBlank { _, _, _ -> action() }
        }
    }

    /**
     * Notifies [surfaceSynchronizer] that a double-buffered surface has been modified.
     */
    fun notifySurfaceDirty(presentAction: () -> Unit) {
        surfaceSynchronizer.onSurfaceDirty(isInVBlankCallback, presentAction)
    }

    /**
     * Executes an action within a VBlank callback scope, automatically flushing any
     * [doAtEndOfCurrentVBlank] actions immediately after [block] finishes.
     */
    inline fun <T> withVBlankCallbackScope(block: () -> T): T {
        val previous = enterVBlankCallbackScope()
        return try {
            block()
        } finally {
            exitVBlankCallbackScope(previous)
        }
    }

    @PublishedApi
    internal fun enterVBlankCallbackScope(): Boolean {
        val prev = isInVBlankCallback
        isInVBlankCallback = true
        return prev
    }

    @PublishedApi
    internal fun exitVBlankCallbackScope(previous: Boolean) {
        try {
            flushEndOfCallbackActions()
        } finally {
            isInVBlankCallback = previous
        }
    }

    /**
     * Flushes all queued [doAtEndOfCurrentVBlank] actions in FIFO order.
     */
    fun flushEndOfCallbackActions() {
        while (endOfCallbackActions.isNotEmpty()) {
            val batch = endOfCallbackActions.toList()
            endOfCallbackActions.clear()
            for (action in batch) {
                action()
            }
        }
    }

    /**
     * Advances the global VBlank watcher by one frame, invoking all registered [watch] listeners,
     * active GPU canvases, and queued [requestNextVBlank] callbacks, and flushing end-of-callback
     * presentation actions.
     */
    fun stepFrame(explicitTimestampMs: Double? = null) {
        val activeListeners = listeners.filter { it.active && !it.disposed }
        val pendingCanvases = activeCanvases.filter { it.hasActiveHeadlessVBlankRenders }
        val pendingOneShots = if (oneShotCallbacks.isNotEmpty()) {
            val copy = oneShotCallbacks.toList()
            oneShotCallbacks.clear()
            copy
        } else {
            emptyList()
        }

        if (activeListeners.isEmpty() && pendingCanvases.isEmpty() && pendingOneShots.isEmpty() && endOfCallbackActions.isEmpty()) {
            syncActiveStateAndAnimator()
            return
        }

        val (deltaMs, totalElapsedMs) = timer.stepFrame(explicitTimestampMs)
        val frameNum = timer.frameNumber

        val previousInVBlank = isInVBlankCallback
        isInVBlankCallback = true
        try {
            for (entry in activeListeners) {
                if (entry.active && !entry.disposed) {
                    entry.listener.onVBlankFrame(deltaMs, totalElapsedMs, frameNum)
                    flushEndOfCallbackActions()
                }
            }
            for (canvas in pendingCanvases) {
                if (canvas.hasActiveHeadlessVBlankRenders) {
                    canvas.dispatchVBlankFrame(explicitTimestampMs)
                    flushEndOfCallbackActions()
                }
            }
            for (oneShot in pendingOneShots) {
                oneShot(deltaMs, totalElapsedMs, frameNum)
                flushEndOfCallbackActions()
            }
            flushEndOfCallbackActions()
        } finally {
            isInVBlankCallback = previousInVBlank
        }

        if (!isInVBlankCallback) {
            syncActiveStateAndAnimator()
        }
    }

    /**
     * Resets all listeners, active canvases, one-shot callbacks, and timer state (useful in tests).
     */
    fun reset() {
        val wasActive = lastSyncedActiveState
        listeners.clear()
        oneShotCallbacks.clear()
        endOfCallbackActions.clear()
        activeCanvases.clear()
        isInVBlankCallback = false
        timer.reset()
        val id = animatorVBlankId
        animatorVBlankId = -1
        if (id > 0) {
            Animator.unwatchVBlank(id)
        }
        lastSyncedActiveState = false
        if (wasActive) {
            onActiveStateChanged?.invoke(false)
        }
    }

    private fun syncActiveStateAndAnimator() {
        val active = hasActiveWatchers
        if (active) {
            if (animatorVBlankId <= 0) {
                animatorVBlankId = Animator.watchVBlank { _, timestampMs, _ ->
                    if (hasActiveWatchers) {
                        stepFrame(timestampMs)
                    } else {
                        syncActiveStateAndAnimator()
                    }
                }
            }
        } else {
            val id = animatorVBlankId
            animatorVBlankId = -1
            if (id > 0) {
                Animator.unwatchVBlank(id)
            }
        }
        if (lastSyncedActiveState != active) {
            lastSyncedActiveState = active
            onActiveStateChanged?.invoke(active)
        }
    }

    private class RegisteredWatcherEntry(
        val listener: VBlankFrameListener,
        var active: Boolean = true,
        var disposed: Boolean = false
    )
}
