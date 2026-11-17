package com.nexustools.genericui.ext.aichat

/**
 * Configurable reasoning / thinking effort level across OpenAI o-series/GPT-5,
 * Anthropic Claude Extended Thinking, Google Gemini Thinking, and xAI Grok reasoning models.
 */
enum class ReasoningEffort(
    val wireValue: String,
    val displayName: String,
    val defaultBudgetTokens: Int
) {
    NONE("none", "Off", 0),
    LOW("low", "Low", 2048),
    MEDIUM("medium", "Medium", 8192),
    HIGH("high", "High", 16384),
    MAX("max", "Max", 32768);

    val isEnabled: Boolean
        get() = this != NONE

    companion object {
        fun fromWireValue(value: String?): ReasoningEffort {
            if (value.isNullOrBlank()) return NONE
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.wireValue == normalized } ?: NONE
        }
    }
}
