@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memset
import platform.windows.CRITICAL_SECTION
import platform.windows.CancelIoEx
import platform.windows.CloseHandle
import platform.windows.CreateEventW
import platform.windows.DeleteCriticalSection
import platform.windows.DisconnectNamedPipe
import platform.windows.ERROR_BROKEN_PIPE
import platform.windows.ERROR_INVALID_HANDLE
import platform.windows.ERROR_IO_PENDING
import platform.windows.ERROR_PIPE_NOT_CONNECTED
import platform.windows.EnterCriticalSection
import platform.windows.GetLastError
import platform.windows.GetOverlappedResult
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.INFINITE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.InitializeCriticalSection
import platform.windows.LeaveCriticalSection
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
import kotlin.time.TimeSource

/**
 * Overlapped Win32 Named Pipe framed IPC connection (`mingwX64`).
 */
internal class WindowsNamedPipeConnection(
    private val pipeHandle: HANDLE,
    private val isServerSide: Boolean
) : DesktopIpcConnection {

    private val readEvent: HANDLE? = CreateEventW(null, 1, 0, null)
    private val writeEvent: HANDLE? = CreateEventW(null, 1, 0, null)
    private val closeEvent: HANDLE? = CreateEventW(null, 1, 0, null)
    private val readLock = nativeHeap.alloc<CRITICAL_SECTION>()
    private val writeLock = nativeHeap.alloc<CRITICAL_SECTION>()
    private val closed = AtomicBoolean(false)
    private val handlesReleased = AtomicBoolean(false)
    private val activeRefs = AtomicInt(1)

    init {
        InitializeCriticalSection(readLock.ptr)
        InitializeCriticalSection(writeLock.ptr)
        if (pipeHandle == INVALID_HANDLE_VALUE || readEvent == null || writeEvent == null || closeEvent == null) {
            closed.store(true)
            releaseHandleRef()
        }
    }

    override val isClosed: Boolean
        get() = closed.load()

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (bodyBytes.isEmpty() || bodyBytes.size > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) {
            return false
        }
        if (!acquireHandleRef()) return false
        try {
            EnterCriticalSection(writeLock.ptr)
            try {
                if (closed.load()) return false
                val framed = ByteArray(4 + bodyBytes.size)
                ServiceWorkerFrameCodec.writeIntBE(framed, 0, bodyBytes.size)
                bodyBytes.copyInto(framed, destinationOffset = 4)
                return writeExact(framed)
            } finally {
                LeaveCriticalSection(writeLock.ptr)
            }
        } finally {
            releaseHandleRef()
        }
    }

    override fun receiveBody(timeoutMs: Int): ByteArray? {
        if (!acquireHandleRef()) return null
        try {
            EnterCriticalSection(readLock.ptr)
            try {
                if (closed.load()) return null
                val header = readExact(4, initialTimeoutMs = timeoutMs) ?: return null
                val len = ServiceWorkerFrameCodec.readIntBE(header, 0)
                if (len <= 0 || len > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) {
                    close()
                    return null
                }
                val frameTimeoutMs = if (timeoutMs > 0) maxOf(timeoutMs, 5_000) else 0
                return readExact(len, initialTimeoutMs = frameTimeoutMs)
            } finally {
                LeaveCriticalSection(readLock.ptr)
            }
        } finally {
            releaseHandleRef()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        if (pipeHandle != INVALID_HANDLE_VALUE) {
            CancelIoEx(pipeHandle, null)
            if (isServerSide) {
                DisconnectNamedPipe(pipeHandle)
            }
        }
        closeEvent?.let { SetEvent(it) }
        releaseHandleRef()
    }

    private fun readExact(count: Int, initialTimeoutMs: Int): ByteArray? {
        val rEvent = readEvent ?: return null
        val cEvent = closeEvent ?: return null
        val deadline = if (initialTimeoutMs > 0) {
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
                    if (closed.load()) return null
                    val waitMs: UInt = if (deadline != null) {
                        val rem = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds
                        if (rem <= 0L) {
                            if (offset > 0) close()
                            return null
                        }
                        rem.coerceAtMost(Int.MAX_VALUE.toLong()).toUInt()
                    } else {
                        INFINITE
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
                            // Always cancel and wait for GetOverlappedResult before leaving memScoped/usePinned
                            CancelIoEx(pipeHandle, ov.ptr)
                            val cancelOk = GetOverlappedResult(pipeHandle, ov.ptr, transferredVar.ptr, 1)
                            if (waitRes == WAIT_TIMEOUT.toUInt() && !closed.load()) {
                                if (cancelOk != 0 && transferredVar.value > 0u) {
                                    transferredVar.value.toInt()
                                } else {
                                    if (offset > 0) close()
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
    }

    private fun writeExact(bytes: ByteArray): Boolean {
        val wEvent = writeEvent ?: return false
        val cEvent = closeEvent ?: return false
        var offset = 0
        bytes.usePinned { pinned ->
            memScoped {
                val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
                val ov = alloc<OVERLAPPED>()
                val transferredVar = alloc<UIntVar>()
                val handles = allocArray<HANDLEVar>(2)
                handles[0] = wEvent
                handles[1] = cEvent

                while (offset < bytes.size) {
                    if (closed.load()) return false
                    ResetEvent(wEvent)
                    memset(ov.ptr, 0, sizeOf<OVERLAPPED>().convert())
                    ov.hEvent = wEvent
                    transferredVar.value = 0u

                    val ok = WriteFile(
                        pipeHandle,
                        basePtr + offset,
                        (bytes.size - offset).toUInt(),
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
                            // Always cancel and wait for GetOverlappedResult before leaving memScoped/usePinned
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
    }

    private fun isTerminalPipeError(err: Int): Boolean =
        err == ERROR_BROKEN_PIPE ||
            err == ERROR_PIPE_NOT_CONNECTED ||
            err == ERROR_INVALID_HANDLE

    private fun acquireHandleRef(): Boolean {
        while (true) {
            val cur = activeRefs.load()
            if (cur <= 0 || closed.load()) return false
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
                DeleteCriticalSection(readLock.ptr)
                DeleteCriticalSection(writeLock.ptr)
                nativeHeap.free(readLock)
                nativeHeap.free(writeLock)
            }
        }
    }
}
