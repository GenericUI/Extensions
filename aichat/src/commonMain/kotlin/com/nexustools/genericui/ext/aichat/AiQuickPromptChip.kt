package com.nexustools.genericui.ext.aichat

/**
 * Quick-action starter prompt chip displayed in [AiChatContainer] for one-click prompt execution.
 */
data class AiQuickPromptChip(
    val label: String,
    val prompt: String,
    val tooltip: String = prompt
)
