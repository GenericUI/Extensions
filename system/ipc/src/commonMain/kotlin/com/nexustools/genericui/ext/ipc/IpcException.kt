package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.exception.GenericUiException

/**
 * Thrown when IPC framing, transport setup, serialization, or delivery fails.
 */
open class IpcException(
    message: String,
    cause: Throwable? = null
) : GenericUiException(message, cause)
