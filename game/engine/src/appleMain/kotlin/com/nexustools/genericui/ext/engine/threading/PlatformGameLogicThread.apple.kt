@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.engine.threading

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_specific
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_set_specific
import platform.darwin.dispatch_time

/**
 * Apple (`appleMain` — macOS & iOS) implementation of [PlatformGameLogicThread] backed by a
 * dedicated serial GCD queue (`"com.nexustools.genericui.game.logic"`) with `dispatch_get_specific`
 * thread/queue identity checks.
 */
actual object PlatformGameLogicThread {
    actual val threadName: String = "com.nexustools.genericui.game.logic"

    private val logicSpecificStorage: IntVar = nativeHeap.alloc()

    @Suppress("CAST_NEVER_SUCCEEDS")
    private val logicSpecificKey: COpaquePointer = logicSpecificStorage.ptr

    private val logicQueue = dispatch_queue_create(threadName, null).also { queue ->
        dispatch_queue_set_specific(queue, logicSpecificKey, logicSpecificKey, null)
    }

    actual fun isOnDedicatedThread(): Boolean =
        dispatch_get_specific(logicSpecificKey) == logicSpecificKey

    actual fun dispatch(task: () -> Unit) {
        dispatch_async(logicQueue) {
            task()
        }
    }

    actual fun dispatchDelayed(delayMs: Long, task: () -> Unit) {
        val delayNs = delayMs.coerceAtLeast(0L) * 1_000_000L
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayNs), logicQueue) {
            task()
        }
    }

    actual fun shutdown() {
        // Serial GCD queues are ARC-managed; pending queued tasks remain safe to drain.
    }
}
