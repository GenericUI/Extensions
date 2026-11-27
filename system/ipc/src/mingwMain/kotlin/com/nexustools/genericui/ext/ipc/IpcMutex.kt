@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.ipc

import platform.windows.SwitchToThread
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Windows native [IpcMutex] using an atomic flag with OS thread yielding.
 */
internal actual class IpcMutex actual constructor() {
    private val locked = AtomicBoolean(false)

    actual fun lock() {
        while (!locked.compareAndSet(expectedValue = false, newValue = true)) {
            SwitchToThread()
        }
    }

    actual fun unlock() {
        locked.store(false)
    }
}
