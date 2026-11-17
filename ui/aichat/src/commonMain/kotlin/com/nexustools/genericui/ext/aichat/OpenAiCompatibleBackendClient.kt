package com.nexustools.genericui.ext.aichat

/**
 * Streaming client for OpenAI, xAI Grok, Ollama, and any OpenAI-compatible (`/v1/chat/completions`) endpoint.
 *
 * Supports:
 * - Standard chat messages, multimodal image URLs/base64, and tool results (`role: "tool"`).
 * - Optional reasoning effort (`reasoning_effort: "low" | "medium" | "high"`) and reasoning delta parsing
 *   (`delta.reasoning_content` / `delta.reasoning`).
 * - Live SSE (`data: {...}`) streaming of text deltas, reasoning deltas, incremental tool call arguments, and usage metrics.
 * - Pluggable [sseTransport] so custom HTTP engines or offline test harnesses can stream SSE lines across all KMP targets.
 */
class OpenAiCompatibleBackendClient(
    override val backendType: AiBackendType = AiBackendType.OPENAI,
    private val sseTransport: (suspend (
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        onLine: suspend (String) -> Unit
    ) -> Unit)? = null
) : AiChatBackendClient {

    fun buildRequestJson(
        config: AiBackendConfig,
        messages: List<AiChatMessage>,
        tools: List<AiToolDefinition>
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"model\":\"").append(escapeJson(config.model)).append("\",")
        sb.append("\"stream\":true,")
        sb.append("\"stream_options\":{\"include_usage\":true},")
        sb.append("\"temperature\":").append(config.temperature).append(",")
        sb.append("\"max_tokens\":").append(config.maxOutputTokens).append(",")
        sb.append("\"top_p\":").append(config.topP)

        if (config.reasoningConfig.isReasoningActive) {
            sb.append(",\"reasoning_effort\":\"").append(config.reasoningConfig.effort.wireValue).append("\"")
        }

        sb.append(",\"messages\":[")
        var firstMsg = true
        if (config.systemPrompt.isNotBlank() && messages.none { it.role == AiMessageRole.SYSTEM }) {
            sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(config.systemPrompt)).append("\"}")
            firstMsg = false
        }
        for (msg in messages) {
            if (msg.role == AiMessageRole.TOOL && msg.toolResults.isNotEmpty()) {
                for (tr in msg.toolResults) {
                    if (!firstMsg) sb.append(",")
                    firstMsg = false
                    sb.append("{\"role\":\"tool\",\"tool_call_id\":\"").append(escapeJson(tr.toolCallId))
                        .append("\",\"content\":\"").append(escapeJson(tr.outputContent)).append("\"}")
                }
            } else {
                if (!firstMsg) sb.append(",")
                firstMsg = false
                sb.append("{\"role\":\"").append(msg.role.wireName).append("\"")
                sb.append(",\"content\":\"").append(escapeJson(msg.content)).append("\"")
                if (msg.toolCalls.isNotEmpty()) {
                    sb.append(",\"tool_calls\":[")
                    msg.toolCalls.forEachIndexed { idx, tc ->
                        if (idx > 0) sb.append(",")
                        sb.append("{\"id\":\"").append(escapeJson(tc.id))
                            .append("\",\"type\":\"function\",\"function\":{\"name\":\"")
                            .append(escapeJson(tc.name)).append("\",\"arguments\":\"")
                            .append(escapeJson(tc.argumentsJson)).append("\"}}")
                    }
                    sb.append("]")
                }
                sb.append("}")
            }
        }
        sb.append("]")

        if (tools.isNotEmpty()) {
            sb.append(",\"tools\":[")
            tools.forEachIndexed { idx, tool ->
                if (idx > 0) sb.append(",")
                sb.append("{\"type\":\"function\",\"function\":{")
                sb.append("\"name\":\"").append(escapeJson(tool.name)).append("\",")
                sb.append("\"description\":\"").append(escapeJson(tool.description)).append("\",")
                sb.append("\"parameters\":").append(tool.parametersJsonSchema.ifBlank { "{\"type\":\"object\"}" })
                if (tool.strict) {
                    sb.append(",\"strict\":true")
                }
                sb.append("}}")
            }
            sb.append("]")
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
            headers["Authorization"] = "Bearer ${config.apiKey}"
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
        val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
        val headers = buildHeaders(config)
        val bodyJson = buildRequestJson(config, messages, tools)

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolCallIds = mutableMapOf<Int, String>()
        val toolCallNames = mutableMapOf<Int, String>()
        val toolCallArgs = mutableMapOf<Int, StringBuilder>()
        var usageMetrics: AiUsageMetrics? = null

        val transport = sseTransport
        if (transport != null) {
            transport(url, headers, bodyJson) { rawLine ->
                val line = rawLine.trim()
                if (!line.startsWith("data:")) return@transport
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]" || payload.isEmpty()) return@transport

                val rDelta = extractJsonStringField(payload, "reasoning_content")
                    ?: extractJsonStringField(payload, "reasoning")
                if (!rDelta.isNullOrEmpty()) {
                    reasoningBuilder.append(rDelta)
                    onChunk(AiStreamChunk(type = AiStreamChunkType.REASONING_DELTA, reasoningDelta = rDelta))
                }

                val cDelta = extractDeltaContent(payload)
                if (!cDelta.isNullOrEmpty()) {
                    contentBuilder.append(cDelta)
                    onChunk(AiStreamChunk(type = AiStreamChunkType.CONTENT_DELTA, textDelta = cDelta))
                }

                if (payload.contains("\"tool_calls\"")) {
                    val idx = extractJsonIntField(payload, "index") ?: 0
                    val tcId = extractToolCallId(payload)
                    val fnName = extractFunctionName(payload)
                    val fnArgs = extractFunctionArguments(payload)
                    if (!tcId.isNullOrEmpty()) {
                        toolCallIds[idx] = tcId
                    }
                    if (!fnName.isNullOrEmpty()) {
                        toolCallNames[idx] = fnName
                        onChunk(
                            AiStreamChunk(
                                type = AiStreamChunkType.TOOL_CALL_START,
                                toolCallId = toolCallIds[idx] ?: "call_$idx",
                                toolName = fnName
                            )
                        )
                    }
                    if (!fnArgs.isNullOrEmpty()) {
                        val sb = toolCallArgs.getOrPut(idx) { StringBuilder() }
                        sb.append(fnArgs)
                        onChunk(
                            AiStreamChunk(
                                type = AiStreamChunkType.TOOL_CALL_ARGS_DELTA,
                                toolCallId = toolCallIds[idx] ?: "call_$idx",
                                toolName = toolCallNames[idx],
                                toolArgsDelta = fnArgs
                            )
                        )
                    }
                }

                if (payload.contains("\"usage\"")) {
                    val promptTok = extractJsonIntField(payload, "prompt_tokens") ?: 0
                    val compTok = extractJsonIntField(payload, "completion_tokens") ?: 0
                    val reasonTok = extractJsonIntField(payload, "reasoning_tokens") ?: 0
                    if (promptTok > 0 || compTok > 0) {
                        val cost = config.calculateCostUsd(promptTok, compTok, reasonTok)
                        usageMetrics = AiUsageMetrics(
                            promptTokens = promptTok,
                            completionTokens = compTok,
                            reasoningTokens = reasonTok,
                            estimatedCostUsd = cost
                        )
                        onChunk(AiStreamChunk(type = AiStreamChunkType.USAGE_UPDATE, usage = usageMetrics))
                    }
                }
            }
        } else {
            // Local fallback / built-in studio tool-calling agent when no external HTTP transport is injected
            val lastMessage = messages.lastOrNull()
            val lastUser = messages.lastOrNull { it.role == AiMessageRole.USER }?.content.orEmpty()
            val lowerPrompt = lastUser.lowercase()
            val registeredNames = tools.map { it.name }.toSet()

            if (lastMessage?.role == AiMessageRole.TOOL && lastMessage.toolResults.isNotEmpty()) {
                // Summarize completed tool calls on the follow-up turn
                val summaryLines = lastMessage.toolResults.joinToString("\n") { tr ->
                    "- **`${tr.toolName}`**: ${tr.outputContent}"
                }
                val reply = buildString {
                    appendLine("Executed **${lastMessage.toolResults.size}** tool operation(s) via **${backendType.displayName}** (`${config.model}`):")
                    appendLine(summaryLines)
                }.trim()
                contentBuilder.append(reply)
                onChunk(AiStreamChunk(type = AiStreamChunkType.CONTENT_DELTA, textDelta = reply))
            } else {
                if (config.reasoningConfig.isReasoningActive) {
                    val thought = buildString {
                        append("Analyzing studio request with ${config.model} ")
                        append("(reasoning effort: ${config.reasoningConfig.effort.wireValue}, ")
                        append("${tools.size} tools available)...")
                    }
                    reasoningBuilder.append(thought)
                    onChunk(AiStreamChunk(type = AiStreamChunkType.REASONING_DELTA, reasoningDelta = thought))
                }

                // Match tool calls if tools are registered
                val matchedCalls = mutableListOf<Pair<String, String>>()
                if (tools.isNotEmpty()) {
                    when {
                        (lowerPrompt.contains("inspect") || lowerPrompt.contains("scene") || lowerPrompt.contains("layer")) &&
                            "inspect_scene" in registeredNames -> {
                            matchedCalls.add("inspect_scene" to "{\"includeLayers\":true,\"includeFilters\":true,\"includeTimeline\":true}")
                        }
                        (lowerPrompt.contains("star") || lowerPrompt.contains("rect") || lowerPrompt.contains("circle") || lowerPrompt.contains("vector") || lowerPrompt.contains("draw")) &&
                            "draw_vector_shape" in registeredNames -> {
                            val shapeType = when {
                                lowerPrompt.contains("star") -> "star"
                                lowerPrompt.contains("circle") -> "circle"
                                lowerPrompt.contains("ellipse") -> "ellipse"
                                lowerPrompt.contains("polygon") -> "polygon"
                                else -> "rect"
                            }
                            val fill = if (lowerPrompt.contains("cyan")) "#38bdf8" else "#6366f1"
                            val stroke = if (lowerPrompt.contains("gold") || lowerPrompt.contains("star")) "#facc15" else "#e2e8f0"
                            matchedCalls.add("draw_vector_shape" to "{\"shape\":\"$shapeType\",\"fill\":\"$fill\",\"stroke\":\"$stroke\",\"strokeWidth\":4}")
                        }
                        (lowerPrompt.contains("gradient") || lowerPrompt.contains("paint") || lowerPrompt.contains("backdrop")) &&
                            "apply_raster_gradient" in registeredNames -> {
                            matchedCalls.add("apply_raster_gradient" to "{\"startColor\":\"#1e1b4b\",\"endColor\":\"#7c3aed\",\"type\":\"linear\"}")
                        }
                        (lowerPrompt.contains("blur") || lowerPrompt.contains("shadow") || lowerPrompt.contains("glow") || lowerPrompt.contains("filter")) &&
                            "apply_gimp_filter" in registeredNames -> {
                            val filterId = when {
                                lowerPrompt.contains("shadow") -> "drop-shadow"
                                lowerPrompt.contains("glow") -> "neon-glow"
                                else -> "gaussian-blur"
                            }
                            matchedCalls.add("apply_gimp_filter" to "{\"filterId\":\"$filterId\",\"dynamic\":true,\"radius\":8.0}")
                        }
                        (lowerPrompt.contains("shared") || lowerPrompt.contains("badge") || lowerPrompt.contains("reusable")) &&
                            "create_shared_object" in registeredNames -> {
                            matchedCalls.add("create_shared_object" to "{\"name\":\"Shared Studio Badge\",\"width\":256,\"height\":256,\"insertInstances\":2}")
                        }
                        (lowerPrompt.contains("animate") || lowerPrompt.contains("pulse") || lowerPrompt.contains("keyframe") || lowerPrompt.contains("timeline")) &&
                            "set_keyframe" in registeredNames -> {
                            matchedCalls.add("set_keyframe" to "{\"frame\":1,\"scaleX\":1.0,\"scaleY\":1.0,\"easing\":\"ease-in-out\"}")
                            matchedCalls.add("set_keyframe" to "{\"frame\":12,\"scaleX\":1.25,\"scaleY\":1.25,\"easing\":\"cubic-bezier(0.42,0,0.58,1)\"}")
                        }
                    }
                }

                if (matchedCalls.isNotEmpty()) {
                    matchedCalls.forEachIndexed { idx, (toolName, argsJson) ->
                        val callId = "call_${idx + 1}"
                        toolCallIds[idx] = callId
                        toolCallNames[idx] = toolName
                        toolCallArgs[idx] = StringBuilder(argsJson)
                        onChunk(AiStreamChunk(type = AiStreamChunkType.TOOL_CALL_START, toolCallId = callId, toolName = toolName))
                        onChunk(
                            AiStreamChunk(
                                type = AiStreamChunkType.TOOL_CALL_ARGS_DELTA,
                                toolCallId = callId,
                                toolName = toolName,
                                toolArgsDelta = argsJson
                            )
                        )
                    }
                } else {
                    val reply = "[${backendType.displayName} - ${config.model}] Processed: $lastUser"
                    contentBuilder.append(reply)
                    onChunk(AiStreamChunk(type = AiStreamChunkType.CONTENT_DELTA, textDelta = reply))
                }
            }

            val pTok = (messages.sumOf { it.content.length } / 4).coerceAtLeast(8)
            val cTok = (contentBuilder.length / 4).coerceAtLeast(8)
            val rTok = if (reasoningBuilder.isNotEmpty()) (reasoningBuilder.length / 4).coerceAtLeast(4) else 0
            usageMetrics = AiUsageMetrics(
                promptTokens = pTok,
                completionTokens = cTok,
                reasoningTokens = rTok,
                estimatedCostUsd = config.calculateCostUsd(pTok, cTok, rTok)
            )
            onChunk(AiStreamChunk(type = AiStreamChunkType.USAGE_UPDATE, usage = usageMetrics))
        }

        val assembledToolCalls = toolCallNames.entries.sortedBy { it.key }.map { (idx, name) ->
            val id = toolCallIds[idx] ?: "call_$idx"
            val args = toolCallArgs[idx]?.toString().orEmpty().ifBlank { "{}" }
            onChunk(AiStreamChunk(type = AiStreamChunkType.TOOL_CALL_END, toolCallId = id, toolName = name))
            AiToolCall(
                id = id,
                name = name,
                argumentsJson = args,
                status = AiToolCallStatus.PENDING
            )
        }

        onChunk(AiStreamChunk(type = AiStreamChunkType.MESSAGE_COMPLETE, usage = usageMetrics))

        return AiChatMessage(
            id = "msg_${backendType.name.lowercase()}_${messages.size + 1}",
            role = AiMessageRole.ASSISTANT,
            content = contentBuilder.toString(),
            reasoningContent = reasoningBuilder.toString(),
            toolCalls = assembledToolCalls,
            usage = usageMetrics,
            modelId = config.model
        )
    }

    override suspend fun listModels(config: AiBackendConfig): List<String> = when (backendType) {
        AiBackendType.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "o3-mini", "o1")
        AiBackendType.XAI_GROK -> listOf("grok-2-latest", "grok-2-vision-latest", "grok-3-mini")
        AiBackendType.OLLAMA -> listOf("qwen2.5-coder:32b", "llama3.3:70b", "deepseek-r1:32b")
        else -> listOf(config.model)
    }

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

    private fun extractDeltaContent(json: String): String? {
        val deltaIdx = json.indexOf("\"delta\"")
        if (deltaIdx < 0) return null
        return extractJsonStringField(json.substring(deltaIdx), "content")
    }

    private fun extractToolCallId(json: String): String? {
        val tcIdx = json.indexOf("\"tool_calls\"")
        if (tcIdx < 0) return null
        return extractJsonStringField(json.substring(tcIdx), "id")
    }

    private fun extractFunctionName(json: String): String? {
        val fnIdx = json.indexOf("\"function\"")
        if (fnIdx < 0) return null
        return extractJsonStringField(json.substring(fnIdx), "name")
    }

    private fun extractFunctionArguments(json: String): String? {
        val fnIdx = json.indexOf("\"function\"")
        if (fnIdx < 0) return null
        return extractJsonStringField(json.substring(fnIdx), "arguments")
    }

    private fun extractJsonStringField(json: String, field: String): String? {
        val key = "\"$field\""
        var idx = json.indexOf(key)
        while (idx >= 0) {
            val colonIdx = json.indexOf(':', idx + key.length)
            if (colonIdx < 0) return null
            var pos = colonIdx + 1
            while (pos < json.length && json[pos].isWhitespace()) pos++
            if (pos < json.length && json[pos] == '"') {
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
            }
            idx = json.indexOf(key, colonIdx + 1)
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
