package com.nexustools.genericui.ext.ipc

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread

/**
 * JVM Win32 Named Pipe server accepting concurrent overlapped client connections scoped to the
 * current Windows user SID.
 */
internal class JvmWin32NamedPipeServer {

    private var acceptThread: Thread? = null
    private var acceptDoneLatch: CountDownLatch? = null
    private var clientExecutor: ExecutorService? = null
    private var stopEvent: Any? = null
    private var connectEvent: Any? = null
    private var serverArena: Any? = null
    private val stopped = AtomicBoolean(true)

    @Volatile
    private var boundAddress: IpcChannelAddress? = null

    val address: IpcChannelAddress?
        get() = boundAddress

    val isRunning: Boolean
        get() = !stopped.get() && boundAddress != null

    fun start(
        pipeName: String,
        config: IpcConfig,
        onClientConnected: (IpcConnection) -> Unit
    ): Boolean {
        if (!JvmWindowsNamedPipeBridge.isSupported() || pipeName.isBlank()) {
            return false
        }
        if (!stopped.get()) {
            close()
        }

        val arena = JvmWindowsNamedPipeBridge.openSharedArena()
        val sEvent = JvmWindowsNamedPipeBridge.createManualResetEvent()
        val cEvent = JvmWindowsNamedPipeBridge.createManualResetEvent()
        if (sEvent == null || cEvent == null) {
            sEvent?.let { JvmWindowsNamedPipeBridge.closeHandle(it) }
            cEvent?.let { JvmWindowsNamedPipeBridge.closeHandle(it) }
            JvmWindowsNamedPipeBridge.closeArena(arena)
            return false
        }

        val pipeNameSeg = JvmWindowsNamedPipeBridge.allocateUtf16String(arena, pipeName)
        val securityAttrs = JvmWindowsNamedPipeBridge.createCurrentUserSecurityAttributes(arena)
        val captureSeg = JvmWindowsNamedPipeBridge.allocateCaptureState(arena)
        val firstPipe = createPipeInstance(
            captureSeg = captureSeg,
            pipeNameSeg = pipeNameSeg,
            securityAttrs = securityAttrs,
            bufferSize = config.pipeBufferSizeBytes,
            isFirst = true
        )
        if (!JvmWindowsNamedPipeBridge.isValidHandle(firstPipe)) {
            JvmWindowsNamedPipeBridge.closeHandle(sEvent)
            JvmWindowsNamedPipeBridge.closeHandle(cEvent)
            JvmWindowsNamedPipeBridge.closeArena(arena)
            return false
        }

        val threadCounter = AtomicInteger(1)
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "GenericUI-IPC-Pipe-Client-${threadCounter.getAndIncrement()}").apply {
                isDaemon = true
            }
        }
        val latch = CountDownLatch(1)

        serverArena = arena
        stopEvent = sEvent
        connectEvent = cEvent
        clientExecutor = executor
        acceptDoneLatch = latch
        stopped.set(false)
        boundAddress = IpcChannelAddress(
            transportKind = IpcChannelAddress.TRANSPORT_WINDOWS_NAMED_PIPE,
            endpointAddress = pipeName
        )

        acceptThread = thread(isDaemon = true, name = "GenericUI-IPC-Pipe-Accept") {
            var pendingPipe: Any? = firstPipe
            try {
                val ovSeg = JvmWindowsNamedPipeBridge.allocateOverlapped(arena)
                val transferredSeg = JvmWindowsNamedPipeBridge.allocateZeroed(arena, 4L, 4L)
                val handlesSeg = JvmWindowsNamedPipeBridge.allocateHandlePair(arena, cEvent, sEvent)

                while (!stopped.get()) {
                    val hPipe = pendingPipe ?: createPipeInstance(
                        captureSeg = captureSeg,
                        pipeNameSeg = pipeNameSeg,
                        securityAttrs = securityAttrs,
                        bufferSize = config.pipeBufferSizeBytes,
                        isFirst = false
                    )
                    pendingPipe = null
                    if (!JvmWindowsNamedPipeBridge.isValidHandle(hPipe)) {
                        if (stopped.get()) break
                        if (JvmWindowsNamedPipeBridge.waitForSingleObject(sEvent, 25) == JvmWindowsNamedPipeBridge.WAIT_OBJECT_0) {
                            break
                        }
                        continue
                    }

                    JvmWindowsNamedPipeBridge.resetEvent(cEvent)
                    JvmWindowsNamedPipeBridge.zeroOverlappedWithEvent(ovSeg, cEvent)

                    val ok = JvmWindowsNamedPipeBridge.connectNamedPipe(captureSeg, hPipe, ovSeg)
                    if (ok != 0) {
                        if (stopped.get()) {
                            JvmWindowsNamedPipeBridge.disconnectNamedPipe(hPipe)
                            JvmWindowsNamedPipeBridge.closeHandle(hPipe)
                            break
                        }
                        val conn = JvmWin32NamedPipeConnection(hPipe, config.maxPacketBytes)
                        executor.execute { onClientConnected(conn) }
                        continue
                    }

                    when (JvmWindowsNamedPipeBridge.readCapturedLastError(captureSeg)) {
                        JvmWindowsNamedPipeBridge.ERROR_PIPE_CONNECTED -> {
                            if (stopped.get()) {
                                JvmWindowsNamedPipeBridge.disconnectNamedPipe(hPipe)
                                JvmWindowsNamedPipeBridge.closeHandle(hPipe)
                                break
                            }
                            val conn = JvmWin32NamedPipeConnection(hPipe, config.maxPacketBytes)
                            executor.execute { onClientConnected(conn) }
                        }
                        JvmWindowsNamedPipeBridge.ERROR_IO_PENDING -> {
                            val waitRes = JvmWindowsNamedPipeBridge.waitForMultipleObjects(
                                capture = captureSeg,
                                count = 2,
                                handlesSeg = handlesSeg,
                                waitAll = 0,
                                timeoutMs = JvmWindowsNamedPipeBridge.INFINITE
                            )
                            if (waitRes == JvmWindowsNamedPipeBridge.WAIT_OBJECT_0) {
                                val resOk = JvmWindowsNamedPipeBridge.getOverlappedResult(
                                    capture = captureSeg,
                                    hFile = hPipe,
                                    overlapped = ovSeg,
                                    bytesTransferredOut = transferredSeg,
                                    wait = 1
                                )
                                if (resOk != 0 && !stopped.get()) {
                                    val conn = JvmWin32NamedPipeConnection(hPipe, config.maxPacketBytes)
                                    executor.execute { onClientConnected(conn) }
                                } else {
                                    if (resOk != 0) {
                                        JvmWindowsNamedPipeBridge.disconnectNamedPipe(hPipe)
                                    }
                                    JvmWindowsNamedPipeBridge.closeHandle(hPipe)
                                }
                            } else {
                                JvmWindowsNamedPipeBridge.cancelIoEx(hPipe, ovSeg)
                                JvmWindowsNamedPipeBridge.getOverlappedResult(
                                    capture = captureSeg,
                                    hFile = hPipe,
                                    overlapped = ovSeg,
                                    bytesTransferredOut = transferredSeg,
                                    wait = 1
                                )
                                JvmWindowsNamedPipeBridge.closeHandle(hPipe)
                                break
                            }
                        }
                        else -> {
                            JvmWindowsNamedPipeBridge.closeHandle(hPipe)
                            if (stopped.get()) break
                            if (JvmWindowsNamedPipeBridge.waitForSingleObject(sEvent, 25) == JvmWindowsNamedPipeBridge.WAIT_OBJECT_0) {
                                break
                            }
                        }
                    }
                }
            } finally {
                if (pendingPipe != null && JvmWindowsNamedPipeBridge.isValidHandle(pendingPipe)) {
                    JvmWindowsNamedPipeBridge.closeHandle(pendingPipe)
                }
                latch.countDown()
            }
        }
        return true
    }

    fun close() {
        if (!stopped.compareAndSet(false, true)) {
            return
        }
        boundAddress = null
        val sEvent = stopEvent
        val cEvent = connectEvent
        val arena = serverArena
        val latch = acceptDoneLatch
        val thread = acceptThread
        val executor = clientExecutor

        if (sEvent != null) {
            JvmWindowsNamedPipeBridge.setEvent(sEvent)
        }
        if (latch != null && Thread.currentThread() !== thread) {
            try {
                latch.await(2_000L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        stopEvent = null
        connectEvent = null
        serverArena = null
        acceptDoneLatch = null
        acceptThread = null
        clientExecutor = null

        executor?.shutdown()
        sEvent?.let { JvmWindowsNamedPipeBridge.closeHandle(it) }
        cEvent?.let { JvmWindowsNamedPipeBridge.closeHandle(it) }
        arena?.let { JvmWindowsNamedPipeBridge.closeArena(it) }
    }

    private fun createPipeInstance(
        captureSeg: Any,
        pipeNameSeg: Any,
        securityAttrs: Any,
        bufferSize: Int,
        isFirst: Boolean
    ): Any {
        val baseOpenMode = JvmWindowsNamedPipeBridge.PIPE_ACCESS_DUPLEX or
            JvmWindowsNamedPipeBridge.FILE_FLAG_OVERLAPPED
        val openMode = if (isFirst) {
            baseOpenMode or JvmWindowsNamedPipeBridge.FILE_FLAG_FIRST_PIPE_INSTANCE
        } else {
            baseOpenMode
        }
        val pipeMode = JvmWindowsNamedPipeBridge.PIPE_TYPE_BYTE or
            JvmWindowsNamedPipeBridge.PIPE_READMODE_BYTE or
            JvmWindowsNamedPipeBridge.PIPE_WAIT or
            JvmWindowsNamedPipeBridge.PIPE_REJECT_REMOTE_CLIENTS
        val effectiveBuffer = bufferSize.coerceAtLeast(4096)
        return JvmWindowsNamedPipeBridge.createNamedPipeW(
            capture = captureSeg,
            pipeNameSeg = pipeNameSeg,
            openMode = openMode,
            pipeMode = pipeMode,
            maxInstances = JvmWindowsNamedPipeBridge.PIPE_UNLIMITED_INSTANCES,
            outBufferSize = effectiveBuffer,
            inBufferSize = effectiveBuffer,
            defaultTimeout = 0,
            securityAttrs = securityAttrs
        )
    }
}
