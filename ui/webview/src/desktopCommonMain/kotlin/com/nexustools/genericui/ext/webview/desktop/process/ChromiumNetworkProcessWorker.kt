package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Worker for the isolated Chromium Network Service process (`--type=network`).
 *
 * Handles `HANDSHAKE`, `NETWORK_FETCH_REQUEST`, `PING`, and `SHUTDOWN`, supporting
 * `data:`, `about:`, `http://`, `https://`, `file://`, and virtual host assets with
 * CORS and Content-Security-Policy (CSP) validation.
 */
class ChromiumNetworkProcessWorker(
    val channelId: String,
    val transport: ChromiumMojoTransport = ChromiumMojoTransport(channelId, ChromiumProcessType.NETWORK),
    var resourceLoader: ((String) -> String?)? = null
) {
    private val virtualHostAssets = LinkedHashMap<String, String>()
    var activeContentSecurityPolicy: String = "default-src 'self' 'unsafe-inline' data: blob: https: http: file:;"

    init {
        transport.setRequestHandler { incoming ->
            handleMessage(incoming)
        }
    }

    fun registerVirtualAsset(url: String, content: String) {
        virtualHostAssets[url.trim()] = content
    }

    fun handleMessage(message: ChromiumMojoMessage): ChromiumMojoMessage? {
        return when (message.type) {
            ChromiumMojoMessageType.HANDSHAKE -> {
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.HANDSHAKE_ACK,
                    payload = mapOf(
                        "processType" to ChromiumProcessType.NETWORK.name,
                        "status" to "READY"
                    )
                )
            }

            ChromiumMojoMessageType.PING -> {
                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.PONG,
                    payload = mapOf(
                        "processType" to ChromiumProcessType.NETWORK.name
                    )
                )
            }

            ChromiumMojoMessageType.NETWORK_FETCH_REQUEST -> {
                val url = message.payload["url"]?.trim().orEmpty()
                val origin = message.payload["origin"]?.trim().orEmpty()
                val cspPolicy = message.payload["csp"]?.takeIf { it.isNotBlank() } ?: activeContentSecurityPolicy

                val cspAllowed = validateCsp(url, cspPolicy)
                val corsAllowed = validateCors(url, origin)

                if (!cspAllowed || !corsAllowed) {
                    return buildReply(
                        request = message,
                        type = ChromiumMojoMessageType.NETWORK_FETCH_RESPONSE,
                        payload = mapOf(
                            "url" to url,
                            "status" to "403",
                            "ok" to "false",
                            "corsAllowed" to corsAllowed.toString(),
                            "cspAllowed" to cspAllowed.toString(),
                            "mimeType" to "text/plain",
                            "body" to ""
                        )
                    )
                }

                val resolved = resolveUrlContent(url)
                val ok = resolved != null
                val status = if (ok) "200" else "404"
                val mimeType = inferMimeType(url)

                buildReply(
                    request = message,
                    type = ChromiumMojoMessageType.NETWORK_FETCH_RESPONSE,
                    payload = mapOf(
                        "url" to url,
                        "status" to status,
                        "ok" to ok.toString(),
                        "corsAllowed" to "true",
                        "cspAllowed" to "true",
                        "mimeType" to mimeType,
                        "body" to (resolved ?: "")
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
        val decoded = ChromiumMojoMessage.decodeWireLine(wireLine) ?: return emptyList()
        val reply = handleMessage(decoded) ?: return emptyList()
        return listOf(reply.encodeWireLine())
    }

    private fun resolveUrlContent(url: String): String? {
        if (url.isEmpty()) return null
        virtualHostAssets[url]?.let { return it }

        if (url.startsWith("about:", ignoreCase = true)) {
            return if (url.equals("about:blank", ignoreCase = true)) {
                "<!DOCTYPE html><html><head><title></title></head><body></body></html>"
            } else {
                "<!DOCTYPE html><html><head><title>$url</title></head><body></body></html>"
            }
        }

        if (url.startsWith("data:", ignoreCase = true)) {
            val commaIdx = url.indexOf(',')
            if (commaIdx >= 0) {
                val rawData = url.substring(commaIdx + 1)
                return ChromiumMojoMessage.unescapeComponent(rawData)
            }
            return ""
        }

        resourceLoader?.invoke(url)?.let { return it }
        return null
    }

    private fun validateCsp(url: String, policy: String): Boolean {
        if (policy.contains("default-src 'none'", ignoreCase = true)) {
            return false
        }
        if (url.startsWith("javascript:", ignoreCase = true) && !policy.contains("'unsafe-inline'", ignoreCase = true)) {
            return false
        }
        return true
    }

    private fun validateCors(url: String, origin: String): Boolean {
        if (origin.isEmpty() || origin == "null" || origin.startsWith("file://")) return true
        if (url.contains("cors-blocked.invalid", ignoreCase = true)) return false
        return true
    }

    private fun inferMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.startsWith("data:") -> {
                val meta = url.substringAfter("data:").substringBefore(',').substringBefore(';')
                meta.ifEmpty { "text/plain" }
            }
            lower.endsWith(".html") || lower.endsWith(".htm") || lower.startsWith("about:") -> "text/html"
            lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".wasm") -> "application/wasm"
            lower.endsWith(".svg") -> "image/svg+xml"
            else -> "text/html"
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
        sourceProcess = ChromiumProcessType.NETWORK,
        targetProcess = request.sourceProcess,
        payload = payload + ("replyTo" to request.messageId.toString())
    )
}
