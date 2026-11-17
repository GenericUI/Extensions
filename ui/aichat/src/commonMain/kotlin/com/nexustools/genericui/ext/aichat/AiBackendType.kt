package com.nexustools.genericui.ext.aichat

/**
 * Supported AI model provider backends for [AiChatSession] and [AiChatContainer].
 */
enum class AiBackendType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val supportsReasoningEffort: Boolean = true,
    val supportsToolCalling: Boolean = true
) {
    LM_STUDIO(
        displayName = "LM Studio",
        defaultBaseUrl = "http://localhost:1234/v1",
        defaultModel = "qwen/qwen3-coder-30b"
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o"
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-7-sonnet-latest"
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-pro"
    ),
    GEMINI_GOOGLE_ONE(
        displayName = "Google Gemini (Google One OAuth)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-pro"
    ),
    XAI_GROK(
        displayName = "xAI Grok",
        defaultBaseUrl = "https://api.x.ai/v1",
        defaultModel = "grok-2-latest"
    ),
    OLLAMA(
        displayName = "Ollama (Local)",
        defaultBaseUrl = "http://localhost:11434/v1",
        defaultModel = "qwen2.5-coder:32b"
    ),
    CUSTOM_OPENAI(
        displayName = "Custom OpenAI",
        defaultBaseUrl = "http://localhost:1234/v1",
        defaultModel = "local-model"
    ),
    CUSTOM_CLAUDE(
        displayName = "Custom Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-7-sonnet-latest"
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI Compatible",
        defaultBaseUrl = "http://localhost:1234/v1",
        defaultModel = "local-model"
    )
}
