@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.posix.memset
import platform.windows.CancelIoEx
import platform.windows.CloseHandle
import platform.windows.ConnectNamedPipe
import platform.windows.CreateEventW
import platform.windows.CreateNamedPipeW
import platform.windows.DisconnectNamedPipe
import platform.windows.ERROR_IO_PENDING
import platform.windows.ERROR_PIPE_CONNECTED
import platform.windows.FILE_FLAG_FIRST_PIPE_INSTANCE
import platform.windows.FILE_FLAG_OVERLAPPED
import platform.windows.GetLastError
import platform.windows.GetOverlappedResult
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.INFINITE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OVERLAPPED
import platform.windows.PIPE_ACCESS_DUPLEX
import platform.windows.PIPE_READMODE_BYTE
import platform.windows.PIPE_REJECT_REMOTE_CLIENTS
import platform.windows.PIPE_TYPE_BYTE
import platform.windows.PIPE_UNLIMITED_INSTANCES
import platform.windows.PIPE_WAIT
import platform.windows.ResetEvent
import platform.windows.SetEvent
import platform.windows.WAIT_OBJECT_0
import platform.windows.WaitForMultipleObjects
import platform.windows.WaitForSingleObject
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt

/**
 * Windows Win32 Named Pipe [PlatformIpcServerDriver] (`mingwX64`) scoped to the current user SID.
 */
internal actual class PlatformIpcServerDriver actual constructor() {

    private var acceptScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clientScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var stopEvent: HANDLE? = null
    private var connectEvent: HANDLE? = null
    private var doneEvent: HANDLE? = null
    private val stopped = AtomicBoolean(true)
    private val eventRefs = AtomicInt(0)

    @Volatile
    private var boundAddress: IpcChannelAddress? = null

    actual val address: IpcChannelAddress?
        get() = boundAddress

    actual val isRunning: Boolean
        get() = !stopped.load() && boundAddress != null

    actual fun start(
        channelName: String,
        runtimeDir: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        if (!stopped.load()) {
            close()
        }
        val resolved = IpcChannelAddress.resolveDesktopEndpoint(
            channelName = channelName,
            runtimeDir = runtimeDir,
            osHint = "windows"
        )
        val pipeName = resolved.endpointAddress
        val sEvent = CreateEventW(null, 1, 0, null) ?: return false
        val cEvent = CreateEventW(null, 1, 0, null)
        val dEvent = CreateEventW(null, 1, 0, null)
        if (cEvent == null || dEvent == null) {
            CloseHandle(sEvent)
            cEvent?.let { CloseHandle(it) }
            dEvent?.let { CloseHandle(it) }
            return false
        }

        val bufSize = config.pipeBufferSizeBytes.coerceAtLeast(4096)
        val firstPipe = createPipeInstance(pipeName, bufSize, isFirst = true)
        if (firstPipe == null || firstPipe == INVALID_HANDLE_VALUE) {
            CloseHandle(sEvent)
            CloseHandle(cEvent)
            CloseHandle(dEvent)
            return false
        }

        if (acceptScope.coroutineContext[Job]?.isActive != true) {
            acceptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        if (clientScope.coroutineContext[Job]?.isActive != true) {
            clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        stopEvent = sEvent
        connectEvent = cEvent
        doneEvent = dEvent
        boundAddress = resolved
        stopped.store(false)
        eventRefs.store(2)

        acceptJob = acceptScope.launch {
            var pendingPipe: HANDLE? = firstPipe
            try {
                memScoped {
                    val ov = alloc<OVERLAPPED>()
                    val transferredVar = alloc<UIntVar>()
                    val handles = allocArray<HANDLEVar>(2)
                    handles[0] = cEvent
                    handles[1] = sEvent

                    while (!stopped.load()) {
                        val hPipe = pendingPipe ?: createPipeInstance(pipeName, bufSize, isFirst = false)
                        pendingPipe = null
                        if (hPipe == null || hPipe == INVALID_HANDLE_VALUE) {
                            if (stopped.load()) break
                            if (WaitForSingleObject(sEvent, 25u) == WAIT_OBJECT_0) break
                            continue
                        }

                        ResetEvent(cEvent)
                        memset(ov.ptr, 0, sizeOf<OVERLAPPED>().convert())
                        ov.hEvent = cEvent

                        val ok = ConnectNamedPipe(hPipe, ov.ptr)
                        if (ok != 0) {
                            if (stopped.load()) {
                                DisconnectNamedPipe(hPipe)
                                CloseHandle(hPipe)
                                break
                            }
                            val conn = WindowsNamedPipeConnection(hPipe, config.maxPacketBytes)
                            clientScope.launch { onClientConnected(conn) }
                            continue
                        }

                        when (GetLastError().toInt()) {
                            ERROR_PIPE_CONNECTED -> {
                                if (stopped.load()) {
                                    DisconnectNamedPipe(hPipe)
                                    CloseHandle(hPipe)
                                    break
                                }
                                val conn = WindowsNamedPipeConnection(hPipe, config.maxPacketBytes)
                                clientScope.launch { onClientConnected(conn) }
                            }
                            ERROR_IO_PENDING -> {
                                val waitRes = WaitForMultipleObjects(2u, handles, 0, INFINITE)
                                if (waitRes == WAIT_OBJECT_0) {
                                    val resOk = GetOverlappedResult(hPipe, ov.ptr, transferredVar.ptr, 1)
                                    if (resOk != 0 && !stopped.load()) {
                                        val conn = WindowsNamedPipeConnection(hPipe, config.maxPacketBytes)
                                        clientScope.launch { onClientConnected(conn) }
                                    } else {
                                        if (resOk != 0) {
                                            DisconnectNamedPipe(hPipe)
                                        }
                                        CloseHandle(hPipe)
                                    }
                                } else {
                                    CancelIoEx(hPipe, ov.ptr)
                                    GetOverlappedResult(hPipe, ov.ptr, transferredVar.ptr, 1)
                                    CloseHandle(hPipe)
                                    break
                                }
                            }
                            else -> {
                                CloseHandle(hPipe)
                                if (stopped.load()) break
                                if (WaitForSingleObject(sEvent, 25u) == WAIT_OBJECT_0) break
                            }
                        }
                    }
                }
            } finally {
                pendingPipe?.let {
                    if (it != INVALID_HANDLE_VALUE) {
                        CloseHandle(it)
                    }
                }
                SetEvent(dEvent)
                releaseEventsRef(sEvent, cEvent, dEvent)
            }
        }
        return true
    }

    actual fun close() {
        if (!stopped.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        boundAddress = null
        val sEvent = stopEvent
        val cEvent = connectEvent
        val dEvent = doneEvent
        stopEvent = null
        connectEvent = null
        doneEvent = null
        if (sEvent != null) {
            SetEvent(sEvent)
        }
        if (dEvent != null) {
            WaitForSingleObject(dEvent, 2_000u)
        }
        acceptJob?.cancel()
        acceptJob = null
        acceptScope.cancel()
        clientScope.cancel()
        if (sEvent != null && cEvent != null && dEvent != null) {
            releaseEventsRef(sEvent, cEvent, dEvent)
        }
    }

    private fun createPipeInstance(pipeName: String, bufferSize: Int, isFirst: Boolean): HANDLE? {
        val baseOpenMode = PIPE_ACCESS_DUPLEX or FILE_FLAG_OVERLAPPED
        val openMode = (if (isFirst) (baseOpenMode or FILE_FLAG_FIRST_PIPE_INSTANCE) else baseOpenMode).toUInt()
        val pipeMode = (PIPE_TYPE_BYTE or PIPE_READMODE_BYTE or PIPE_WAIT or PIPE_REJECT_REMOTE_CLIENTS).toUInt()
        val bufBytes = bufferSize.toUInt()
        return WindowsNamedPipeSecurity.withCurrentUserPipeSecurityAttributes { saPtr ->
            CreateNamedPipeW(
                pipeName,
                openMode,
                pipeMode,
                PIPE_UNLIMITED_INSTANCES.toUInt(),
                bufBytes,
                bufBytes,
                0u,
                saPtr
            )
        }
    }

    private fun releaseEventsRef(sEvent: HANDLE, cEvent: HANDLE, dEvent: HANDLE) {
        if (eventRefs.fetchAndAdd(-1) == 1) {
            CloseHandle(sEvent)
            CloseHandle(cEvent)
            CloseHandle(dEvent)
        }
    }
}
