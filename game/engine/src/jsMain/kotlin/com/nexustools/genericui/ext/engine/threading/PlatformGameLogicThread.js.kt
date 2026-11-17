package com.nexustools.genericui.ext.engine.threading

import kotlin.js.Promise

private external fun setTimeout(handler: () -> Unit, timeout: Int): Int

/**
 * Kotlin/JS (`jsMain`) implementation of [PlatformGameLogicThread] scheduling ticks asynchronously
 * via browser/runtime microtask (`Promise.resolve().then`) and `setTimeout` queues.
 */
actual object PlatformGameLogicThread {
    actual val threadName: String = "GenericUI-Web-GameLogic-Queue"

    private var activeDispatchDepth: Int = 0
    private var isShutDown: Boolean = false

    actual fun isOnDedicatedThread(): Boolean = activeDispatchDepth > 0

    actual fun dispatch(task: () -> Unit) {
        isShutDown = false
        Promise.resolve(Unit).then {
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
        setTimeout({
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
