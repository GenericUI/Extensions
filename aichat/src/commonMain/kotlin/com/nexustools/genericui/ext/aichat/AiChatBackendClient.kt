package com.nexustools.genericui.ext.aichat

/**
 * Common interface for multi-backend AI chat providers (`OPENAI`, `ANTHROPIC`, `GEMINI`, `XAI_GROK`,
 * `OLLAMA`, `OPENAI_COMPATIBLE`) supporting live token streaming, reasoning blocks, and tool calls.
 */
interface AiChatBackendClient {
    val backendType: AiBackendType

    /**
     * Streams a chat completion response from the provider backend, invoking [onChunk] for each
     * incremental content delta, reasoning delta, tool-call delta, and usage update, then returns
     * the assembled assistant [AiChatMessage].
     */
    suspend fun streamChat(
        config: AiBackendConfig,
        messages: List<AiChatMessage>,
        tools: List<AiToolDefinition> = emptyList(),
        onChunk: suspend (AiStreamChunk) -> Unit = {}
    ): AiChatMessage

    /**
     * Queries or returns the available model identifiers for this backend.
     */
    suspend fun listModels(config: AiBackendConfig): List<String>
}
