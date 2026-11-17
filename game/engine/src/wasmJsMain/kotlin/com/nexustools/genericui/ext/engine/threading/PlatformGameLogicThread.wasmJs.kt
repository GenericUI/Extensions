@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.engine.threading

@JsFun("(fn) => { Promise.resolve().then(fn); }")
private external fun wasmScheduleMicrotask(fn: () -> Unit)

@JsFun("(fn, ms) => { return setTimeout(fn, ms); }")
private external fun wasmScheduleTimeout(fn: () -> Unit, ms: Int): Int

/**
 * Kotlin/Wasm (`wasmJsMain`) implementation of [PlatformGameLogicThread] scheduling ticks
 * asynchronously via browser/runtime `Promise.resolve().then` and `setTimeout` queues.
 */
actual object PlatformGameLogicThread {
    actual val threadName: String = "GenericUI-Wasm-GameLogic-Queue"

    private var activeDispatchDepth: Int = 0
    private var isShutDown: Boolean = false

    actual fun isOnDedicatedThread(): Boolean = activeDispatchDepth > 0

    actual fun dispatch(task: () -> Unit) {
        isShutDown = false
        wasmScheduleMicrotask {
            if (!isShutDown) {
                activeDispatchDepth++
                try {
                    task()
                } finally {
                    activeDispatchDepth--
                }
            }
        }
    }

    actual fun dispatchDelayed(delayMs: Long, task: () -> Unit) {
        isShutDown = false
        val ms = delayMs.coerceAtLeast(0L).toInt()
        wasmScheduleTimeout({
            if (!isShutDown) {
                activeDispatchDepth++
                try {
                    task()
                } finally {
                    activeDispatchDepth--
                }
            }
        }, ms)
    }

    actual fun shutdown() {
        isShutDown = true
    }
}
