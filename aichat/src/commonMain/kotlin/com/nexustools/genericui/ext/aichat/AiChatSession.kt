package com.nexustools.genericui.ext.aichat

/**
 * Stateful multi-backend AI chat session coordinator with live streaming and an automated
 * multi-turn tool-call execution loop.
 *
 * When the model emits one or more [AiToolCall]s in a turn, [sendUserMessage] automatically
 * invokes [toolExecutor], appends the resulting `TOOL` role message to the conversation history,
 * and streams the next assistant turn until the model completes without further tool calls or
 * [maxToolRoundtrips] is reached.
 */
class AiChatSession(
    var config: AiBackendConfig = AiBackendConfig(),
    val toolExecutor: AiToolExecutor = AiToolExecutor(),
    var maxToolRoundtrips: Int = 8
) {
    private val _messages = mutableListOf<AiChatMessage>()
    val messages: List<AiChatMessage>
        get() = _messages

    var cumulativeUsage: AiUsageMetrics = AiUsageMetrics()
        private set

    var isStreaming: Boolean = false
        private set

    private val customClients = mutableMapOf<AiBackendType, AiChatBackendClient>()
    private val messageListeners = mutableListOf<(List<AiChatMessage>) -> Unit>()
    private val chunkListeners = mutableListOf<(AiStreamChunk) -> Unit>()
    private val toolStatusListeners = mutableListOf<(AiToolCall, AiToolResult?) -> Unit>()

    fun registerBackendClient(client: AiChatBackendClient) {
        customClients[client.backendType] = client
    }

    fun resolveClient(backendType: AiBackendType = config.backendType): AiChatBackendClient {
        customClients[backendType]?.let { return it }
        return when (backendType) {
            AiBackendType.LM_STUDIO -> OpenAiCompatibleBackendClient(AiBackendType.LM_STUDIO)
            AiBackendType.OPENAI -> OpenAiCompatibleBackendClient(AiBackendType.OPENAI)
            AiBackendType.XAI_GROK -> OpenAiCompatibleBackendClient(AiBackendType.XAI_GROK)
            AiBackendType.OLLAMA -> OpenAiCompatibleBackendClient(AiBackendType.OLLAMA)
            AiBackendType.CUSTOM_OPENAI -> OpenAiCompatibleBackendClient(AiBackendType.CUSTOM_OPENAI)
            AiBackendType.OPENAI_COMPATIBLE -> OpenAiCompatibleBackendClient(AiBackendType.OPENAI_COMPATIBLE)
            AiBackendType.ANTHROPIC, AiBackendType.CUSTOM_CLAUDE -> AnthropicBackendClient()
            AiBackendType.GEMINI, AiBackendType.GEMINI_GOOGLE_ONE -> GeminiBackendClient()
        }
    }

    fun cancelStreaming() {
        isStreaming = false
        notifyMessagesChanged()
    }

    fun setReasoningEffort(effort: ReasoningEffort, budgetTokens: Int? = null) {
        config = config.copy(
            reasoningConfig = config.reasoningConfig.copy(
                effort = effort,
                budgetTokens = budgetTokens
            )
        )
    }

    fun switchBackend(
        backendType: AiBackendType,
        model: String = backendType.defaultModel,
        apiKey: String = config.apiKey,
        baseUrl: String = backendType.defaultBaseUrl
    ) {
        config = config.copy(
            backendType = backendType,
            model = model,
            apiKey = apiKey,
            baseUrl = baseUrl
        )
    }

    fun appendMessage(message: AiChatMessage) {
        _messages.add(message)
        notifyMessagesChanged()
    }

    fun clearHistory() {
        _messages.clear()
        cumulativeUsage = AiUsageMetrics()
        notifyMessagesChanged()
    }

    fun addOnMessagesChangedListener(listener: (List<AiChatMessage>) -> Unit) {
        messageListeners.add(listener)
    }

    fun addOnStreamChunkListener(listener: (AiStreamChunk) -> Unit) {
        chunkListeners.add(listener)
    }

    fun addOnToolStatusListener(listener: (AiToolCall, AiToolResult?) -> Unit) {
        toolStatusListeners.add(listener)
    }

    /**
     * Sends a user prompt (with optional [attachments]), streams the assistant response,
     * and automatically executes any requested tool calls in a multi-turn loop.
     */
    suspend fun sendUserMessage(
        prompt: String,
        attachments: List<AiAttachment> = emptyList(),
        onChunk: suspend (AiStreamChunk) -> Unit = {}
    ): AiChatMessage {
        val userMsg = AiChatMessage(
            id = "user_${_messages.size + 1}",
            role = AiMessageRole.USER,
            content = prompt,
            attachments = attachments
        )
        _messages.add(userMsg)
        notifyMessagesChanged()

        isStreaming = true
        var lastAssistantMessage: AiChatMessage
        var roundtrip = 0

        try {
            while (true) {
                val client = resolveClient(config.backendType)
                val tools = if (config.backendType.supportsToolCalling) {
                    toolExecutor.getToolDefinitions()
                } else {
                    emptyList()
                }

                val streamingMsgIndex = _messages.size
                val placeholder = AiChatMessage(
                    id = "assistant_stream_${streamingMsgIndex + 1}",
                    role = AiMessageRole.ASSISTANT,
                    content = "",
                    isStreaming = true,
                    modelId = config.model
                )
                _messages.add(placeholder)
                notifyMessagesChanged()

                val liveContent = StringBuilder()
                val liveReasoning = StringBuilder()

                val completedAssistant = client.streamChat(
                    config = config,
                    messages = _messages.subList(0, streamingMsgIndex),
                    tools = tools
                ) { chunk ->
                    when (chunk.type) {
                        AiStreamChunkType.CONTENT_DELTA -> liveContent.append(chunk.textDelta)
                        AiStreamChunkType.REASONING_DELTA -> liveReasoning.append(chunk.reasoningDelta)
                        else -> {}
                    }
                    _messages[streamingMsgIndex] = placeholder.copy(
                        content = liveContent.toString(),
                        reasoningContent = liveReasoning.toString(),
                        isStreaming = chunk.type != AiStreamChunkType.MESSAGE_COMPLETE
                    )
                    for (listener in chunkListeners) {
                        listener(chunk)
                    }
                    onChunk(chunk)
                    notifyMessagesChanged()
                }

                _messages[streamingMsgIndex] = completedAssistant.copy(isStreaming = false)
                completedAssistant.usage?.let { cumulativeUsage += it }
                notifyMessagesChanged()
                lastAssistantMessage = _messages[streamingMsgIndex]

                if (lastAssistantMessage.toolCalls.isEmpty() || roundtrip >= maxToolRoundtrips) {
                    break
                }

                roundtrip++
                val executedCalls = mutableListOf<AiToolCall>()
                val results = mutableListOf<AiToolResult>()

                for (toolCall in lastAssistantMessage.toolCalls) {
                    val runningCall = toolCall.copy(status = AiToolCallStatus.RUNNING)
                    for (listener in toolStatusListeners) {
                        listener(runningCall, null)
                    }
                    var finalStatus = AiToolCallStatus.COMPLETED
                    val result = toolExecutor.execute(toolCall) { status ->
                        finalStatus = status
                    }
                    val updatedCall = toolCall.copy(status = finalStatus)
                    executedCalls.add(updatedCall)
                    results.add(result)
                    for (listener in toolStatusListeners) {
                        listener(updatedCall, result)
                    }
                }

                _messages[streamingMsgIndex] = lastAssistantMessage.copy(toolCalls = executedCalls)
                val toolMessage = AiChatMessage(
                    id = "tool_${_messages.size + 1}",
                    role = AiMessageRole.TOOL,
                    content = results.joinToString("\n") { "${it.toolName}: ${it.outputContent}" },
                    toolResults = results
                )
                _messages.add(toolMessage)
                notifyMessagesChanged()
            }
        } finally {
            isStreaming = false
        }

        return lastAssistantMessage
    }

    private fun notifyMessagesChanged() {
        val snapshot = _messages.toList()
        for (listener in messageListeners) {
            listener(snapshot)
        }
    }
}
