package com.nexustools.genericui.ext.ipc

/**
 * Local IPC server listening on a platform-native endpoint (Win32 Named Pipe on Windows or
 * user-private `AF_UNIX` domain socket on POSIX/JVM) and dispatching accepted [IpcConnection]s.
 */
class IpcServer() {
    private val driver = PlatformIpcServerDriver()

    val address: IpcChannelAddress?
        get() = driver.address

    val isRunning: Boolean
        get() = driver.isRunning

    fun start(
        channelName: String,
        runtimeDir: String = "",
        config: IpcConfig = IpcConfig(),
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        if (channelName.isBlank()) {
            return false
        }
        return driver.start(channelName, runtimeDir, config, onClientConnected)
    }

    fun close() {
        driver.close()
    }
}
