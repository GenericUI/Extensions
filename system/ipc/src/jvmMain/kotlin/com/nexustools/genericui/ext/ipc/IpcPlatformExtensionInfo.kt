package com.nexustools.genericui.ext.ipc

/**
 * JVM capability and backend descriptor for [IpcExtension].
 */
internal actual object IpcPlatformExtensionInfo {
    actual val isSupported: Boolean = true

    actual val backendName: String
        get() = if (IpcEndpointPlatformSupport.defaultTransportKind() == IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE) {
            "JVM Windows Named Pipe"
        } else {
            "JVM AF_UNIX"
        }
}
