package com.nexustools.genericui.ext.ipc

/**
 * JVM endpoint resolution defaults across Windows, macOS, and Linux.
 */
internal actual object IpcEndpointPlatformSupport {
    private fun isWindowsOs(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    actual fun defaultTransportKind(): String =
        if (isWindowsOs() && JvmWindowsNamedPipeBridge.isSupported()) {
            IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE
        } else {
            IpcChannelAddress.TRANSPORT_UNIX_SOCKET
        }

    actual fun currentUserScopeTag(): String =
        System.getenv("USERNAME")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: System.getenv("USER")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: System.getProperty("user.name")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: "user"

    actual fun shortSocketBaseDir(userHash8: String, osHint: String): String {
        val hintLower = osHint.trim().lowercase()
        if (hintLower.contains("mac") || hintLower.contains("darwin")) {
            return "/tmp/gui-$userHash8"
        }
        if (hintLower.contains("linux")) {
            val xdg = System.getenv("XDG_RUNTIME_DIR")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            if (xdg != null && "$xdg/gui-$userHash8".encodeToByteArray().size <= 68) {
                return "$xdg/gui-$userHash8"
            }
            return "/tmp/gui-$userHash8"
        }
        if (isWindowsOs()) {
            val tmp = System.getProperty("java.io.tmpdir")
                ?.trim()
                ?.replace('\\', '/')
                ?.trimEnd('/')
                ?.takeIf { it.isNotEmpty() }
            if (tmp != null && "$tmp/gui-$userHash8".encodeToByteArray().size <= 68) {
                return "$tmp/gui-$userHash8"
            }
        } else {
            val xdg = System.getenv("XDG_RUNTIME_DIR")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            if (xdg != null && "$xdg/gui-$userHash8".encodeToByteArray().size <= 68) {
                return "$xdg/gui-$userHash8"
            }
        }
        return "/tmp/gui-$userHash8"
    }
}
