@file:OptIn(kotlin.native.concurrent.ObsoleteWorkersApi::class)

package com.nexustools.genericui.ext.engine.threading

import kotlin.concurrent.Volatile
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

/**
 * Linux native (`linuxMain`) implementation of [PlatformGameLogicThread] backed by a dedicated
 * Kotlin/Native [Worker] thread.
 */
actual object PlatformGameLogicThread {
    actual val threadName: String = "GenericUI-GameLogic-Thread"

    @Volatile
    private var worker: Worker? = Worker.start(errorReporting = true, name = threadName)

    private fun ensureWorker(): Worker {
        val existing = worker
        if (existing != null) return existing
        val created = Worker.start(errorReporting = true, name = threadName)
        worker = created
        return created
    }

    actual fun isOnDedicatedThread(): Boolean {
        val active = worker ?: return false
        return Worker.current == active
    }

    actual fun dispatch(task: () -> Unit) {
        ensureWorker().execute(TransferMode.SAFE, { task }) { fn ->
            fn()
        }
    }

    actual fun dispatchDelayed(delayMs: Long, task: () -> Unit) {
        val afterMicros = delayMs.coerceAtLeast(0L) * 1000L
        ensureWorker().executeAfter(afterMicroseconds = afterMicros) {
            task()
        }
    }

    actual fun shutdown() {
        val active = worker ?: return
        worker = null
        active.requestTermination(processScheduledJobs = false)
    }
}
