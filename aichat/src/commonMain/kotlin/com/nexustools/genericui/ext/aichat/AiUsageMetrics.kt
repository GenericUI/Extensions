package com.nexustools.genericui.ext.aichat

/**
 * Token consumption, latency, and estimated financial cost telemetry for an AI request/response.
 */
data class AiUsageMetrics(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val cachedTokens: Int = 0,
    val totalTokens: Int = promptTokens + completionTokens + reasoningTokens,
    val estimatedCostUsd: Double = 0.0,
    val latencyMs: Long = 0L
) {
    operator fun plus(other: AiUsageMetrics): AiUsageMetrics = AiUsageMetrics(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        reasoningTokens = reasoningTokens + other.reasoningTokens,
        cachedTokens = cachedTokens + other.cachedTokens,
        totalTokens = totalTokens + other.totalTokens,
        estimatedCostUsd = estimatedCostUsd + other.estimatedCostUsd,
        latencyMs = latencyMs + other.latencyMs
    )
}
