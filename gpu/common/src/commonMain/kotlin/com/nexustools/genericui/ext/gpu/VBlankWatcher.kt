package com.nexustools.genericui.ext.gpu

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

    /**
     * Optional platform hook invoked whenever the watcher transitions between idle and active.
     */
    var onActiveStateChanged: ((Boolean) -> Unit)? = null

    val hasActiveWatchers: Boolean
        get() = listeners.any { it.active && !it.disposed } || oneShotCallbacks.isNotEmpty()

    /**
     * Registers a continuous [VBlankFrameListener] that is invoked on every VBlank tick.
     */
    fun watch(listener: VBlankFrameListener): VBlankSubscription {
        val wasActive = hasActiveWatchers
        val entry = RegisteredWatcherEntry(listener)
        listeners.add(entry)
        if (!wasActive && hasActiveWatchers) {
            onActiveStateChanged?.invoke(true)
        }
        return object : VBlankSubscription {
            override val isActive: Boolean
                get() = entry.active && !entry.disposed

            override fun pause() {
                val prev = hasActiveWatchers
                entry.active = false
                if (prev != hasActiveWatchers) {
                    onActiveStateChanged?.invoke(hasActiveWatchers)
                }
            }

            override fun resume() {
                if (!entry.disposed) {
                    val prev = hasActiveWatchers
                    entry.active = true
                    if (prev != hasActiveWatchers) {
                        onActiveStateChanged?.invoke(hasActiveWatchers)
                    }
                }
            }

            override fun dispose() {
                val prev = hasActiveWatchers
                entry.disposed = true
                entry.active = false
                listeners.remove(entry)
                if (prev != hasActiveWatchers) {
                    onActiveStateChanged?.invoke(hasActiveWatchers)
                }
            }
        }
    }

    /**
     * Schedules a one-shot callback to run on the next VBlank tick.
     */
    fun requestNextVBlank(callback: (deltaMs: Double, totalElapsedMs: Double, frameNumber: Long) -> Unit) {
        val wasActive = hasActiveWatchers
        oneShotCallbacks.add(callback)
        if (!wasActive && hasActiveWatchers) {
            onActiveStateChanged?.invoke(true)
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
     * Advances the global VBlank watcher by one frame, invoking all registered [watch] listeners
     * and queued [requestNextVBlank] callbacks, and flushing end-of-callback presentation actions.
     */
    fun stepFrame(explicitTimestampMs: Double? = null) {
        val activeListeners = listeners.filter { it.active && !it.disposed }
        val pendingOneShots = if (oneShotCallbacks.isNotEmpty()) {
            val copy = oneShotCallbacks.toList()
            oneShotCallbacks.clear()
            copy
        } else {
            emptyList()
        }

        if (activeListeners.isEmpty() && pendingOneShots.isEmpty() && endOfCallbackActions.isEmpty()) {
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
            for (oneShot in pendingOneShots) {
                oneShot(deltaMs, totalElapsedMs, frameNum)
                flushEndOfCallbackActions()
            }
            flushEndOfCallbackActions()
        } finally {
            isInVBlankCallback = previousInVBlank
        }

        if (!hasActiveWatchers) {
            onActiveStateChanged?.invoke(false)
        }
    }

    /**
     * Resets all listeners, one-shot callbacks, and timer state (useful in tests).
     */
    fun reset() {
        listeners.clear()
        oneShotCallbacks.clear()
        endOfCallbackActions.clear()
        isInVBlankCallback = false
        timer.reset()
    }

    private class RegisteredWatcherEntry(
        val listener: VBlankFrameListener,
        var active: Boolean = true,
        var disposed: Boolean = false
    )
}
