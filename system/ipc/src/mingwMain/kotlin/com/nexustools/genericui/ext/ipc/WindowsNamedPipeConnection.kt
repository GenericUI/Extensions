@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memset
import platform.windows.CancelIoEx
import platform.windows.CloseHandle
import platform.windows.CreateEventW
import platform.windows.ERROR_BROKEN_PIPE
import platform.windows.ERROR_INVALID_HANDLE
import platform.windows.ERROR_IO_PENDING
import platform.windows.ERROR_PIPE_NOT_CONNECTED
import platform.windows.GetLastError
import platform.windows.GetOverlappedResult
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.INFINITE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OVERLAPPED
import platform.windows.ReadFile
import platform.windows.ResetEvent
import platform.windows.SetEvent
import platform.windows.WAIT_OBJECT_0
import platform.windows.WAIT_TIMEOUT
import platform.windows.WaitForMultipleObjects
import platform.windows.WriteFile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Overlapped Win32 Named Pipe [IpcConnection] (`mingwX64`).
 */
internal class WindowsNamedPipeConnection(
    private val pipeHandle: HANDLE,
    maxPacketBytes: Int = IpcPacketFramer.MAX_PACKET_BYTES
) : BaseFramedIpcConnection(maxPacketBytes = maxPacketBytes) {

    private val readEvent: HANDLE? = CreateEventW(null, 1, 0, null)
    private val writeEvent: HANDLE? = CreateEventW(null, 1, 0, null)
    private val closeEvent: HANDLE? = CreateEventW(null, 1, 0, null)
    private val handlesReleased = AtomicBoolean(false)
    private val activeRefs = AtomicInt(1)

    init {
        if (pipeHandle == INVALID_HANDLE_VALUE || readEvent == null || writeEvent == null || closeEvent == null) {
            close()
        }
    }

    override fun writeFrameBytes(framedBytes: ByteArray): Boolean {
        val wEvent = writeEvent ?: return false
        val cEvent = closeEvent ?: return false
        if (!acquireHandleRef()) return false
        try {
            var offset = 0
            framedBytes.usePinned { pinned ->
                memScoped {
                    val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
                    val ov = alloc<OVERLAPPED>()
                    val transferredVar = alloc<UIntVar>()
                    val handles = allocArray<HANDLEVar>(2)
                    handles[0] = wEvent
                    handles[1] = cEvent

                    while (offset < framedBytes.size) {
                        if (isClosed) return false
                        ResetEvent(wEvent)
                        memset(ov.ptr, 0, sizeOf<OVERLAPPED>().convert())
                        ov.hEvent = wEvent
                        transferredVar.value = 0u

                        val ok = WriteFile(
                            pipeHandle,
                            basePtr + offset,
                            (framedBytes.size - offset).toUInt(),
                            transferredVar.ptr,
                            ov.ptr
                        )
                        val bytesWritten = if (ok != 0) {
                            transferredVar.value.toInt()
                        } else {
                            val err = GetLastError().toInt()
                            if (err != ERROR_IO_PENDING) {
                                if (isTerminalPipeError(err) || err != 0) {
                                    close()
                                }
                                return false
                            }
                            val waitRes = WaitForMultipleObjects(2u, handles, 0, INFINITE)
                            if (waitRes == WAIT_OBJECT_0) {
                                val resOk = GetOverlappedResult(pipeHandle, ov.ptr, transferredVar.ptr, 1)
                                if (resOk == 0) {
                                    close()
                                    return false
                                }
                                transferredVar.value.toInt()
                            } else {
                                CancelIoEx(pipeHandle, ov.ptr)
                                GetOverlappedResult(pipeHandle, ov.ptr, transferredVar.ptr, 1)
                                close()
                                return false
                            }
                        }

                        if (bytesWritten <= 0) {
                            close()
                            return false
                        }
                        offset += bytesWritten
                    }
                }
            }
            return true
        } finally {
            releaseHandleRef()
        }
    }

    override fun readFrameBytes(
        count: Int,
        initialTimeoutMs: Int,
        isHeader: Boolean
    ): ByteArray? {
        val rEvent = readEvent ?: return null
        val cEvent = closeEvent ?: return null
        if (!acquireHandleRef()) return null
        try {
            val initialDeadline: TimeMark? = if (initialTimeoutMs > 0) {
                TimeSource.Monotonic.markNow() + initialTimeoutMs.milliseconds
            } else {
                null
            }
            var completionDeadline: TimeMark? = if (!isHeader && initialTimeoutMs > 0) {
                TimeSource.Monotonic.markNow() + initialTimeoutMs.milliseconds
            } else {
                null
            }

            val out = ByteArray(count)
            var offset = 0
            out.usePinned { pinned ->
                memScoped {
                    val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
                    val ov = alloc<OVERLAPPED>()
                    val transferredVar = alloc<UIntVar>()
                    val handles = allocArray<HANDLEVar>(2)
                    handles[0] = rEvent
                    handles[1] = cEvent

                    while (offset < count) {
                        if (isClosed) return null
                        val waitMs: UInt = when {
                            isHeader && offset == 0 -> when {
                                initialTimeoutMs == 0 -> 0u
                                initialDeadline != null -> {
                                    val rem = -initialDeadline.elapsedNow().inWholeMilliseconds
                                    if (rem <= 0L) return null
                                    rem.coerceAtMost(Int.MAX_VALUE.toLong()).toUInt()
                                }
                                else -> INFINITE
                            }
                            else -> {
                                val activeDeadline = if (isHeader) {
                                    if (completionDeadline == null) {
                                        val compMs = computeFrameCompletionTimeoutMs(initialTimeoutMs)
                                        completionDeadline = if (compMs > 0) {
                                            TimeSource.Monotonic.markNow() + compMs.milliseconds
                                        } else {
                                            null
                                        }
                                    }
                                    completionDeadline
                                } else {
                                    completionDeadline
                                }
                                if (activeDeadline != null) {
                                    val rem = -activeDeadline.elapsedNow().inWholeMilliseconds
                                    if (rem <= 0L) {
                                        close()
                                        return null
                                    }
                                    rem.coerceAtMost(Int.MAX_VALUE.toLong()).toUInt()
                                } else {
                                    INFINITE
                                }
                            }
                        }

                        ResetEvent(rEvent)
                        memset(ov.ptr, 0, sizeOf<OVERLAPPED>().convert())
                        ov.hEvent = rEvent
                        transferredVar.value = 0u

                        val ok = ReadFile(
                            pipeHandle,
                            basePtr + offset,
                            (count - offset).toUInt(),
                            transferredVar.ptr,
                            ov.ptr
                        )
                        val bytesRead = if (ok != 0) {
                            transferredVar.value.toInt()
                        } else {
                            val err = GetLastError().toInt()
                            if (err != ERROR_IO_PENDING) {
                                if (isTerminalPipeError(err) || err != 0) {
                                    close()
                                }
                                return null
                            }
                            val waitRes = WaitForMultipleObjects(2u, handles, 0, waitMs)
                            if (waitRes == WAIT_OBJECT_0) {
                                val resOk = GetOverlappedResult(pipeHandle, ov.ptr, transferredVar.ptr, 1)
                                if (resOk == 0) {
                                    close()
                                    return null
                                }
                                transferredVar.value.toInt()
                            } else {
                                CancelIoEx(pipeHandle, ov.ptr)
                                val cancelOk = GetOverlappedResult(pipeHandle, ov.ptr, transferredVar.ptr, 1)
                                if (waitRes == WAIT_TIMEOUT.toUInt() && !isClosed) {
                                    if (cancelOk != 0 && transferredVar.value > 0u) {
                                        transferredVar.value.toInt()
                                    } else {
                                        if (offset > 0 || !isHeader) close()
                                        return null
                                    }
                                } else {
                                    close()
                                    return null
                                }
                            }
                        }

                        if (bytesRead <= 0) {
                            close()
                            return null
                        }
                        offset += bytesRead
                    }
                }
            }
            return out
        } finally {
            releaseHandleRef()
        }
    }

    override fun closeTransport() {
        if (pipeHandle != INVALID_HANDLE_VALUE) {
            CancelIoEx(pipeHandle, null)
        }
        closeEvent?.let { SetEvent(it) }
        releaseHandleRef()
    }

    private fun isTerminalPipeError(err: Int): Boolean =
        err == ERROR_BROKEN_PIPE ||
            err == ERROR_PIPE_NOT_CONNECTED ||
            err == ERROR_INVALID_HANDLE

    private fun acquireHandleRef(): Boolean {
        while (true) {
            val cur = activeRefs.load()
            if (cur <= 0 || isClosed) return false
            if (activeRefs.compareAndSet(cur, cur + 1)) return true
        }
    }

    private fun releaseHandleRef() {
        if (activeRefs.fetchAndAdd(-1) == 1) {
            if (handlesReleased.compareAndSet(expectedValue = false, newValue = true)) {
                if (pipeHandle != INVALID_HANDLE_VALUE) {
                    CloseHandle(pipeHandle)
                }
                readEvent?.let { CloseHandle(it) }
                writeEvent?.let { CloseHandle(it) }
                closeEvent?.let { CloseHandle(it) }
            }
        }
    }
}
