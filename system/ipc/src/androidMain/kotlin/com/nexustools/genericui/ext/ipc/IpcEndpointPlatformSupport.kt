package com.nexustools.genericui.ext.ipc

/**
 * Android endpoint resolution defaults (desktop IPC transport is disabled on Android).
 */
internal actual object IpcEndpointPlatformSupport {
    actual fun defaultTransportKind(): String =
        IpcChannelAddress.TRANSPORT_UNIX_SOCKET

    actual fun currentUserScopeTag(): String =
        "android"

    actual fun shortSocketBaseDir(userHash8: String, osHint: String): String =
        "/data/local/tmp/gui-$userHash8"
}
