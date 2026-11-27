package com.nexustools.genericui.ext.ipc

/**
 * Lightweight cross-platform mutual exclusion lock used internally to serialize read and write halves
 * of [BaseFramedIpcConnection].
 */
internal expect class IpcMutex() {
    fun lock()
    fun unlock()
}

internal inline fun <T> IpcMutex.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
