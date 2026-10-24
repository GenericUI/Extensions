package com.nexustools.genericui.ext.aichat

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
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
        addClass("ai-tool-call-card")
        setBorderRadius(BorderRadiusStyle.ROUNDED)
        setPaddingAll(SpacingScale.S2)
        setMarginAll(SpacingScale.S1)

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
        val border = when (currentToolCall.status) {
            AiToolCallStatus.COMPLETED -> BootstrapType.SUCCESS
            AiToolCallStatus.FAILED -> BootstrapType.DANGER
            AiToolCallStatus.RUNNING, AiToolCallStatus.STREAMING_ARGS -> BootstrapType.INFO
            AiToolCallStatus.CANCELLED -> BootstrapType.WARNING
            AiToolCallStatus.PENDING -> BootstrapType.SECONDARY
        }
        setBorderType(border)
        headerRow.tooltipText = summaryLabel
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
