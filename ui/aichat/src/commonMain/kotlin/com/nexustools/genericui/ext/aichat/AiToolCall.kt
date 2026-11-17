package com.nexustools.genericui.ext.aichat

/**
 * Represents a structured tool/function call requested by the assistant.
 */
data class AiToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
    val status: AiToolCallStatus = AiToolCallStatus.PENDING,
    val startedAtEpochMs: Long = 0L,
    val completedAtEpochMs: Long = 0L
) {
    val durationMs: Long
        get() = if (completedAtEpochMs > startedAtEpochMs && startedAtEpochMs > 0L) {
            completedAtEpochMs - startedAtEpochMs
        } else {
            0L
        }
}
