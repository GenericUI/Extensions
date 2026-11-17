package com.nexustools.genericui.ext.aichat

/**
 * Type of incremental streaming event emitted by an [AiChatBackendClient].
 */
enum class AiStreamChunkType {
    CONTENT_DELTA,
    REASONING_DELTA,
    TOOL_CALL_START,
    TOOL_CALL_ARGS_DELTA,
    TOOL_CALL_END,
    USAGE_UPDATE,
    MESSAGE_COMPLETE,
    ERROR
}
