package com.nexustools.genericui.ext.ipc

/**
 * macOS capability and backend descriptor for [IpcExtension].
 */
internal actual object IpcPlatformExtensionInfo {
    actual val isSupported: Boolean = true
    actual val backendName: String = "macOS XPC / AF_UNIX"
}
