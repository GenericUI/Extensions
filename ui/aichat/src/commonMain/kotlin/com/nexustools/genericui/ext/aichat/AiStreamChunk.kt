package com.nexustools.genericui.ext.aichat

/**
 * Incremental streaming delta emitted during live token generation.
 */
data class AiStreamChunk(
    val type: AiStreamChunkType,
    val textDelta: String = "",
    val reasoningDelta: String = "",
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgsDelta: String = "",
    val usage: AiUsageMetrics? = null,
    val errorMessage: String? = null,
    val finishReason: String? = null
)
