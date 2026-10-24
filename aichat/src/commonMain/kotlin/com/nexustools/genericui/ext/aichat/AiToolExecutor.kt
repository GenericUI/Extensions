package com.nexustools.genericui.ext.aichat

/**
 * Registry and dispatcher for AI tool calls requested during an [AiChatSession].
 *
 * Supports registering tools with their [AiToolDefinition] schema and suspending handler lambdas,
 * optional user-confirmation hooks for tools with [AiToolDefinition.requiresConfirmation],
 * and structured error recovery so tool failures are returned cleanly to the model.
 */
class AiToolExecutor {

    private val definitions = LinkedHashMap<String, AiToolDefinition>()
    private val handlers = LinkedHashMap<String, suspend (String) -> AiToolResult>()
    private var confirmationGate: (suspend (AiToolCall, AiToolDefinition) -> Boolean)? = null

    /**
     * Registers a tool definition and its suspending execution callback.
     */
    fun registerTool(
        definition: AiToolDefinition,
        handler: suspend (argumentsJson: String) -> AiToolResult
    ) {
        definitions[definition.name] = definition
        handlers[definition.name] = handler
    }

    /**
     * Convenience overload that registers a tool whose handler returns a plain output string.
     */
    fun registerSimpleTool(
        name: String,
        description: String,
        parametersJsonSchema: String = """{"type":"object","properties":{}}""",
        requiresConfirmation: Boolean = false,
        handler: suspend (argumentsJson: String) -> String
    ) {
        val def = AiToolDefinition(
            name = name,
            description = description,
            parametersJsonSchema = parametersJsonSchema,
            requiresConfirmation = requiresConfirmation
        )
        registerTool(def) { argsJson ->
            val output = handler(argsJson)
            AiToolResult(
                toolCallId = "",
                toolName = name,
                outputContent = output,
                isError = false
            )
        }
    }

    fun unregisterTool(name: String) {
        definitions.remove(name)
        handlers.remove(name)
    }

    fun getToolDefinitions(): List<AiToolDefinition> = definitions.values.toList()

    fun hasTool(name: String): Boolean = handlers.containsKey(name)

    fun setConfirmationGate(gate: (suspend (AiToolCall, AiToolDefinition) -> Boolean)?) {
        confirmationGate = gate
    }

    /**
     * Executes [toolCall] against the registered tool handler and returns its [AiToolResult].
     */
    suspend fun execute(
        toolCall: AiToolCall,
        onStatusChanged: (AiToolCallStatus) -> Unit = {}
    ): AiToolResult {
        val definition = definitions[toolCall.name]
        val handler = handlers[toolCall.name]
        if (definition == null || handler == null) {
            onStatusChanged(AiToolCallStatus.FAILED)
            return AiToolResult(
                toolCallId = toolCall.id,
                toolName = toolCall.name,
                outputContent = """{"error":"Tool '${toolCall.name}' is not registered."}""",
                isError = true
            )
        }

        val gate = confirmationGate
        if (definition.requiresConfirmation && gate != null) {
            val approved = gate(toolCall, definition)
            if (!approved) {
                onStatusChanged(AiToolCallStatus.CANCELLED)
                return AiToolResult(
                    toolCallId = toolCall.id,
                    toolName = toolCall.name,
                    outputContent = """{"error":"Tool execution cancelled by user."}""",
                    isError = true
                )
            }
        }

        onStatusChanged(AiToolCallStatus.RUNNING)
        return try {
            val rawResult = handler(toolCall.argumentsJson)
            val finalStatus = if (rawResult.isError) AiToolCallStatus.FAILED else AiToolCallStatus.COMPLETED
            onStatusChanged(finalStatus)
            rawResult.copy(
                toolCallId = toolCall.id,
                toolName = toolCall.name
            )
        } catch (t: Throwable) {
            onStatusChanged(AiToolCallStatus.FAILED)
            AiToolResult(
                toolCallId = toolCall.id,
                toolName = toolCall.name,
                outputContent = """{"error":"${t.message ?: t::class.simpleName ?: "Tool execution failed"}"}""",
                isError = true
            )
        }
    }
}
