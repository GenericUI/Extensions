package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.core.Disposable

/**
 * Simple idempotent [Disposable] invoking [onDisposeAction] once when disposed.
 */
class ServiceWorkerDisposableHandle internal constructor(
    private val onDisposeAction: () -> Unit
) : Disposable {
    override var isDisposed: Boolean = false
        private set

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        onDisposeAction()
    }
}
