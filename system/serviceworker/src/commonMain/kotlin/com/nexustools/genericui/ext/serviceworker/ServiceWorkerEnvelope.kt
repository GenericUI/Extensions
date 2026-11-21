package com.nexustools.genericui.ext.serviceworker

/**
 * Internal framed IPC message envelope transported across loopback TCP, Android Service Binder,
 * Web Worker `postMessage`, and test harnesses.
 */
internal data class ServiceWorkerEnvelope(
    val kind: Byte,
    val correlationId: Long = 0L,
    val clientId: String = "",
    val typeId: String = "",
    val payloadJson: String = "null",
    val error: String = ""
) {
    companion object {
        const val KIND_AUTH: Byte = 1
        const val KIND_AUTH_OK: Byte = 2
        const val KIND_AUTH_ERR: Byte = 3
        const val KIND_MESSAGE: Byte = 4
        const val KIND_REQUEST: Byte = 5
        const val KIND_RESPONSE: Byte = 6
        const val KIND_RESPONSE_ERR: Byte = 7
        const val KIND_PING: Byte = 8
        const val KIND_BYE: Byte = 9
    }
}
