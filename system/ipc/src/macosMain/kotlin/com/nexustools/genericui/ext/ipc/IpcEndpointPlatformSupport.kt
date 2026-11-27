package com.nexustools.genericui.ext.ipc

import platform.posix.geteuid

/**
 * macOS endpoint resolution defaults (`macosArm64`).
 */
internal actual object IpcEndpointPlatformSupport {
    actual fun defaultTransportKind(): String =
        IpcChannelAddress.TRANSPORT_UNIX_SOCKET

    actual fun currentUserScopeTag(): String =
        geteuid().toString()

    actual fun shortSocketBaseDir(userHash8: String, osHint: String): String =
        "/tmp/gui-$userHash8"
}
