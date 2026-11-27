@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.ipc

import platform.posix.sched_yield
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Linux [IpcMutex] using an atomic flag with POSIX thread yielding.
 */
internal actual class IpcMutex actual constructor() {
    private val locked = AtomicBoolean(false)

    actual fun lock() {
        while (!locked.compareAndSet(expectedValue = false, newValue = true)) {
            sched_yield()
        }
    }

    actual fun unlock() {
        locked.store(false)
    }
}
