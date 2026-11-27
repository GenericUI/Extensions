@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Windows native endpoint resolution defaults (`mingwX64`).
 */
internal actual object IpcEndpointPlatformSupport {
    actual fun defaultTransportKind(): String =
        IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE

    actual fun currentUserScopeTag(): String =
        getenv("USERNAME")?.toKString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "user"

    actual fun shortSocketBaseDir(userHash8: String, osHint: String): String {
        val hint = osHint.trim().lowercase()
        if (hint.contains("linux") || hint.contains("mac") || hint.contains("darwin")) {
            return "/tmp/gui-$userHash8"
        }
        val tmp = getenv("TEMP")?.toKString()?.trim()?.replace('\\', '/')?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        if (tmp != null && "$tmp/gui-$userHash8".encodeToByteArray().size <= 68) {
            return "$tmp/gui-$userHash8"
        }
        return "/tmp/gui-$userHash8"
    }
}
