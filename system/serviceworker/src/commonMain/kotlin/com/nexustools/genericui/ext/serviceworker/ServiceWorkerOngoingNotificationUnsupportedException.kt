package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.exception.UnsupportedBackendCapabilityException

/**
 * Thrown when ongoing ServiceWorker foreground-service notification APIs are invoked on a backend without support.
 */
open class ServiceWorkerOngoingNotificationUnsupportedException(
    backendName: String,
    message: String = "ServiceWorker ongoing foreground notification is not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException("serviceWorkerOngoingNotification", backendName, message, cause)
