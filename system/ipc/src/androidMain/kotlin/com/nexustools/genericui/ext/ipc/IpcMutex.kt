package com.nexustools.genericui.ext.ipc

import java.util.concurrent.locks.ReentrantLock

/**
 * Android [IpcMutex] backed by [ReentrantLock].
 */
internal actual class IpcMutex actual constructor() {
    private val delegate = ReentrantLock()

    actual fun lock() {
        delegate.lock()
    }

    actual fun unlock() {
        if (delegate.isHeldByCurrentThread) {
            delegate.unlock()
        }
    }
}
