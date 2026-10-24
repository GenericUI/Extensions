package com.nexustools.genericui.ext.aichat

/**
 * Streaming client for Anthropic Claude (`/v1/messages`) supporting Extended Thinking (`thinking` block
 * with configurable `budget_tokens`), tool definitions (`input_schema`), `tool_use` / `tool_result` blocks,
 * and SSE streaming (`content_block_start`, `content_block_delta`, `message_delta`).
 */
class AnthropicBackendClient(
    private val sseTransport: (suspend (
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        onLine: suspend (String) -> Unit
    ) -> Unit)? = null
) : AiChatBackendClient {

    override val backendType: AiBackendType = AiBackendType.ANTHROPIC

    fun buildRequestJson(
        config: AiBackendConfig,
        messages: List<AiChatMessage>,
        tools: List<AiToolDefinition>
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"model\":\"").append(escapeJson(config.model)).append("\",")
        sb.append("\"max_tokens\":").append(config.maxOutputTokens).append(",")
        sb.append("\"stream\":true")

        val systemMsg = messages.firstOrNull { it.role == AiMessageRole.SYSTEM }?.content
            ?.ifBlank { config.systemPrompt } ?: config.systemPrompt
        if (systemMsg.isNotBlank()) {
            sb.append(",\"system\":\"").append(escapeJson(systemMsg)).append("\"")
        }

        if (config.reasoningConfig.isReasoningActive) {
            val budget = config.reasoningConfig.resolvedBudgetTokens
            sb.append(",\"thinking\":{\"type\":\"enabled\",\"budget_tokens\":").append(budget).append("}")
        } else {
            sb.append(",\"temperature\":").append(config.temperature)
        }

        sb.append(",\"messages\":[")
        var firstMsg = true
        for (msg in messages) {
            if (msg.role == AiMessageRole.SYSTEM) continue
            if (!firstMsg) sb.append(",")
            firstMsg = false
            if (msg.role == AiMessageRole.TOOL && msg.toolResults.isNotEmpty()) {
                sb.append("{\"role\":\"user\",\"content\":[")
                msg.toolResults.forEachIndexed { idx, tr ->
                    if (idx > 0) sb.append(",")
                    sb.append("{\"type\":\"tool_result\",\"tool_use_id\":\"")
                        .append(escapeJson(tr.toolCallId)).append("\",\"content\":\"")
                        .append(escapeJson(tr.outputContent)).append("\"")
                    if (tr.isError) sb.append(",\"is_error\":true")
                    sb.append("}")
                }
                sb.append("]}")
            } else {
                val wireRole = if (msg.role == AiMessageRole.ASSISTANT) "assistant" else "user"
                sb.append("{\"role\":\"").append(wireRole).append("\",\"content\":[")
                var hasPart = false
                if (msg.content.isNotEmpty() || msg.toolCalls.isEmpty()) {
                    sb.append("{\"type\":\"text\",\"text\":\"").append(escapeJson(msg.content)).append("\"}")
                    hasPart = true
                }
                for (tc in msg.toolCalls) {
                    if (hasPart) sb.append(",")
                    hasPart = true
                    sb.append("{\"type\":\"tool_use\",\"id\":\"").append(escapeJson(tc.id))
                        .append("\",\"name\":\"").append(escapeJson(tc.name))
                        .append("\",\"input\":").append(tc.argumentsJson.ifBlank { "{}" }).append("}")
                }
                sb.append("]}")
            }
        }
        sb.append("]")

        if (tools.isNotEmpty()) {
            sb.append(",\"tools\":[")
            tools.forEachIndexed { idx, tool ->
                if (idx > 0) sb.append(",")
                sb.append("{\"name\":\"").append(escapeJson(tool.name)).append("\",")
                sb.append("\"description\":\"").append(escapeJson(tool.description)).append("\",")
                sb.append("\"input_schema\":").append(tool.parametersJsonSchema.ifBlank { "{\"type\":\"object\"}" })
                sb.append("}")
            }
            sb.append("]")
        }

        sb.append("}")
        return sb.toString()
    }

    fun buildHeaders(config: AiBackendConfig): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream",
            "anthropic-version" to "2023-06-01"
        )
        if (config.apiKey.isNotBlank()) {
            headers["x-api-key"] = config.apiKey
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
        val url = "${config.baseUrl.trimEnd('/')}/messages"
        val headers = buildHeaders(config)
        val bodyJson = buildRequestJson(config, messages, tools)

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolCalls = mutableListOf<AiToolCall>()
        var activeToolId: String? = null
        var activeToolName: String? = null
        val activeToolArgs = StringBuilder()
        var inputTokens = 0
        var outputTokens = 0

        val transport = sseTransport
        if (transport != null) {
            transport(url, headers, bodyJson) { rawLine ->
                val line = rawLine.trim()
                if (!line.startsWith("data:")) return@transport
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) return@transport

                when {
                    payload.contains("\"type\":\"content_block_start\"") && payload.contains("\"tool_use\"") -> {
                        activeToolId = extractJsonStringField(payload, "id") ?: "toolu_${toolCalls.size + 1}"
                        activeToolName = extractJsonStringField(payload, "name") ?: "unknown_tool"
                        activeToolArgs.clear()
                        onChunk(
                            AiStreamChunk(
                                type = AiStreamChunkType.TOOL_CALL_START,
                                toolCallId = activeToolId,
                                toolName = activeToolName
                            )
                        )
                    }
                    payload.contains("\"thinking_delta\"") -> {
                        val thinking = extractJsonStringField(payload, "thinking")
                        if (!thinking.isNullOrEmpty()) {
                            reasoningBuilder.append(thinking)
                            onChunk(AiStreamChunk(type = AiStreamChunkType.REASONING_DELTA, reasoningDelta = thinking))
                        }
                    }
                    payload.contains("\"text_delta\"") -> {
                        val text = extractJsonStringField(payload, "text")
                        if (!text.isNullOrEmpty()) {
                            contentBuilder.append(text)
                            onChunk(AiStreamChunk(type = AiStreamChunkType.CONTENT_DELTA, textDelta = text))
                        }
                    }
                    payload.contains("\"input_json_delta\"") -> {
                        val partial = extractJsonStringField(payload, "partial_json")
                        if (!partial.isNullOrEmpty()) {
                            activeToolArgs.append(partial)
                            onChunk(
                                AiStreamChunk(
                                    type = AiStreamChunkType.TOOL_CALL_ARGS_DELTA,
                                    toolCallId = activeToolId,
                                    toolName = activeToolName,
                                    toolArgsDelta = partial
                                )
                            )
                        }
                    }
                    payload.contains("\"type\":\"content_block_stop\"") && activeToolName != null -> {
                        val id = activeToolId ?: "toolu_${toolCalls.size + 1}"
                        val name = activeToolName ?: "tool"
                        val args = activeToolArgs.toString().ifBlank { "{}" }
                        toolCalls.add(AiToolCall(id = id, name = name, argumentsJson = args))
                        onChunk(AiStreamChunk(type = AiStreamChunkType.TOOL_CALL_END, toolCallId = id, toolName = name))
                        activeToolId = null
                        activeToolName = null
                        activeToolArgs.clear()
                    }
                    payload.contains("\"input_tokens\"") || payload.contains("\"output_tokens\"") -> {
                        extractJsonIntField(payload, "input_tokens")?.let { inputTokens = it }
                        extractJsonIntField(payload, "output_tokens")?.let { outputTokens = it }
                    }
                }
            }
        } else {
            if (config.reasoningConfig.isReasoningActive) {
                val thought = "Claude extended thinking (${config.reasoningConfig.resolvedBudgetTokens} budget tokens)..."
                reasoningBuilder.append(thought)
                onChunk(AiStreamChunk(type = AiStreamChunkType.REASONING_DELTA, reasoningDelta = thought))
            }
            val lastUser = messages.lastOrNull { it.role == AiMessageRole.USER }?.content.orEmpty()
            val reply = "[Anthropic ${config.model}] Response to: $lastUser"
            contentBuilder.append(reply)
            onChunk(AiStreamChunk(type = AiStreamChunkType.CONTENT_DELTA, textDelta = reply))
            inputTokens = (messages.sumOf { it.content.length } / 4).coerceAtLeast(10)
            outputTokens = (reply.length / 4).coerceAtLeast(10)
        }

        val reasoningTok = if (reasoningBuilder.isNotEmpty()) (reasoningBuilder.length / 4).coerceAtLeast(4) else 0
        val usage = AiUsageMetrics(
            promptTokens = inputTokens,
            completionTokens = outputTokens,
            reasoningTokens = reasoningTok,
            estimatedCostUsd = config.calculateCostUsd(inputTokens, outputTokens, reasoningTok)
        )
        onChunk(AiStreamChunk(type = AiStreamChunkType.USAGE_UPDATE, usage = usage))
        onChunk(AiStreamChunk(type = AiStreamChunkType.MESSAGE_COMPLETE, usage = usage))

        return AiChatMessage(
            id = "msg_anthropic_${messages.size + 1}",
            role = AiMessageRole.ASSISTANT,
            content = contentBuilder.toString(),
            reasoningContent = reasoningBuilder.toString(),
            toolCalls = toolCalls,
            usage = usage,
            modelId = config.model
        )
    }

    override suspend fun listModels(config: AiBackendConfig): List<String> = listOf(
        "claude-3-7-sonnet-latest",
        "claude-3-5-sonnet-latest",
        "claude-3-5-haiku-latest"
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
