@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.ipc

import platform.darwin.xpc_connection_cancel
import platform.darwin.xpc_connection_t
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Handle owning an active macOS XPC listener (`xpc_connection_t`) that can be cancelled cleanly.
 */
internal class XpcListenerHandle(
    private val listener: xpc_connection_t
) {
    private val closedFlag = AtomicBoolean(listener == null)

    fun close() {
        if (!closedFlag.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        listener?.let { xpc_connection_cancel(it) }
    }
}
