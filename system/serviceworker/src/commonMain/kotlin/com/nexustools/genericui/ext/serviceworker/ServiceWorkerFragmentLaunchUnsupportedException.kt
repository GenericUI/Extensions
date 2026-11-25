package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.exception.FragmentLaunchException

/**
 * Thrown when a ServiceWorker attempts to start a fragment on a backend or runtime context that forbids fragment launches.
 */
open class ServiceWorkerFragmentLaunchUnsupportedException(
    fragmentId: String,
    val backendName: String,
    message: String = "Launching fragment '$fragmentId' from ServiceWorker is not supported on backend '$backendName'.",
    cause: Throwable? = null
) : FragmentLaunchException(fragmentId, message, cause)
