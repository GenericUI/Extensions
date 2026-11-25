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
    val error: String = "",
    val targetFragmentId: String? = null,
    val sourceFragmentId: String? = null
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

        const val CONTROL_OPEN_MAIN_WINDOW: String = "__genericui.sw.control.openMainWindow"
        const val CONTROL_CLOSE_MAIN_WINDOW: String = "__genericui.sw.control.closeMainWindow"
        const val CONTROL_EXIT_APP: String = "__genericui.sw.control.exitApp"
        const val CONTROL_REQUEST_ACTIVATE_MAIN_WINDOW: String = "__genericui.sw.control.requestActivateMainWindow"
        const val CONTROL_REQUEST_LAUNCH_FRAGMENT: String = "__genericui.sw.control.requestLaunchFragment"
        const val CONTROL_LAUNCH_FRAGMENT: String = "__genericui.sw.control.launchFragment"
        const val CONTROL_CLOSE_FRAGMENT: String = "__genericui.sw.control.closeFragment"
        const val CONTROL_FRAGMENT_MOUNTED: String = "__genericui.sw.control.fragmentMounted"
        const val CONTROL_FRAGMENT_UNMOUNTED: String = "__genericui.sw.control.fragmentUnmounted"
        const val CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW: String = "__genericui.sw.control.queryShouldOpenMainWindow"
    }
}
