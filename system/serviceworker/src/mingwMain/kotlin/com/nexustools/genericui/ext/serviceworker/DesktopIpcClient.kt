@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.serviceworker

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
 * Windows Win32 Named Pipe IPC client (`mingwX64`).
 */
internal actual object DesktopIpcClient {

    actual fun connect(transportKind: String, address: String, timeoutMs: Int): DesktopIpcConnection? {
        if (transportKind != DesktopEndpointDescriptor.TRANSPORT_WINDOWS_NAMED_PIPE || address.isBlank()) {
            return null
        }
        val effectiveTimeoutMs = timeoutMs.coerceAtLeast(1)
        val deadline = TimeSource.Monotonic.markNow() + effectiveTimeoutMs.milliseconds
        val access = GENERIC_READ or GENERIC_WRITE.toUInt()
        val flags = (FILE_FLAG_OVERLAPPED or SECURITY_SQOS_PRESENT or SECURITY_IDENTIFICATION).toUInt()

        while (true) {
            val handle = CreateFileW(
                address,
                access,
                0u,
                null,
                OPEN_EXISTING.toUInt(),
                flags,
                null
            )
            if (handle != null && handle != INVALID_HANDLE_VALUE) {
                return WindowsNamedPipeConnection(handle, isServerSide = false)
            }
            val err = GetLastError().toInt()
            if (err != ERROR_PIPE_BUSY) {
                return null
            }
            val remainingMs = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds
            if (remainingMs <= 0L) {
                return null
            }
            WaitNamedPipeW(address, remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toUInt())
        }
    }
}
