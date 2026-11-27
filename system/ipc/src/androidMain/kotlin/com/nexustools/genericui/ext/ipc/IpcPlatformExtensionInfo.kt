package com.nexustools.genericui.ext.ipc

/**
 * Android capability metadata for [IpcExtension] (desktop socket/pipe IPC is unsupported on Android;
 * use Android Binder IPC helpers instead).
 */
internal actual object IpcPlatformExtensionInfo {
    actual val isSupported: Boolean = false
    actual val backendName: String = "Android"
}
