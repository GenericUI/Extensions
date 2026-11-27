package com.nexustools.genericui.ext.ipc

/**
 * Windows native capability and backend descriptor for [IpcExtension].
 */
internal actual object IpcPlatformExtensionInfo {
    actual val isSupported: Boolean = true
    actual val backendName: String = "Windows Named Pipe"
}
