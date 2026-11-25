package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.exception.GenericUiException

/**
 * Thrown when ServiceWorker cross-process IPC transport, framing, delivery, or request/reply fails or times out.
 */
open class ServiceWorkerIpcException(
    message: String,
    cause: Throwable? = null
) : GenericUiException(message, cause)
