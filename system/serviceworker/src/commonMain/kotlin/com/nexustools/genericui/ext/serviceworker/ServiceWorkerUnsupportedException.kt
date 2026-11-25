package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.exception.UnsupportedBackendCapabilityException

/**
 * Thrown when ServiceWorker lifecycle or IPC operations are invoked on a backend without ServiceWorker support.
 */
open class ServiceWorkerUnsupportedException(
    backendName: String,
    message: String = "ServiceWorker is not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException("serviceWorker", backendName, message, cause)
