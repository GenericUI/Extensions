package com.nexustools.genericui.ext.aichat

/**
 * Connection, model, generation, reasoning, and token-pricing configuration for an AI provider backend.
 */
data class AiBackendConfig(
    val backendType: AiBackendType = AiBackendType.OPENAI,
    val apiKey: String = "",
    val baseUrl: String = backendType.defaultBaseUrl,
    val model: String = backendType.defaultModel,
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 4096,
    val topP: Double = 1.0,
    val systemPrompt: String = "",
    val reasoningConfig: ReasoningConfig = ReasoningConfig(),
    val customHeaders: Map<String, String> = emptyMap(),
    val inputCostPerMillionTokens: Double = 2.50,
    val outputCostPerMillionTokens: Double = 10.00,
    val reasoningCostPerMillionTokens: Double = 10.00
) {
    fun calculateCostUsd(promptTokens: Int, completionTokens: Int, reasoningTokens: Int = 0): Double {
        val inputCost = (promptTokens.toDouble() / 1_000_000.0) * inputCostPerMillionTokens
        val outputCost = (completionTokens.toDouble() / 1_000_000.0) * outputCostPerMillionTokens
        val thinkingCost = (reasoningTokens.toDouble() / 1_000_000.0) * reasoningCostPerMillionTokens
        return inputCost + outputCost + thinkingCost
    }
}
