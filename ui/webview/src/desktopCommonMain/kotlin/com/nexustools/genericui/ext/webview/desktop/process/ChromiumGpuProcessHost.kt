package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Browser-process host managing the isolated Chromium GPU / Compositor process (`--type=gpu`).
 */
class ChromiumGpuProcessHost(
    val channelId: String = "chromium-gpu-0"
) {
    var state: ChromiumProcessState = ChromiumProcessState.STARTING
        private set

    var worker: ChromiumGpuProcessWorker = ChromiumGpuProcessWorker(channelId)
        private set

    var transport: ChromiumMojoTransport = ChromiumSubprocessSpawner.spawnSubprocess(
        processType = ChromiumProcessType.GPU,
        channelId = channelId,
        workerTransport = worker.transport
    )
        private set

    val processId: Long
        get() = transport.processId

    val isExternalOsProcess: Boolean
        get() = transport.isExternalOsProcess

    var rasterizedFrameCount: Int = 0
        private set

    var lastRasterizedFrameChecksum: Long = 0L
        private set

    var lastRasterizedFrameChecksumHex: String = ""
        private set

    var lastRasterizedRgbaSummary: String = ""
        private set

    var lastNonBlankPixelCount: Long = 0L
        private set

    private var restartSequence: Int = 0

    init {
        performHandshake()
    }

    private fun performHandshake(): Boolean {
        val ack = transport.sendRequest(
            type = ChromiumMojoMessageType.HANDSHAKE,
            targetProcess = ChromiumProcessType.GPU,
            payload = mapOf("channelId" to transport.channelId)
        )
        state = if (ack?.type == ChromiumMojoMessageType.HANDSHAKE_ACK) {
            ChromiumProcessState.RUNNING
        } else {
            ChromiumProcessState.CRASHED
        }
        return state == ChromiumProcessState.RUNNING
    }

    fun rasterizeFrame(compositorFramePayload: Map<String, String>): Map<String, String> {
        if (state != ChromiumProcessState.RUNNING) {
            restartProcess()
        }
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.RASTERIZE_DISPLAY_LIST,
            targetProcess = ChromiumProcessType.GPU,
            payload = compositorFramePayload
        )
        if (response == null || response.type != ChromiumMojoMessageType.RASTERIZE_FRAME_COMPLETE) {
            state = ChromiumProcessState.CRASHED
            return emptyMap()
        }
        rasterizedFrameCount++
        lastRasterizedFrameChecksum = response.payload["pixelChecksum"]?.toLongOrNull() ?: 0L
        lastRasterizedFrameChecksumHex = response.payload["pixelChecksumHex"].orEmpty()
        lastRasterizedRgbaSummary = response.payload["rgbaSummary"].orEmpty()
        lastNonBlankPixelCount = response.payload["nonBlankPixelCount"]?.toLongOrNull() ?: 0L
        return response.payload
    }

    fun ping(): Boolean {
        if (state != ChromiumProcessState.RUNNING) return false
        val pong = transport.sendRequest(
            type = ChromiumMojoMessageType.PING,
            targetProcess = ChromiumProcessType.GPU
        )
        return pong?.type == ChromiumMojoMessageType.PONG
    }

    fun restartProcess(): Boolean {
        runCatching { transport.close() }
        restartSequence++
        val nextChannelId = "$channelId-r$restartSequence"
        state = ChromiumProcessState.STARTING
        worker = ChromiumGpuProcessWorker(nextChannelId)
        transport = ChromiumSubprocessSpawner.spawnSubprocess(
            processType = ChromiumProcessType.GPU,
            channelId = nextChannelId,
            workerTransport = worker.transport
        )
        return performHandshake()
    }

    fun shutdown() {
        if (state == ChromiumProcessState.TERMINATED) return
        if (!transport.isClosed) {
            runCatching {
                transport.sendRequest(
                    type = ChromiumMojoMessageType.SHUTDOWN,
                    targetProcess = ChromiumProcessType.GPU
                )
            }
            transport.close()
        }
        state = ChromiumProcessState.TERMINATED
    }
}
