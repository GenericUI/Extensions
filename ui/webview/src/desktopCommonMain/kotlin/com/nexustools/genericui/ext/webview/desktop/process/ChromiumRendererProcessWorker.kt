package com.nexustools.genericui.ext.webview.desktop.process

import com.nexustools.genericui.ext.webview.desktop.ChromiumBlinkDomNode
import com.nexustools.genericui.ext.webview.desktop.ChromiumBlinkEngine
import com.nexustools.genericui.ext.webview.desktop.NjsDesktopWebRuntime
import com.nexustools.genericui.ext.webview.desktop.NjsPlatformCompilerBootstrap

/**
 * Worker for the isolated Chromium Renderer process (`--type=renderer`).
 *
 * Owns an isolated [ChromiumBlinkEngine] and [NjsDesktopWebRuntime] (`NJS`), handles
 * `HANDSHAKE`, `LOAD_URL`, `LOAD_HTML`, `EVALUATE_JS`, `POST_MESSAGE_TO_JS`, `SET_VIEWPORT_SIZE`,
 * and `PING`, forwards `window.GenericUIWebView.postMessage` / `window.genericUi.call` back to the
 * Browser process via `POST_MESSAGE_TO_HOST`, and submits compositor frames (`SUBMIT_COMPOSITOR_FRAME`)
 * with layout/render tree metrics.
 */
class ChromiumRendererProcessWorker(
    val channelId: String,
    val transport: ChromiumMojoTransport = ChromiumMojoTransport(channelId, ChromiumProcessType.RENDERER),
    var resourceLoader: ((String) -> String?)? = null
) {
    val blinkEngine: ChromiumBlinkEngine = ChromiumBlinkEngine()

    var viewportWidth: Int = 1280
        private set

    var viewportHeight: Int = 720
        private set

    var committedFrameCount: Int = 0
        private set

    var domMutationCount: Int = 0
        private set

    var lastSubmittedDisplayList: String = ""
        private set

    private var mutationFlagInCurrentTurn: Boolean = false
    private val outboundWireBuffer = ArrayList<String>()

    val activeEngineName: String
        get() {
            NjsPlatformCompilerBootstrap.ensureInitialized()
            return NjsPlatformCompilerBootstrap.activeEngineName()
        }

    val njsRuntime: NjsDesktopWebRuntime by lazy {
        NjsPlatformCompilerBootstrap.ensureInitialized()
        NjsDesktopWebRuntime(
            blinkEngine = blinkEngine,
            onPostMessage = { message ->
                emitMessageToBrowser(
                    ChromiumMojoMessageType.POST_MESSAGE_TO_HOST,
                    mapOf(
                        "kind" to "postMessage",
                        "message" to message
                    )
                )
            },
            onConsoleMessage = { consoleMsg ->
                emitMessageToBrowser(
                    ChromiumMojoMessageType.POST_MESSAGE_TO_HOST,
                    mapOf(
                        "kind" to "console",
                        "level" to consoleMsg.level,
                        "message" to consoleMsg.message,
                        "sourceId" to consoleMsg.sourceId,
                        "lineNumber" to consoleMsg.line.toString()
                    )
                )
            },
            onJavaScriptError = { errMsg ->
                emitMessageToBrowser(
                    ChromiumMojoMessageType.POST_MESSAGE_TO_HOST,
                    mapOf(
                        "kind" to "jsError",
                        "error" to errMsg
                    )
                )
            },
            resourceTextLoader = { targetUrl ->
                resolveExternalResource(targetUrl)
            },
            onBridgeCall = { channel, payload ->
                val reply = transport.sendRequest(
                    type = ChromiumMojoMessageType.POST_MESSAGE_TO_HOST,
                    targetProcess = ChromiumProcessType.BROWSER,
                    payload = mapOf(
                        "kind" to "bridgeCall",
                        "channel" to channel,
                        "payload" to payload
                    )
                )
                reply?.payload?.get("result")
            },
            onDomMutation = {
                domMutationCount++
                mutationFlagInCurrentTurn = true
            }
        )
    }

    init {
        transport.setRequestHandler { incoming ->
            handleMessage(incoming)
        }
    }

    private fun resolveExternalResource(urlOrPath: String): String? {
        resourceLoader?.invoke(urlOrPath)?.let { return it }
        val reply = transport.sendRequest(
            type = ChromiumMojoMessageType.NETWORK_FETCH_REQUEST,
            targetProcess = ChromiumProcessType.BROWSER,
            payload = mapOf("url" to urlOrPath)
        )
        if (reply != null && reply.payload["ok"] == "true") {
            return reply.payload["body"]
        }
        return null
    }

    private fun emitMessageToBrowser(type: ChromiumMojoMessageType, payload: Map<String, String>) {
        val msg = ChromiumMojoMessage(
            messageId = transport.nextMessageId(),
            channelId = channelId,
            type = type,
            sourceProcess = ChromiumProcessType.RENDERER,
            targetProcess = ChromiumProcessType.BROWSER,
            payload = payload
        )
        outboundWireBuffer.add(msg.encodeWireLine())
        transport.send(msg)
    }

    fun handleMessage(message: ChromiumMojoMessage): ChromiumMojoMessage? {
        return when (message.type) {
            ChromiumMojoMessageType.HANDSHAKE -> {
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.HANDSHAKE_ACK,
                    payload = mapOf(
                        "processType" to ChromiumProcessType.RENDERER.name,
                        "engine" to activeEngineName,
                        "status" to "READY"
                    )
                )
            }

            ChromiumMojoMessageType.PING -> {
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.PONG,
                    payload = mapOf(
                        "processType" to ChromiumProcessType.RENDERER.name,
                        "engine" to activeEngineName
                    )
                )
            }

            ChromiumMojoMessageType.SET_VIEWPORT_SIZE -> {
                val w = message.payload["width"]?.toIntOrNull()?.coerceAtLeast(1) ?: viewportWidth
                val h = message.payload["height"]?.toIntOrNull()?.coerceAtLeast(1) ?: viewportHeight
                viewportWidth = w
                viewportHeight = h
                val framePayload = commitDomAndSubmitCompositorFrame()
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                    payload = framePayload + mapOf(
                        "width" to viewportWidth.toString(),
                        "height" to viewportHeight.toString()
                    )
                )
            }

            ChromiumMojoMessageType.LOAD_URL -> {
                val url = message.payload["url"] ?: "about:blank"
                val html = message.payload["html"] ?: resolveExternalResource(url)
                val enableJs = message.payload["enableJavaScript"] != "false"
                blinkEngine.currentUrl = url
                if (html != null) {
                    executeHtmlInRenderer(html, url, enableJs)
                } else {
                    blinkEngine.readyState = "complete"
                }
                val framePayload = commitDomAndSubmitCompositorFrame()
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                    payload = framePayload + mapOf(
                        "url" to (blinkEngine.currentUrl ?: url),
                        "title" to blinkEngine.documentTitle,
                        "readyState" to blinkEngine.readyState
                    )
                )
            }

            ChromiumMojoMessageType.LOAD_HTML -> {
                val html = message.payload["html"] ?: ""
                val baseUrl = message.payload["baseUrl"] ?: "about:blank"
                val enableJs = message.payload["enableJavaScript"] != "false"
                executeHtmlInRenderer(html, baseUrl, enableJs)
                val framePayload = commitDomAndSubmitCompositorFrame()
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                    payload = framePayload + mapOf(
                        "url" to (blinkEngine.currentUrl ?: baseUrl),
                        "title" to blinkEngine.documentTitle,
                        "readyState" to blinkEngine.readyState
                    )
                )
            }

            ChromiumMojoMessageType.EVALUATE_JS -> {
                val kind = message.payload["kind"] ?: "script"
                when (kind) {
                    "registerHostFunction" -> {
                        val fnName = message.payload["name"].orEmpty()
                        if (fnName.isNotEmpty()) {
                            njsRuntime.registerCommunicationFunction(fnName) { argPayload ->
                                val reply = transport.sendRequest(
                                    type = ChromiumMojoMessageType.POST_MESSAGE_TO_HOST,
                                    targetProcess = ChromiumProcessType.BROWSER,
                                    payload = mapOf(
                                        "kind" to "bridgeCall",
                                        "channel" to fnName,
                                        "payload" to argPayload
                                    )
                                )
                                reply?.payload?.get("result") ?: "{}"
                            }
                        }
                        buildReply(
                            request = message,
                            type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                            payload = mapOf("registered" to fnName, "isNull" to "false")
                        )
                    }

                    "unregisterHostFunction" -> {
                        val fnName = message.payload["name"].orEmpty()
                        if (fnName.isNotEmpty()) {
                            njsRuntime.unregisterCommunicationFunction(fnName)
                        }
                        buildReply(
                            request = message,
                            type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                            payload = mapOf("unregistered" to fnName, "isNull" to "false")
                        )
                    }

                    "callCommunicationFunction" -> {
                        val fnName = message.payload["name"].orEmpty()
                        val argPayload = message.payload["payload"].orEmpty()
                        mutationFlagInCurrentTurn = false
                        val res = njsRuntime.callCommunicationFunction(fnName, argPayload)
                        val extraFrame = if (mutationFlagInCurrentTurn) commitDomAndSubmitCompositorFrame() else emptyMap()
                        buildReply(
                            request = message,
                            type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                            payload = extraFrame + mapOf(
                                "result" to (res ?: ""),
                                "isNull" to (res == null).toString(),
                                "title" to blinkEngine.documentTitle
                            )
                        )
                    }

                    else -> {
                        val script = message.payload["script"].orEmpty()
                        val sourceName = message.payload["sourceName"] ?: "<renderer-eval>"
                        mutationFlagInCurrentTurn = false
                        val evalOut = njsRuntime.evaluateScript(script, sourceName)
                        val extraFrame = if (mutationFlagInCurrentTurn) commitDomAndSubmitCompositorFrame() else emptyMap()
                        buildReply(
                            request = message,
                            type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                            payload = extraFrame + mapOf(
                                "result" to (evalOut ?: ""),
                                "isNull" to (evalOut == null).toString(),
                                "title" to blinkEngine.documentTitle
                            )
                        )
                    }
                }
            }

            ChromiumMojoMessageType.POST_MESSAGE_TO_JS -> {
                val msg = message.payload["message"].orEmpty()
                mutationFlagInCurrentTurn = false
                val delivered = njsRuntime.deliverHostMessageToGuest(msg)
                val extraFrame = if (mutationFlagInCurrentTurn) commitDomAndSubmitCompositorFrame() else emptyMap()
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.EVALUATE_JS_RESULT,
                    payload = extraFrame + mapOf(
                        "delivered" to delivered.toString()
                    )
                )
            }

            ChromiumMojoMessageType.SHUTDOWN -> {
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.PONG,
                    payload = mapOf("status" to "SHUTTING_DOWN")
                )
            }

            else -> null
        }
    }

    fun handleWireLine(wireLine: String): List<String> {
        outboundWireBuffer.clear()
        val decoded = ChromiumMojoMessage.decodeWireLine(wireLine) ?: return emptyList()
        val reply = handleMessage(decoded)
        if (reply != null) {
            outboundWireBuffer.add(reply.encodeWireLine())
        }
        return outboundWireBuffer.toList()
    }

    private fun executeHtmlInRenderer(html: String, effectiveUrl: String, enableJavaScript: Boolean) {
        mutationFlagInCurrentTurn = true
        domMutationCount++
        val scripts = blinkEngine.parseHtmlDocumentWithScripts(html, effectiveUrl)
        if (enableJavaScript) {
            for ((index, entry) in scripts.withIndex()) {
                val src = entry.src
                if (!src.isNullOrBlank()) {
                    val externalCode = resolveExternalResource(src)
                    if (externalCode != null) {
                        njsRuntime.evaluateScript(externalCode, src)
                    }
                }
                if (entry.inlineCode.isNotEmpty()) {
                    njsRuntime.evaluateScript(entry.inlineCode, "$effectiveUrl#script$index")
                }
            }
        }
        blinkEngine.readyState = "complete"
    }

    private fun commitDomAndSubmitCompositorFrame(): Map<String, String> {
        committedFrameCount++
        val nodeCount = countDomNodes(blinkEngine.rootNode)
        val displayList = buildDisplayListSummary(blinkEngine.rootNode)
        lastSubmittedDisplayList = displayList

        val domCommitPayload = mapOf(
            "frameNumber" to committedFrameCount.toString(),
            "domMutationCount" to domMutationCount.toString(),
            "domNodeCount" to nodeCount.toString(),
            "title" to blinkEngine.documentTitle,
            "url" to (blinkEngine.currentUrl ?: "about:blank")
        )
        emitMessageToBrowser(ChromiumMojoMessageType.DOM_MUTATION_COMMIT, domCommitPayload)

        val compositorPayload = mapOf(
            "frameNumber" to committedFrameCount.toString(),
            "viewportWidth" to viewportWidth.toString(),
            "viewportHeight" to viewportHeight.toString(),
            "domNodeCount" to nodeCount.toString(),
            "title" to blinkEngine.documentTitle,
            "url" to (blinkEngine.currentUrl ?: "about:blank"),
            "displayList" to displayList
        )
        emitMessageToBrowser(ChromiumMojoMessageType.SUBMIT_COMPOSITOR_FRAME, compositorPayload)
        return compositorPayload
    }

    private fun countDomNodes(node: ChromiumBlinkDomNode): Int {
        var total = 1
        for (child in node.children) {
            total += countDomNodes(child)
        }
        return total
    }

    private fun buildDisplayListSummary(root: ChromiumBlinkDomNode): String {
        val commands = ArrayList<String>()
        commands.add("CLEAR(0,0,$viewportWidth,$viewportHeight,#FFFFFFFF)")
        collectDisplayCommands(root, 0, commands)
        return commands.joinToString(";")
    }

    private fun collectDisplayCommands(
        node: ChromiumBlinkDomNode,
        depth: Int,
        out: MutableList<String>
    ) {
        val bg = node.style["background-color"] ?: node.style["background"] ?: ""
        val fg = node.style["color"] ?: "#000000"
        val yOffset = depth * 24
        val boxW = (viewportWidth - depth * 16).coerceAtLeast(32)
        out.add("BOX(${node.tagName},id=${node.id},x=${depth * 8},y=$yOffset,w=$boxW,h=24,bg=$bg,fg=$fg)")
        if (node.textContent.isNotEmpty()) {
            val snippet = node.textContent.replace(';', ',').take(64)
            out.add("TEXT(${node.tagName},id=${node.id},text=$snippet)")
        }
        for (child in node.children) {
            collectDisplayCommands(child, depth + 1, out)
        }
    }

    private fun buildReply(
        request: ChromiumMojoMessage,
        type: ChromiumMojoMessageType,
        payload: Map<String, String>
    ): ChromiumMojoMessage = ChromiumMojoMessage(
        messageId = transport.nextMessageId(),
        channelId = channelId,
        type = type,
        sourceProcess = ChromiumProcessType.RENDERER,
        targetProcess = request.sourceProcess,
        payload = payload + ("replyTo" to request.messageId.toString())
    )
}
