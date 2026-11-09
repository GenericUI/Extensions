package com.nexustools.genericui.ext.webview.desktop.process

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.desktop.ChromiumBlinkEngine
import com.nexustools.genericui.ext.webview.desktop.NjsDesktopWebRuntime

/**
 * Browser-process host managing an isolated Chromium Renderer process (`--type=renderer`).
 */
class ChromiumRenderProcessHost(
    val channelId: String = "chromium-renderer-0",
    var resourceLoader: ((String) -> String?)? = null
) {
    var state: ChromiumProcessState = ChromiumProcessState.STARTING
        private set

    var worker: ChromiumRendererProcessWorker = ChromiumRendererProcessWorker(
        channelId = channelId,
        resourceLoader = resourceLoader
    )
        private set

    var transport: ChromiumMojoTransport = ChromiumSubprocessSpawner.spawnSubprocess(
        processType = ChromiumProcessType.RENDERER,
        channelId = channelId,
        workerTransport = worker.transport
    )
        private set

    val processId: Long
        get() = transport.processId

    val isExternalOsProcess: Boolean
        get() = transport.isExternalOsProcess

    val blinkEngine: ChromiumBlinkEngine
        get() = worker.blinkEngine

    val njsRuntime: NjsDesktopWebRuntime
        get() = worker.njsRuntime

    val activeEngineName: String
        get() = worker.activeEngineName

    var onPostMessage: ((String) -> Unit)? = null
    var onConsoleMessage: ((WebViewConsoleMessage) -> Unit)? = null
    var onJavaScriptError: ((String) -> Unit)? = null
    var onDomMutationCommit: ((Map<String, String>) -> Unit)? = null
    var onCompositorFrameSubmitted: ((Map<String, String>) -> Unit)? = null
    var onNetworkFetchRequest: ((String) -> String?)? = null

    private val hostFunctions = LinkedHashMap<String, (String) -> String>()
    private var restartSequence: Int = 0

    init {
        bindTransportHandlers()
        performHandshake()
    }

    private fun bindTransportHandlers() {
        transport.addMessageListener { message ->
            when (message.type) {
                ChromiumMojoMessageType.POST_MESSAGE_TO_HOST -> {
                    when (message.payload["kind"]) {
                        "postMessage" -> {
                            onPostMessage?.invoke(message.payload["message"].orEmpty())
                        }
                        "console" -> {
                            onConsoleMessage?.invoke(
                                WebViewConsoleMessage(
                                    level = message.payload["level"] ?: "log",
                                    message = message.payload["message"].orEmpty(),
                                    sourceId = message.payload["sourceId"] ?: "<webview>",
                                    line = message.payload["lineNumber"]?.toIntOrNull() ?: 0
                                )
                            )
                        }
                        "jsError" -> {
                            onJavaScriptError?.invoke(message.payload["error"].orEmpty())
                        }
                    }
                }

                ChromiumMojoMessageType.DOM_MUTATION_COMMIT -> {
                    message.payload["title"]?.let { worker.blinkEngine.documentTitle = it }
                    message.payload["url"]?.let { worker.blinkEngine.currentUrl = it }
                    onDomMutationCommit?.invoke(message.payload)
                }

                ChromiumMojoMessageType.SUBMIT_COMPOSITOR_FRAME -> {
                    onCompositorFrameSubmitted?.invoke(message.payload)
                }

                else -> {}
            }
        }

        transport.setRequestHandler { request ->
            when (request.type) {
                ChromiumMojoMessageType.POST_MESSAGE_TO_HOST -> {
                    if (request.payload["kind"] == "bridgeCall") {
                        val channel = request.payload["channel"].orEmpty()
                        val argPayload = request.payload["payload"].orEmpty()
                        val handler = hostFunctions[channel]
                        val result = if (handler != null) {
                            runCatching { handler(argPayload) }.getOrElse { err ->
                                val msg = err.message ?: "BridgeError"
                                onJavaScriptError?.invoke(msg)
                                "{\"error\":\"$msg\"}"
                            }
                        } else {
                            "{}"
                        }
                        ChromiumMojoMessage(
                            messageId = transport.nextMessageId(),
                            channelId = transport.channelId,
                            type = ChromiumMojoMessageType.POST_MESSAGE_TO_JS,
                            sourceProcess = ChromiumProcessType.BROWSER,
                            targetProcess = ChromiumProcessType.RENDERER,
                            payload = mapOf(
                                "replyTo" to request.messageId.toString(),
                                "result" to result
                            )
                        )
                    } else {
                        null
                    }
                }

                ChromiumMojoMessageType.NETWORK_FETCH_REQUEST -> {
                    val url = request.payload["url"].orEmpty()
                    val body = onNetworkFetchRequest?.invoke(url) ?: resourceLoader?.invoke(url)
                    ChromiumMojoMessage(
                        messageId = transport.nextMessageId(),
                        channelId = transport.channelId,
                        type = ChromiumMojoMessageType.NETWORK_FETCH_RESPONSE,
                        sourceProcess = ChromiumProcessType.BROWSER,
                        targetProcess = ChromiumProcessType.RENDERER,
                        payload = mapOf(
                            "replyTo" to request.messageId.toString(),
                            "url" to url,
                            "ok" to (body != null).toString(),
                            "status" to if (body != null) "200" else "404",
                            "body" to (body ?: "")
                        )
                    )
                }

                else -> null
            }
        }
    }

    private fun performHandshake(): Boolean {
        val ack = transport.sendRequest(
            type = ChromiumMojoMessageType.HANDSHAKE,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf("channelId" to transport.channelId)
        )
        state = if (ack?.type == ChromiumMojoMessageType.HANDSHAKE_ACK) {
            ChromiumProcessState.RUNNING
        } else {
            ChromiumProcessState.CRASHED
        }
        return state == ChromiumProcessState.RUNNING
    }

    fun ensureRunning() {
        if (state != ChromiumProcessState.RUNNING) {
            restartProcess()
        }
    }

    fun loadUrl(
        url: String,
        html: String? = null,
        enableJavaScript: Boolean = true
    ): Map<String, String> {
        ensureRunning()
        worker.blinkEngine.currentUrl = url
        val reqPayload = LinkedHashMap<String, String>()
        reqPayload["url"] = url
        reqPayload["enableJavaScript"] = enableJavaScript.toString()
        if (html != null) {
            reqPayload["html"] = html
            if (isExternalOsProcess) {
                worker.blinkEngine.parseHtmlDocumentWithScripts(html, url)
            }
        }
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.LOAD_URL,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = reqPayload
        )
        if (response == null) {
            state = ChromiumProcessState.CRASHED
            return emptyMap()
        }
        syncBlinkMetadataFromResponse(response.payload)
        return response.payload
    }

    fun loadHtml(
        html: String,
        baseUrl: String = "about:blank",
        enableJavaScript: Boolean = true
    ): Map<String, String> {
        ensureRunning()
        worker.blinkEngine.currentUrl = baseUrl
        if (isExternalOsProcess) {
            worker.blinkEngine.parseHtmlDocumentWithScripts(html, baseUrl)
        }
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.LOAD_HTML,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf(
                "html" to html,
                "baseUrl" to baseUrl,
                "enableJavaScript" to enableJavaScript.toString()
            )
        )
        if (response == null) {
            state = ChromiumProcessState.CRASHED
            return emptyMap()
        }
        syncBlinkMetadataFromResponse(response.payload)
        return response.payload
    }

    fun evaluateJavaScript(
        script: String,
        sourceName: String = "<evaluateJavascript>"
    ): String? {
        ensureRunning()
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.EVALUATE_JS,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf(
                "kind" to "script",
                "script" to script,
                "sourceName" to sourceName
            )
        )
        if (response == null) {
            state = ChromiumProcessState.CRASHED
            return null
        }
        syncBlinkMetadataFromResponse(response.payload)
        return if (response.payload["isNull"] == "true") null else response.payload["result"]
    }

    fun postMessageToJs(message: String): Boolean {
        ensureRunning()
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.POST_MESSAGE_TO_JS,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf("message" to message)
        )
        return response?.payload?.get("delivered") == "true"
    }

    fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        hostFunctions[clean] = handler
        ensureRunning()
        transport.sendRequest(
            type = ChromiumMojoMessageType.EVALUATE_JS,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf(
                "kind" to "registerHostFunction",
                "name" to clean
            )
        )
    }

    fun unregisterCommunicationFunction(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        hostFunctions.remove(clean)
        if (state == ChromiumProcessState.RUNNING) {
            transport.sendRequest(
                type = ChromiumMojoMessageType.EVALUATE_JS,
                targetProcess = ChromiumProcessType.RENDERER,
                payload = mapOf(
                    "kind" to "unregisterHostFunction",
                    "name" to clean
                )
            )
        }
    }

    fun callCommunicationFunction(name: String, payloadJson: String = ""): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        ensureRunning()
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.EVALUATE_JS,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf(
                "kind" to "callCommunicationFunction",
                "name" to clean,
                "payload" to payloadJson
            )
        )
        if (response != null && response.payload["isNull"] != "true") {
            return response.payload["result"]
        }
        return hostFunctions[clean]?.invoke(payloadJson)
    }

    fun setViewportSize(width: Int, height: Int): Map<String, String> {
        ensureRunning()
        val response = transport.sendRequest(
            type = ChromiumMojoMessageType.SET_VIEWPORT_SIZE,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf(
                "width" to width.coerceAtLeast(1).toString(),
                "height" to height.coerceAtLeast(1).toString()
            )
        )
        return response?.payload ?: emptyMap()
    }

    fun ping(): Boolean {
        if (state != ChromiumProcessState.RUNNING) return false
        val pong = transport.sendRequest(
            type = ChromiumMojoMessageType.PING,
            targetProcess = ChromiumProcessType.RENDERER
        )
        return pong?.type == ChromiumMojoMessageType.PONG
    }

    fun simulateCrash() {
        state = ChromiumProcessState.CRASHED
        transport.close()
    }

    fun restartProcess(): Boolean {
        runCatching { transport.close() }
        restartSequence++
        val nextChannelId = "$channelId-r$restartSequence"
        state = ChromiumProcessState.STARTING
        worker = ChromiumRendererProcessWorker(
            channelId = nextChannelId,
            resourceLoader = resourceLoader
        )
        transport = ChromiumSubprocessSpawner.spawnSubprocess(
            processType = ChromiumProcessType.RENDERER,
            channelId = nextChannelId,
            workerTransport = worker.transport
        )
        bindTransportHandlers()
        val ok = performHandshake()
        if (ok) {
            for (fnName in hostFunctions.keys) {
                transport.sendRequest(
                    type = ChromiumMojoMessageType.EVALUATE_JS,
                    targetProcess = ChromiumProcessType.RENDERER,
                    payload = mapOf(
                        "kind" to "registerHostFunction",
                        "name" to fnName
                    )
                )
            }
        }
        return ok
    }

    fun shutdown() {
        if (state == ChromiumProcessState.TERMINATED) return
        if (!transport.isClosed) {
            runCatching {
                transport.sendRequest(
                    type = ChromiumMojoMessageType.SHUTDOWN,
                    targetProcess = ChromiumProcessType.RENDERER
                )
            }
            transport.close()
        }
        state = ChromiumProcessState.TERMINATED
    }

    private fun syncBlinkMetadataFromResponse(payload: Map<String, String>) {
        payload["url"]?.takeIf { it.isNotEmpty() }?.let { worker.blinkEngine.currentUrl = it }
        payload["title"]?.let { worker.blinkEngine.documentTitle = it }
        payload["readyState"]?.takeIf { it.isNotEmpty() }?.let { worker.blinkEngine.readyState = it }
    }
}
