package com.nexustools.genericui.ext.aichat

/**
 * Configuration for model reasoning / chain-of-thought thinking tokens.
 */
data class ReasoningConfig(
    val effort: ReasoningEffort = ReasoningEffort.NONE,
    val budgetTokens: Int? = null,
    val includeReasoningInResponse: Boolean = true,
    val summaryMode: String = "auto"
) {
    val resolvedBudgetTokens: Int
        get() = budgetTokens ?: effort.defaultBudgetTokens

    val isReasoningActive: Boolean
        get() = effort.isEnabled && resolvedBudgetTokens > 0
}
