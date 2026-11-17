package com.nexustools.genericui.ext.engine.threading

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * JVM and Android (`jvmAndAndroidMain`) implementation of [PlatformGameLogicThread] using a
 * dedicated daemon [ScheduledExecutorService] named `"GenericUI-GameLogic-Thread"`.
 */
actual object PlatformGameLogicThread {
    actual val threadName: String = "GenericUI-GameLogic-Thread"

    private val workerThreadRef = AtomicReference<Thread?>(null)
    private val executorLock = Any()

    @Volatile
    private var executor: ScheduledExecutorService? = null

    private fun ensureExecutor(): ScheduledExecutorService {
        val current = executor
        if (current != null && !current.isShutdown) {
            return current
        }
        synchronized(executorLock) {
            val existing = executor
            if (existing != null && !existing.isShutdown) {
                return existing
            }
            val created = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, threadName).apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY
                    workerThreadRef.set(this)
                }
            }
            executor = created
            return created
        }
    }

    actual fun isOnDedicatedThread(): Boolean {
        val worker = workerThreadRef.get() ?: return false
        return Thread.currentThread() === worker
    }

    actual fun dispatch(task: () -> Unit) {
        ensureExecutor().execute {
            workerThreadRef.compareAndSet(null, Thread.currentThread())
            task()
        }
    }

    actual fun dispatchDelayed(delayMs: Long, task: () -> Unit) {
        val safeDelay = delayMs.coerceAtLeast(0L)
        ensureExecutor().schedule(
            {
                workerThreadRef.compareAndSet(null, Thread.currentThread())
                task()
            },
            safeDelay,
            TimeUnit.MILLISECONDS
        )
    }

    actual fun shutdown() {
        synchronized(executorLock) {
            executor?.shutdownNow()
            executor = null
            workerThreadRef.set(null)
        }
    }
}
