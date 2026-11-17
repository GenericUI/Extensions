package com.nexustools.genericui.ext.aichat

/**
 * Streaming client for Google Gemini (`/v1beta/models/{model}:streamGenerateContent?alt=sse`) supporting:
 * - Thinking budget (`thinkingConfig: { includeThoughts: true, thinkingBudget: ... }`).
 * - Function declarations (`tools: [{ functionDeclarations: [...] }]`), `functionCall`, and `functionResponse`.
 * - Live SSE streaming of thought parts (`"thought": true`), content parts, tool calls, and `usageMetadata`.
 */
class GeminiBackendClient(
    private val sseTransport: (suspend (
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        onLine: suspend (String) -> Unit
    ) -> Unit)? = null
) : AiChatBackendClient {

    override val backendType: AiBackendType = AiBackendType.GEMINI

    fun buildRequestJson(
        config: AiBackendConfig,
        messages: List<AiChatMessage>,
        tools: List<AiToolDefinition>
    ): String {
        val sb = StringBuilder()
        sb.append("{")

        val sysPrompt = messages.firstOrNull { it.role == AiMessageRole.SYSTEM }?.content
            ?.ifBlank { config.systemPrompt } ?: config.systemPrompt
        if (sysPrompt.isNotBlank()) {
            sb.append("\"systemInstruction\":{\"parts\":[{\"text\":\"").append(escapeJson(sysPrompt)).append("\"}]},")
        }

        sb.append("\"contents\":[")
        var firstMsg = true
        for (msg in messages) {
            if (msg.role == AiMessageRole.SYSTEM) continue
            if (!firstMsg) sb.append(",")
            firstMsg = false
            if (msg.role == AiMessageRole.TOOL && msg.toolResults.isNotEmpty()) {
                sb.append("{\"role\":\"user\",\"parts\":[")
                msg.toolResults.forEachIndexed { idx, tr ->
                    if (idx > 0) sb.append(",")
                    sb.append("{\"functionResponse\":{\"name\":\"").append(escapeJson(tr.toolName))
                        .append("\",\"response\":{\"result\":\"").append(escapeJson(tr.outputContent)).append("\"}}}")
                }
                sb.append("]}")
            } else {
                val wireRole = if (msg.role == AiMessageRole.ASSISTANT) "model" else "user"
                sb.append("{\"role\":\"").append(wireRole).append("\",\"parts\":[")
                var hasPart = false
                if (msg.content.isNotEmpty() || msg.toolCalls.isEmpty()) {
                    sb.append("{\"text\":\"").append(escapeJson(msg.content)).append("\"}")
                    hasPart = true
                }
                for (tc in msg.toolCalls) {
                    if (hasPart) sb.append(",")
                    hasPart = true
                    sb.append("{\"functionCall\":{\"name\":\"").append(escapeJson(tc.name))
                        .append("\",\"args\":").append(tc.argumentsJson.ifBlank { "{}" }).append("}}")
                }
                sb.append("]}")
            }
        }
        sb.append("],")

        sb.append("\"generationConfig\":{")
        sb.append("\"temperature\":").append(config.temperature).append(",")
        sb.append("\"maxOutputTokens\":").append(config.maxOutputTokens).append(",")
        sb.append("\"topP\":").append(config.topP)
        if (config.reasoningConfig.isReasoningActive) {
            sb.append(",\"thinkingConfig\":{\"includeThoughts\":true,\"thinkingBudget\":")
                .append(config.reasoningConfig.resolvedBudgetTokens).append("}")
        }
        sb.append("}")

        if (tools.isNotEmpty()) {
            sb.append(",\"tools\":[{\"functionDeclarations\":[")
            tools.forEachIndexed { idx, tool ->
                if (idx > 0) sb.append(",")
                sb.append("{\"name\":\"").append(escapeJson(tool.name)).append("\",")
                sb.append("\"description\":\"").append(escapeJson(tool.description)).append("\",")
                sb.append("\"parameters\":").append(tool.parametersJsonSchema.ifBlank { "{\"type\":\"object\"}" })
                sb.append("}")
            }
            sb.append("]}]")
        }

        sb.append("}")
        return sb.toString()
    }

    fun buildHeaders(config: AiBackendConfig): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream"
        )
        if (config.apiKey.isNotBlank()) {
            headers["x-goog-api-key"] = config.apiKey
        }
        headers.putAll(config.customHeaders)
        return headers
    }

    override suspend fun streamChat(
        config: AiBackendConfig,
        messages: List<AiChatMessage>,
        tools: List<AiToolDefinition>,
        onChunk: suspend (AiStreamChunk) -> Unit
    ): AiChatMessage {
        val url = "${config.baseUrl.trimEnd('/')}/models/${config.model}:streamGenerateContent?alt=sse"
        val headers = buildHeaders(config)
        val bodyJson = buildRequestJson(config, messages, tools)

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolCalls = mutableListOf<AiToolCall>()
        var promptTokens = 0
        var candidateTokens = 0
        var thoughtsTokens = 0

        val transport = sseTransport
        if (transport != null) {
            transport(url, headers, bodyJson) { rawLine ->
                val line = rawLine.trim()
                if (!line.startsWith("data:")) return@transport
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) return@transport

                if (payload.contains("\"functionCall\"")) {
                    val fnIdx = payload.indexOf("\"functionCall\"")
                    val sub = payload.substring(fnIdx)
                    val fnName = extractJsonStringField(sub, "name") ?: "gemini_tool"
                    val argsObj = extractJsonObjectField(sub, "args") ?: "{}"
                    val callId = "gemini_call_${toolCalls.size + 1}"
                    onChunk(AiStreamChunk(type = AiStreamChunkType.TOOL_CALL_START, toolCallId = callId, toolName = fnName))
                    onChunk(
                        AiStreamChunk(
                            type = AiStreamChunkType.TOOL_CALL_ARGS_DELTA,
                            toolCallId = callId,
                            toolName = fnName,
                            toolArgsDelta = argsObj
                        )
                    )
                    onChunk(AiStreamChunk(type = AiStreamChunkType.TOOL_CALL_END, toolCallId = callId, toolName = fnName))
                    toolCalls.add(AiToolCall(id = callId, name = fnName, argumentsJson = argsObj))
                } else {
                    val text = extractJsonStringField(payload, "text")
                    if (!text.isNullOrEmpty()) {
                        val isThought = payload.contains("\"thought\":true") || payload.contains("\"thought\": true")
                        if (isThought) {
                            reasoningBuilder.append(text)
                            onChunk(AiStreamChunk(type = AiStreamChunkType.REASONING_DELTA, reasoningDelta = text))
                        } else {
                            contentBuilder.append(text)
                            onChunk(AiStreamChunk(type = AiStreamChunkType.CONTENT_DELTA, textDelta = text))
                        }
                    }
                }

                if (payload.contains("\"usageMetadata\"")) {
                    extractJsonIntField(payload, "promptTokenCount")?.let { promptTokens = it }
                    extractJsonIntField(payload, "candidatesTokenCount")?.let { candidateTokens = it }
                    extractJsonIntField(payload, "thoughtsTokenCount")?.let { thoughtsTokens = it }
                }
            }
        } else {
            if (config.reasoningConfig.isReasoningActive) {
                val thought = "Gemini thinking (${config.reasoningConfig.effort.displayName} budget: ${config.reasoningConfig.resolvedBudgetTokens} tokens)..."
                reasoningBuilder.append(thought)
                onChunk(AiStreamChunk(type = AiStreamChunkType.REASONING_DELTA, reasoningDelta = thought))
            }
            val lastUser = messages.lastOrNull { it.role == AiMessageRole.USER }?.content.orEmpty()
            val reply = "[Gemini ${config.model}] Response to: $lastUser"
            contentBuilder.append(reply)
            onChunk(AiStreamChunk(type = AiStreamChunkType.CONTENT_DELTA, textDelta = reply))
            promptTokens = (messages.sumOf { it.content.length } / 4).coerceAtLeast(8)
            candidateTokens = (reply.length / 4).coerceAtLeast(8)
            thoughtsTokens = if (reasoningBuilder.isNotEmpty()) (reasoningBuilder.length / 4).coerceAtLeast(4) else 0
        }

        val usage = AiUsageMetrics(
            promptTokens = promptTokens,
            completionTokens = candidateTokens,
            reasoningTokens = thoughtsTokens,
            estimatedCostUsd = config.calculateCostUsd(promptTokens, candidateTokens, thoughtsTokens)
        )
        onChunk(AiStreamChunk(type = AiStreamChunkType.USAGE_UPDATE, usage = usage))
        onChunk(AiStreamChunk(type = AiStreamChunkType.MESSAGE_COMPLETE, usage = usage))

        return AiChatMessage(
            id = "msg_gemini_${messages.size + 1}",
            role = AiMessageRole.ASSISTANT,
            content = contentBuilder.toString(),
            reasoningContent = reasoningBuilder.toString(),
            toolCalls = toolCalls,
            usage = usage,
            modelId = config.model
        )
    }

    override suspend fun listModels(config: AiBackendConfig): List<String> = listOf(
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.0-flash"
    )

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun extractJsonStringField(json: String, field: String): String? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val colonIdx = json.indexOf(':', idx + key.length)
        if (colonIdx < 0) return null
        var pos = colonIdx + 1
        while (pos < json.length && json[pos].isWhitespace()) pos++
        if (pos >= json.length || json[pos] != '"') return null
        pos++
        val sb = StringBuilder()
        var escaped = false
        while (pos < json.length) {
            val c = json[pos++]
            if (escaped) {
                when (c) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(c)
                }
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
            }
        }
        return null
    }

    private fun extractJsonObjectField(json: String, field: String): String? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val braceStart = json.indexOf('{', idx + key.length)
        if (braceStart < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in braceStart until json.length) {
            val c = json[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return json.substring(braceStart, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractJsonIntField(json: String, field: String): Int? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val colonIdx = json.indexOf(':', idx + key.length)
        if (colonIdx < 0) return null
        var pos = colonIdx + 1
        while (pos < json.length && json[pos].isWhitespace()) pos++
        val start = pos
        while (pos < json.length && (json[pos].isDigit() || json[pos] == '-')) pos++
        return if (pos > start) json.substring(start, pos).toIntOrNull() else null
    }
}
