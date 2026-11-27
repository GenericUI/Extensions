package com.nexustools.genericui.ext.ipc

/**
 * Resolved local desktop IPC transport kind and endpoint address (Win32 Named Pipe or `AF_UNIX` socket).
 */
data class IpcChannelAddress(
    val transportKind: String,
    val endpointAddress: String
) {
    companion object {
        const val TRANSPORT_WINDOWS_NAMED_PIPE: String = "named-pipe"
        const val TRANSPORT_UNIX_SOCKET: String = "unix-socket"
        const val TRANSPORT_MACOS_XPC_MACH: String = "macos-xpc-mach"
        private const val MAX_UNIX_SOCKET_PATH_BYTES: Int = 96

        fun resolveDesktopEndpoint(
            channelName: String,
            runtimeDir: String = "",
            osHint: String = ""
        ): IpcChannelAddress {
            val cleanChannel = channelName.trim().ifEmpty { "default" }
            val normalizedChannel = cleanChannel.lowercase()
            val normalizedDir = runtimeDir.trim().replace('\\', '/').trimEnd('/').lowercase()
            val userTag = IpcEndpointPlatformSupport.currentUserScopeTag().trim().ifEmpty { "user" }
            val userHash8 = IpcHash.fnv1a64Hex(userTag).take(8)
            val scopeHash16 = IpcHash.fnv1a64Hex("$normalizedChannel|$normalizedDir")

            val transportKind = resolveTransportKind(osHint)
            if (transportKind == TRANSPORT_WINDOWS_NAMED_PIPE) {
                return IpcChannelAddress(
                    transportKind = TRANSPORT_WINDOWS_NAMED_PIPE,
                    endpointAddress = "\\\\.\\pipe\\genericui.$userHash8.$scopeHash16.ipc"
                )
            }

            val stem = IpcHash.sanitizeChannelStem(cleanChannel)
            val cleanRuntimeUnix = runtimeDir.trim().replace('\\', '/').trimEnd('/')
            val suffix = "${stem}-${scopeHash16.take(8)}.sock"
            if (cleanRuntimeUnix.isNotEmpty()) {
                val candidate = "$cleanRuntimeUnix/$suffix"
                if (candidate.encodeToByteArray().size <= MAX_UNIX_SOCKET_PATH_BYTES) {
                    return IpcChannelAddress(
                        transportKind = TRANSPORT_UNIX_SOCKET,
                        endpointAddress = candidate
                    )
                }
            }

            val shortBase = IpcEndpointPlatformSupport.shortSocketBaseDir(userHash8, osHint)
                .trim()
                .replace('\\', '/')
                .trimEnd('/')
                .ifEmpty { "/tmp/gui-$userHash8" }
            val fallbackCandidate = "$shortBase/g-$scopeHash16.sock"
            val finalPath = if (fallbackCandidate.encodeToByteArray().size <= MAX_UNIX_SOCKET_PATH_BYTES) {
                fallbackCandidate
            } else {
                "/tmp/gui-$userHash8/g-$scopeHash16.sock"
            }
            return IpcChannelAddress(
                transportKind = TRANSPORT_UNIX_SOCKET,
                endpointAddress = finalPath
            )
        }

        private fun resolveTransportKind(osHint: String): String {
            val hint = osHint.trim().lowercase()
            if (hint.isNotEmpty()) {
                return when {
                    hint.contains("win") || hint.contains("mingw") -> TRANSPORT_WINDOWS_NAMED_PIPE
                    hint.contains("mac") ||
                        hint.contains("darwin") ||
                        hint.contains("linux") ||
                        hint.contains("unix") ||
                        hint.contains("posix") -> TRANSPORT_UNIX_SOCKET
                    else -> IpcEndpointPlatformSupport.defaultTransportKind()
                }
            }
            return IpcEndpointPlatformSupport.defaultTransportKind()
        }
    }
}
