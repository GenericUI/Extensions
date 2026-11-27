package com.nexustools.genericui.ext.ipc

/**
 * Platform capability and backend metadata backing [IpcExtension].
 */
internal expect object IpcPlatformExtensionInfo {
    val isSupported: Boolean
    val backendName: String
}
