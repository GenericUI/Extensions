package com.nexustools.genericui.ext.aichat

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.ext.codeeditor.CodeLanguage
import com.nexustools.genericui.ext.codeeditor.IdeCodeEditor

/**
 * Interactive UI card/pill representing a single [AiToolCall] and its optional [AiToolResult].
 *
 * Displays the tool name, live [AiToolCallStatus] badge, execution duration, and an expandable
 * JSON inspector powered by [IdeCodeEditor] for inspecting tool arguments and return output.
 */
class AiChatToolCallCard(
    toolCall: AiToolCall,
    toolResult: AiToolResult? = null,
    initiallyExpanded: Boolean = false
) : VStack() {

    var currentToolCall: AiToolCall = toolCall
        private set

    var currentToolResult: AiToolResult? = toolResult
        private set

    var isDetailsExpanded: Boolean = initiallyExpanded
        private set

    private val headerRow = HStack()
    private val detailsEditor = IdeCodeEditor(
        initialText = formatInspectorPayload(toolCall, toolResult),
        language = CodeLanguage.JSON,
        width = 520,
        height = 160
    ).apply {
        readOnly = true
        isVisible = initiallyExpanded
    }

    init {
        AiChatExtension.ensureStyleSheetRegistered()
        addClass("ai-tool-call-card")

        headerRow.setOnClick {
            toggleDetails()
        }

        addChild(headerRow)
        addChild(detailsEditor)
        applyStatusStyling()
    }

    val summaryLabel: String
        get() {
            val icon = when (currentToolCall.status) {
                AiToolCallStatus.STREAMING_ARGS, AiToolCallStatus.PENDING -> "⋯"
                AiToolCallStatus.RUNNING -> "⟳"
                AiToolCallStatus.COMPLETED -> "✓"
                AiToolCallStatus.FAILED -> "✕"
                AiToolCallStatus.CANCELLED -> "⊘"
            }
            val durationSuffix = currentToolResult?.durationMs?.takeIf { it > 0 }?.let { " (${it}ms)" }.orEmpty()
            return "$icon Tool: ${currentToolCall.name} — ${currentToolCall.status.label}$durationSuffix"
        }

    fun updateToolCall(toolCall: AiToolCall, toolResult: AiToolResult? = currentToolResult) {
        currentToolCall = toolCall
        currentToolResult = toolResult
        detailsEditor.setText(formatInspectorPayload(toolCall, toolResult))
        applyStatusStyling()
    }

    fun toggleDetails() {
        isDetailsExpanded = !isDetailsExpanded
        detailsEditor.isVisible = isDetailsExpanded
    }

    private fun applyStatusStyling() {
        removeClass("ai-tool-status-completed")
        removeClass("ai-tool-status-failed")
        removeClass("ai-tool-status-running")
        removeClass("ai-tool-status-cancelled")
        removeClass("ai-tool-status-pending")
        val statusClass = when (currentToolCall.status) {
            AiToolCallStatus.COMPLETED -> "ai-tool-status-completed"
            AiToolCallStatus.FAILED -> "ai-tool-status-failed"
            AiToolCallStatus.RUNNING, AiToolCallStatus.STREAMING_ARGS -> "ai-tool-status-running"
            AiToolCallStatus.CANCELLED -> "ai-tool-status-cancelled"
            AiToolCallStatus.PENDING -> "ai-tool-status-pending"
        }
        addClass(statusClass)
    }

    companion object {
        fun formatInspectorPayload(call: AiToolCall, result: AiToolResult?): String = buildString {
            appendLine("// Tool Call: ${call.name} (id: ${call.id})")
            appendLine("// Arguments:")
            appendLine(call.argumentsJson.ifBlank { "{}" })
            if (result != null) {
                appendLine()
                appendLine("// Result (${if (result.isError) "ERROR" else "OK"}):")
                append(result.outputContent)
            }
        }
    }
}
