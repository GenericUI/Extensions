package com.nexustools.genericui.ext.ipc

/**
 * Linux capability and backend descriptor for [IpcExtension].
 */
internal actual object IpcPlatformExtensionInfo {
    actual val isSupported: Boolean = true
    actual val backendName: String = "Linux AF_UNIX"
}
