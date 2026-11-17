package com.nexustools.genericui.ext.aichat

/**
 * Execution lifecycle state of an [AiToolCall].
 */
enum class AiToolCallStatus(val label: String) {
    STREAMING_ARGS("Streaming arguments..."),
    PENDING("Queued"),
    RUNNING("Running..."),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}
