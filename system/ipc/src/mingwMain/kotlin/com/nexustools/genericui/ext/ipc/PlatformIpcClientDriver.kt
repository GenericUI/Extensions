@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import platform.windows.CreateFileW
import platform.windows.ERROR_PIPE_BUSY
import platform.windows.FILE_FLAG_OVERLAPPED
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetLastError
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.SECURITY_IDENTIFICATION
import platform.windows.SECURITY_SQOS_PRESENT
import platform.windows.WaitNamedPipeW
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Windows Win32 Named Pipe [PlatformIpcClientDriver] (`mingwX64`).
 */
internal actual object PlatformIpcClientDriver {
    actual fun connect(
        address: IpcChannelAddress,
        timeoutMs: Int,
        config: IpcConfig
    ): IpcConnection? {
        if (address.transportKind != IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE || address.endpointAddress.isBlank()) {
            return null
        }
        val effectiveTimeoutMs = timeoutMs.coerceAtLeast(1)
        val deadline = TimeSource.Monotonic.markNow() + effectiveTimeoutMs.milliseconds
        val access = GENERIC_READ or GENERIC_WRITE.toUInt()
        val flags = (FILE_FLAG_OVERLAPPED or SECURITY_SQOS_PRESENT or SECURITY_IDENTIFICATION).toUInt()

        while (true) {
            val handle = CreateFileW(
                address.endpointAddress,
                access,
                0u,
                null,
                OPEN_EXISTING.toUInt(),
                flags,
                null
            )
            if (handle != null && handle != INVALID_HANDLE_VALUE) {
                return WindowsNamedPipeConnection(handle, config.maxPacketBytes)
            }
            val err = GetLastError().toInt()
            if (err != ERROR_PIPE_BUSY) {
                return null
            }
            val remainingMs = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds
            if (remainingMs <= 0L) {
                return null
            }
            WaitNamedPipeW(
                address.endpointAddress,
                remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toUInt()
            )
        }
    }
}
