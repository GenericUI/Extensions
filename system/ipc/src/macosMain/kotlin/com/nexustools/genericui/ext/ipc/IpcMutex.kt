package com.nexustools.genericui.ext.ipc

import platform.Foundation.NSLock

/**
 * macOS [IpcMutex] backed by Foundation [NSLock].
 */
internal actual class IpcMutex actual constructor() {
    private val delegate = NSLock()

    actual fun lock() {
        delegate.lock()
    }

    actual fun unlock() {
        delegate.unlock()
    }
}
