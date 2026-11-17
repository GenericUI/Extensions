package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Browser-process host managing the isolated Chromium Network Service process (`--type=network`).
 */
class ChromiumNetworkProcessHost(
    val channelId: String = "chromium-network-0",
    var resourceLoader: ((String) -> String?)? = null
) {
    var state: ChromiumProcessState = ChromiumProcessState.STARTING
        private set

    var worker: ChromiumNetworkProcessWorker = ChromiumNetworkProcessWorker(
        channelId = channelId,
        resourceLoader = resourceLoader
    )
        private set

    var transport: ChromiumMojoTransport = ChromiumSubprocessSpawner.spawnSubprocess(
        processType = ChromiumProcessType.NETWORK,
        channelId = channelId,
        workerTransport = worker.transport
    )
        private set

    val processId: Long
        get() = transport.processId

    val isExternalOsProcess: Boolean
        get() = transport.isExternalOsProcess

    var completedFetchCount: Int = 0
        private set

    private val virtualAssets = LinkedHashMap<String, String>()
    private var restartSequence: Int = 0

    init {
        performHandshake()
    }

    private fun performHandshake(): Boolean {
        val ack = transport.sendRequest(
            type = ChromiumMojoMessageType.HANDSHAKE,
            targetProcess = ChromiumProcessType.NETWORK,
            payload = mapOf("channelId" to transport.channelId)
        )
        state = if (ack?.type == ChromiumMojoMessageType.HANDSHAKE_ACK) {
            ChromiumProcessState.RUNNING
        } else {
            ChromiumProcessState.CRASHED
        }
        return state == ChromiumProcessState.RUNNING
    }

    fun registerVirtualAsset(url: String, content: String) {
        virtualAssets[url.trim()] = content
        worker.registerVirtualAsset(url, content)
    }

    fun fetchResource(
        url: String,
        origin: String = "",
        csp: String = ""
    ): Map<String, String> {
        if (state != ChromiumProcessState.RUNNING) {
            restartProcess()
        }
        virtualAssets[url.trim()]?.let { assetBody ->
            completedFetchCount++
            return mapOf(
                "url" to url,
                "status" to "200",
                "ok" to "true",
                "corsAllowed" to "true",
                "cspAllowed" to "true",
                "body" to assetBody
            )
        }
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.NETWORK_FETCH_REQUEST,
            targetProcess = ChromiumProcessType.NETWORK,
            payload = mapOf(
                "url" to url,
                "origin" to origin,
                "csp" to csp
            )
        )
        if (response == null || response.type != ChromiumMojoMessageType.NETWORK_FETCH_RESPONSE) {
            state = ChromiumProcessState.CRASHED
            return emptyMap()
        }
        completedFetchCount++
        val payload = response.payload
        if (payload["ok"] != "true" && payload["corsAllowed"] != "false" && payload["cspAllowed"] != "false") {
            val localFallback = resourceLoader?.invoke(url)
            if (localFallback != null) {
                return payload + mapOf(
                    "status" to "200",
                    "ok" to "true",
                    "body" to localFallback
                )
            }
        }
        return payload
    }

    fun fetchResourceBody(url: String): String? {
        val res = fetchResource(url)
        return if (res["ok"] == "true") res["body"] else null
    }

    fun ping(): Boolean {
        if (state != ChromiumProcessState.RUNNING) return false
        val pong = transport.sendRequest(
            type = ChromiumMojoMessageType.PING,
            targetProcess = ChromiumProcessType.NETWORK
        )
        return pong?.type == ChromiumMojoMessageType.PONG
    }

    fun restartProcess(): Boolean {
        runCatching { transport.close() }
        restartSequence++
        val nextChannelId = "$channelId-r$restartSequence"
        state = ChromiumProcessState.STARTING
        worker = ChromiumNetworkProcessWorker(
            channelId = nextChannelId,
            resourceLoader = resourceLoader
        )
        for ((k, v) in virtualAssets) {
            worker.registerVirtualAsset(k, v)
        }
        transport = ChromiumSubprocessSpawner.spawnSubprocess(
            processType = ChromiumProcessType.NETWORK,
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
                    targetProcess = ChromiumProcessType.NETWORK
                )
            }
            transport.close()
        }
        state = ChromiumProcessState.TERMINATED
    }
}
