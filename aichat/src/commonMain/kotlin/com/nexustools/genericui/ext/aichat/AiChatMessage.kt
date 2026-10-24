package com.nexustools.genericui.ext.aichat

/**
 * Immutable representation of a chat message in an [AiChatSession], supporting text/markdown content,
 * reasoning/chain-of-thought blocks, tool calls, tool execution results, attachments, and usage metrics.
 */
data class AiChatMessage(
    val id: String,
    val role: AiMessageRole,
    val content: String = "",
    val reasoningContent: String = "",
    val toolCalls: List<AiToolCall> = emptyList(),
    val toolResults: List<AiToolResult> = emptyList(),
    val attachments: List<AiAttachment> = emptyList(),
    val usage: AiUsageMetrics? = null,
    val timestampEpochMs: Long = 0L,
    val isStreaming: Boolean = false,
    val modelId: String? = null
) {
    val hasReasoning: Boolean
        get() = reasoningContent.isNotBlank()

    val hasToolCalls: Boolean
        get() = toolCalls.isNotEmpty()

    val hasToolResults: Boolean
        get() = toolResults.isNotEmpty()
}
