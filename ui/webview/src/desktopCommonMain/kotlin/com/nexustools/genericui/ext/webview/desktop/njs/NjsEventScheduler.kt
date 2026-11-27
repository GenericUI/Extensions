package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.EventLoop
import com.nexustools.njs.runtime.Undefined
import kotlin.time.TimeSource

/**
 * Single-threaded cooperative scheduler inside the isolated WebView worker managing:
 * - Microtasks & `EventLoop.run()` flushing (Promise chains & `queueMicrotask`)
 * - `setTimeout` / `clearTimeout` / `setInterval` / `clearInterval`
 * - `requestAnimationFrame` / `cancelAnimationFrame`
 * - Deferred observer / transition / animation tick deliveries
 */
class NjsEventScheduler {

    private data class ScheduledTimer(
        val id: Int,
        val dueAtMs: Double,
        val intervalMs: Double?,
        val callback: () -> Unit
    )

    private val startMark = TimeSource.Monotonic.markNow()
    private val microtasks = ArrayList<() -> Unit>()
    private val timers = ArrayList<ScheduledTimer>()
    private val rafCallbacks = LinkedHashMap<Int, (Double) -> Unit>()
    private var nextId: Int = 1

    var onError: ((Throwable) -> Unit)? = null
    var onFrameRenderedTick: (() -> Unit)? = null

    fun nowMs(): Double = startMark.elapsedNow().inWholeMicroseconds / 1000.0

    fun queueMicrotask(task: () -> Unit) {
        EventLoop.queueMicrotask {
            try {
                task()
            } catch (t: Throwable) {
                onError?.invoke(t)
            }
        }
    }

    fun setTimeout(delayMs: Double, callback: () -> Unit): Int {
        val id = nextId++
        val due = nowMs() + delayMs.coerceAtLeast(0.0)
        timers.add(ScheduledTimer(id, due, null, callback))
        timers.sortBy { it.dueAtMs }
        return id
    }

    fun setInterval(intervalMs: Double, callback: () -> Unit): Int {
        val id = nextId++
        val safeInterval = intervalMs.coerceAtLeast(4.0)
        val due = nowMs() + safeInterval
        timers.add(ScheduledTimer(id, due, safeInterval, callback))
        timers.sortBy { it.dueAtMs }
        return id
    }

    fun clearTimer(id: Int) {
        timers.removeAll { it.id == id }
    }

    fun requestAnimationFrame(callback: (Double) -> Unit): Int {
        val id = nextId++
        rafCallbacks[id] = callback
        return id
    }

    fun cancelAnimationFrame(id: Int) {
        rafCallbacks.remove(id)
    }

    fun drainMicrotasks() {
        EventLoop.drainMicrotasks()
    }

    fun hasPendingAnimationFrames(): Boolean = rafCallbacks.isNotEmpty()

    fun hasImminentTimers(withinMs: Double = 250.0): Boolean {
        val cutoff = nowMs() + withinMs
        return timers.any { it.dueAtMs <= cutoff }
    }

    fun stepAnimationFrames() {
        if (rafCallbacks.isEmpty()) return
        val timestamp = nowMs()
        val snapshot = rafCallbacks.values.toList()
        rafCallbacks.clear()
        for (cb in snapshot) {
            try {
                cb(timestamp)
            } catch (t: Throwable) {
                onError?.invoke(t)
            }
        }
        drainMicrotasks()
        onFrameRenderedTick?.invoke()
    }

    fun flushReadyTimersAndPump(maxTimers: Int = 64): Int {
        var executed = 0
        drainMicrotasks()
        while (executed < maxTimers) {
            val now = nowMs()
            val readyIdx = timers.indexOfFirst { it.dueAtMs <= now }
            if (readyIdx < 0) break
            val timer = timers.removeAt(readyIdx)
            if (timer.intervalMs != null) {
                timers.add(timer.copy(dueAtMs = now + timer.intervalMs))
                timers.sortBy { it.dueAtMs }
            }
            try {
                timer.callback()
            } catch (t: Throwable) {
                onError?.invoke(t)
            }
            drainMicrotasks()
            executed++
        }
        if (rafCallbacks.isNotEmpty()) {
            stepAnimationFrames()
        }
        return executed
    }

    fun clearAll() {
        microtasks.clear()
        timers.clear()
        rafCallbacks.clear()
    }

    fun invokeJsCallbackSafely(fn: BaseFunction, _this: BaseObject = Undefined, vararg args: BaseObject) {
        try {
            fn.call(_this, *args)
        } catch (t: Throwable) {
            onError?.invoke(t)
        }
    }
}
