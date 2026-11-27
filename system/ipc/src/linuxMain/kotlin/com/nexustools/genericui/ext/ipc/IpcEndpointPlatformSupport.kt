@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.geteuid

/**
 * Linux endpoint resolution defaults (`linuxX64` / `linuxArm64`).
 */
internal actual object IpcEndpointPlatformSupport {
    actual fun defaultTransportKind(): String =
        IpcChannelAddress.TRANSPORT_UNIX_SOCKET

    actual fun currentUserScopeTag(): String =
        geteuid().toString()

    actual fun shortSocketBaseDir(userHash8: String, osHint: String): String {
        val hint = osHint.trim().lowercase()
        if (hint.contains("mac") || hint.contains("darwin")) {
            return "/tmp/gui-$userHash8"
        }
        val xdg = getenv("XDG_RUNTIME_DIR")?.toKString()?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        if (xdg != null && "$xdg/gui-$userHash8".encodeToByteArray().size <= 68) {
            return "$xdg/gui-$userHash8"
        }
        return "/tmp/gui-$userHash8"
    }
}
