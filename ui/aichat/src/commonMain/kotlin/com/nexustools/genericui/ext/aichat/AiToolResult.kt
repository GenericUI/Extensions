package com.nexustools.genericui.ext.aichat

/**
 * Result produced by executing an [AiToolCall] via [AiToolExecutor].
 */
data class AiToolResult(
    val toolCallId: String,
    val toolName: String,
    val outputContent: String,
    val isError: Boolean = false,
    val durationMs: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)
