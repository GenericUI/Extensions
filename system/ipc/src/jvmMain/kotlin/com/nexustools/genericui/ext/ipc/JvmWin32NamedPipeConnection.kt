package com.nexustools.genericui.ext.ipc

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Full-duplex overlapped Win32 Named Pipe [IpcConnection] on JVM via [JvmWindowsNamedPipeBridge].
 */
internal class JvmWin32NamedPipeConnection(
    private val pipeHandle: Any,
    maxPacketBytes: Int = IpcPacketFramer.MAX_PACKET_BYTES
) : BaseFramedIpcConnection(maxPacketBytes = maxPacketBytes) {

    private companion object {
        const val IO_CHUNK_BYTES = 65_536
    }

    private val arena = JvmWindowsNamedPipeBridge.openSharedArena()
    private val readEvent = JvmWindowsNamedPipeBridge.createManualResetEvent()
    private val writeEvent = JvmWindowsNamedPipeBridge.createManualResetEvent()
    private val closeEvent = JvmWindowsNamedPipeBridge.createManualResetEvent()
    private val readBufSeg = JvmWindowsNamedPipeBridge.allocate(arena, IO_CHUNK_BYTES.toLong(), 8L)
    private val writeBufSeg = JvmWindowsNamedPipeBridge.allocate(arena, IO_CHUNK_BYTES.toLong(), 8L)
    private val readOvSeg = JvmWindowsNamedPipeBridge.allocateOverlapped(arena)
    private val writeOvSeg = JvmWindowsNamedPipeBridge.allocateOverlapped(arena)
    private val readCaptureSeg = JvmWindowsNamedPipeBridge.allocateCaptureState(arena)
    private val writeCaptureSeg = JvmWindowsNamedPipeBridge.allocateCaptureState(arena)
    private val readTransferredSeg = JvmWindowsNamedPipeBridge.allocateZeroed(arena, 4L, 4L)
    private val writeTransferredSeg = JvmWindowsNamedPipeBridge.allocateZeroed(arena, 4L, 4L)
    private val readHandlesSeg: Any?
    private val writeHandlesSeg: Any?

    private val handlesReleased = AtomicBoolean(false)
    private val activeRefs = AtomicInteger(1)

    init {
        if (!JvmWindowsNamedPipeBridge.isValidHandle(pipeHandle) ||
            readEvent == null ||
            writeEvent == null ||
            closeEvent == null
        ) {
            readHandlesSeg = null
            writeHandlesSeg = null
            close()
        } else {
            readHandlesSeg = JvmWindowsNamedPipeBridge.allocateHandlePair(arena, readEvent, closeEvent)
            writeHandlesSeg = JvmWindowsNamedPipeBridge.allocateHandlePair(arena, writeEvent, closeEvent)
        }
    }

    override fun writeFrameBytes(framedBytes: ByteArray): Boolean {
        val wEvent = writeEvent ?: return false
        val wHandles = writeHandlesSeg ?: return false
        if (!acquireHandleRef()) return false
        try {
            var offset = 0
            while (offset < framedBytes.size) {
                if (isClosed) return false
                val chunkLen = minOf(framedBytes.size - offset, IO_CHUNK_BYTES)
                JvmWindowsNamedPipeBridge.copyToNative(framedBytes, offset, writeBufSeg, chunkLen)
                JvmWindowsNamedPipeBridge.resetEvent(wEvent)
                JvmWindowsNamedPipeBridge.zeroOverlappedWithEvent(writeOvSeg, wEvent)
                JvmWindowsNamedPipeBridge.writeInt(writeTransferredSeg, 0L, 0)

                val ok = JvmWindowsNamedPipeBridge.writeFile(
                    capture = writeCaptureSeg,
                    hFile = pipeHandle,
                    bufferSeg = writeBufSeg,
                    bytesToWrite = chunkLen,
                    bytesWrittenOut = writeTransferredSeg,
                    overlapped = writeOvSeg
                )
                val bytesWritten = if (ok != 0) {
                    JvmWindowsNamedPipeBridge.readInt(writeTransferredSeg)
                } else {
                    val err = JvmWindowsNamedPipeBridge.readCapturedLastError(writeCaptureSeg)
                    if (err != JvmWindowsNamedPipeBridge.ERROR_IO_PENDING) {
                        if (isTerminalPipeError(err) || err != 0) {
                            close()
                        }
                        return false
                    }
                    val waitRes = JvmWindowsNamedPipeBridge.waitForMultipleObjects(
                        capture = writeCaptureSeg,
                        count = 2,
                        handlesSeg = wHandles,
                        waitAll = 0,
                        timeoutMs = JvmWindowsNamedPipeBridge.INFINITE
                    )
                    if (waitRes == JvmWindowsNamedPipeBridge.WAIT_OBJECT_0) {
                        val resOk = JvmWindowsNamedPipeBridge.getOverlappedResult(
                            capture = writeCaptureSeg,
                            hFile = pipeHandle,
                            overlapped = writeOvSeg,
                            bytesTransferredOut = writeTransferredSeg,
                            wait = 1
                        )
                        if (resOk == 0) {
                            close()
                            return false
                        }
                        JvmWindowsNamedPipeBridge.readInt(writeTransferredSeg)
                    } else {
                        JvmWindowsNamedPipeBridge.cancelIoEx(pipeHandle, writeOvSeg)
                        JvmWindowsNamedPipeBridge.getOverlappedResult(
                            capture = writeCaptureSeg,
                            hFile = pipeHandle,
                            overlapped = writeOvSeg,
                            bytesTransferredOut = writeTransferredSeg,
                            wait = 1
                        )
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
        val rHandles = readHandlesSeg ?: return null
        if (!acquireHandleRef()) return null
        try {
            val out = ByteArray(count)
            var offset = 0
            val initialDeadlineNanos = when {
                initialTimeoutMs > 0 -> System.nanoTime() + initialTimeoutMs.toLong() * 1_000_000L
                else -> null
            }
            var completionDeadlineNanos: Long? = if (!isHeader && initialTimeoutMs > 0) {
                System.nanoTime() + initialTimeoutMs.toLong() * 1_000_000L
            } else {
                null
            }

            while (offset < count) {
                if (isClosed) return null

                val waitMs: Int = when {
                    isHeader && offset == 0 -> when {
                        initialTimeoutMs == 0 -> 0
                        initialDeadlineNanos != null -> {
                            val remMs = (initialDeadlineNanos - System.nanoTime() + 999_999L) / 1_000_000L
                            if (remMs <= 0L) return null
                            remMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        }
                        else -> JvmWindowsNamedPipeBridge.INFINITE
                    }
                    else -> {
                        val activeDeadline = if (isHeader) {
                            if (completionDeadlineNanos == null) {
                                val compMs = computeFrameCompletionTimeoutMs(initialTimeoutMs)
                                completionDeadlineNanos = if (compMs > 0) {
                                    System.nanoTime() + compMs.toLong() * 1_000_000L
                                } else {
                                    null
                                }
                            }
                            completionDeadlineNanos
                        } else {
                            completionDeadlineNanos
                        }
                        if (activeDeadline != null) {
                            val remMs = (activeDeadline - System.nanoTime() + 999_999L) / 1_000_000L
                            if (remMs <= 0L) {
                                close()
                                return null
                            }
                            remMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        } else {
                            JvmWindowsNamedPipeBridge.INFINITE
                        }
                    }
                }

                val chunkLimit = minOf(count - offset, IO_CHUNK_BYTES)
                JvmWindowsNamedPipeBridge.resetEvent(rEvent)
                JvmWindowsNamedPipeBridge.zeroOverlappedWithEvent(readOvSeg, rEvent)
                JvmWindowsNamedPipeBridge.writeInt(readTransferredSeg, 0L, 0)

                val ok = JvmWindowsNamedPipeBridge.readFile(
                    capture = readCaptureSeg,
                    hFile = pipeHandle,
                    bufferSeg = readBufSeg,
                    bytesToRead = chunkLimit,
                    bytesReadOut = readTransferredSeg,
                    overlapped = readOvSeg
                )
                val bytesRead = if (ok != 0) {
                    JvmWindowsNamedPipeBridge.readInt(readTransferredSeg)
                } else {
                    val err = JvmWindowsNamedPipeBridge.readCapturedLastError(readCaptureSeg)
                    if (err != JvmWindowsNamedPipeBridge.ERROR_IO_PENDING) {
                        if (isTerminalPipeError(err) || err != 0) {
                            close()
                        }
                        return null
                    }
                    val waitRes = JvmWindowsNamedPipeBridge.waitForMultipleObjects(
                        capture = readCaptureSeg,
                        count = 2,
                        handlesSeg = rHandles,
                        waitAll = 0,
                        timeoutMs = waitMs
                    )
                    if (waitRes == JvmWindowsNamedPipeBridge.WAIT_OBJECT_0) {
                        val resOk = JvmWindowsNamedPipeBridge.getOverlappedResult(
                            capture = readCaptureSeg,
                            hFile = pipeHandle,
                            overlapped = readOvSeg,
                            bytesTransferredOut = readTransferredSeg,
                            wait = 1
                        )
                        if (resOk == 0) {
                            close()
                            return null
                        }
                        JvmWindowsNamedPipeBridge.readInt(readTransferredSeg)
                    } else {
                        JvmWindowsNamedPipeBridge.cancelIoEx(pipeHandle, readOvSeg)
                        val cancelOk = JvmWindowsNamedPipeBridge.getOverlappedResult(
                            capture = readCaptureSeg,
                            hFile = pipeHandle,
                            overlapped = readOvSeg,
                            bytesTransferredOut = readTransferredSeg,
                            wait = 1
                        )
                        val transferred = JvmWindowsNamedPipeBridge.readInt(readTransferredSeg)
                        if (waitRes == JvmWindowsNamedPipeBridge.WAIT_TIMEOUT && !isClosed) {
                            if (cancelOk != 0 && transferred > 0) {
                                transferred
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
                JvmWindowsNamedPipeBridge.copyFromNative(readBufSeg, out, offset, bytesRead)
                offset += bytesRead
            }
            return out
        } finally {
            releaseHandleRef()
        }
    }

    override fun closeTransport() {
        if (JvmWindowsNamedPipeBridge.isValidHandle(pipeHandle)) {
            JvmWindowsNamedPipeBridge.cancelIoEx(pipeHandle, null)
        }
        closeEvent?.let { JvmWindowsNamedPipeBridge.setEvent(it) }
        releaseHandleRef()
    }

    private fun isTerminalPipeError(err: Int): Boolean =
        err == JvmWindowsNamedPipeBridge.ERROR_BROKEN_PIPE ||
            err == JvmWindowsNamedPipeBridge.ERROR_PIPE_NOT_CONNECTED ||
            err == JvmWindowsNamedPipeBridge.ERROR_INVALID_HANDLE

    private fun acquireHandleRef(): Boolean {
        while (true) {
            val cur = activeRefs.get()
            if (cur <= 0 || isClosed) return false
            if (activeRefs.compareAndSet(cur, cur + 1)) return true
        }
    }

    private fun releaseHandleRef() {
        if (activeRefs.decrementAndGet() == 0) {
            if (handlesReleased.compareAndSet(false, true)) {
                if (JvmWindowsNamedPipeBridge.isValidHandle(pipeHandle)) {
                    JvmWindowsNamedPipeBridge.closeHandle(pipeHandle)
                }
                readEvent?.let { JvmWindowsNamedPipeBridge.closeHandle(it) }
                writeEvent?.let { JvmWindowsNamedPipeBridge.closeHandle(it) }
                closeEvent?.let { JvmWindowsNamedPipeBridge.closeHandle(it) }
                JvmWindowsNamedPipeBridge.closeArena(arena)
            }
        }
    }
}
