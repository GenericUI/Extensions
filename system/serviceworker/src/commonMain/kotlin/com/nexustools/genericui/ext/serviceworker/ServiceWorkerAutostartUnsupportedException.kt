package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.exception.UnsupportedBackendCapabilityException

/**
 * Thrown when ServiceWorker autostart configuration or registration is invoked where unsupported or disallowed.
 */
open class ServiceWorkerAutostartUnsupportedException(
    backendName: String,
    message: String = "ServiceWorker autostart is not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException("serviceWorkerAutostart", backendName, message, cause)
