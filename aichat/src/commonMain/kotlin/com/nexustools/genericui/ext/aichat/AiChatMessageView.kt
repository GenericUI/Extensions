package com.nexustools.genericui.ext.aichat

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.ext.codeeditor.CodeLanguage
import com.nexustools.genericui.ext.codeeditor.IdeCodeEditor

/**
 * UI component that renders a single [AiChatMessage] bubble, including:
 * - Role & model header
 * - Collapsible [AiChatReasoningAccordion] for chain-of-thought reasoning blocks
 * - Markdown prose & syntax-highlighted fenced code blocks via [IdeCodeEditor]
 * - Interactive [AiChatToolCallCard] items for tool invocations and results
 * - Token usage & cost telemetry footer
 */
class AiChatMessageView(
    message: AiChatMessage
) : VStack() {

    var message: AiChatMessage = message
        private set

    private val headerBar = HStack()
    val reasoningAccordion: AiChatReasoningAccordion = AiChatReasoningAccordion(
        reasoningText = message.reasoningContent,
        isStreaming = message.isStreaming
    )
    private val contentBlocksContainer = VStack()
    private val toolCardsContainer = VStack()
    private val footerBar = HStack()

    private val toolCallCards = mutableListOf<AiChatToolCallCard>()

    init {
        addClass("ai-chat-message-view")
        setBorderRadius(BorderRadiusStyle.ROUNDED_3)
        setPaddingAll(SpacingScale.S3)
        setMarginAll(SpacingScale.S2)

        addChild(headerBar)
        addChild(reasoningAccordion)
        addChild(contentBlocksContainer)
        addChild(toolCardsContainer)
        addChild(footerBar)

        bindMessage(message)
    }

    fun getToolCallCards(): List<AiChatToolCallCard> = toolCallCards.toList()

    fun bindMessage(updated: AiChatMessage) {
        this.message = updated

        val roleBorder = when (updated.role) {
            AiMessageRole.USER -> BootstrapType.PRIMARY
            AiMessageRole.ASSISTANT -> BootstrapType.INFO
            AiMessageRole.TOOL -> BootstrapType.SUCCESS
            AiMessageRole.SYSTEM -> BootstrapType.SECONDARY
        }
        setBorderType(roleBorder)

        val modelSuffix = updated.modelId?.let { " ($it)" }.orEmpty()
        headerBar.tooltipText = "${updated.role.name}$modelSuffix"

        if (updated.hasReasoning) {
            reasoningAccordion.isVisible = true
            reasoningAccordion.updateReasoning(updated.reasoningContent, updated.isStreaming)
        } else {
            reasoningAccordion.isVisible = false
        }

        rebuildContentBlocks(updated.content)
        rebuildToolCards(updated)

        val usage = updated.usage
        if (usage != null) {
            footerBar.isVisible = true
            footerBar.tooltipText = "Tokens: ${usage.totalTokens} (in: ${usage.promptTokens}, out: ${usage.completionTokens}, reasoning: ${usage.reasoningTokens}) • Est: $${formatUsd(usage.estimatedCostUsd)}"
        } else {
            footerBar.isVisible = false
        }
    }

    private fun rebuildContentBlocks(markdown: String) {
        contentBlocksContainer.clearChildren()
        if (markdown.isEmpty()) return

        val fenceRegex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
        var lastIndex = 0
        for (match in fenceRegex.findAll(markdown)) {
            if (match.range.first > lastIndex) {
                val prose = markdown.substring(lastIndex, match.range.first).trim()
                if (prose.isNotEmpty()) {
                    val proseBlock = Container("ai-chat-markdown-prose").apply {
                        tooltipText = prose
                    }
                    contentBlocksContainer.addChild(proseBlock)
                }
            }
            val langTag = match.groupValues[1]
            val codeBody = match.groupValues[2].trimEnd()
            val lineCount = codeBody.count { it == '\n' } + 1
            val codeEditor = IdeCodeEditor(
                initialText = codeBody,
                language = CodeLanguage.fromFenceTag(langTag),
                width = 640,
                height = (lineCount * 22 + 24).coerceIn(80, 360)
            ).apply {
                readOnly = true
            }
            contentBlocksContainer.addChild(codeEditor)
            lastIndex = match.range.last + 1
        }

        if (lastIndex < markdown.length) {
            val remainingProse = markdown.substring(lastIndex).trim()
            if (remainingProse.isNotEmpty()) {
                val proseBlock = Container("ai-chat-markdown-prose").apply {
                    tooltipText = remainingProse
                }
                contentBlocksContainer.addChild(proseBlock)
            }
        }
    }

    private fun rebuildToolCards(msg: AiChatMessage) {
        toolCardsContainer.clearChildren()
        toolCallCards.clear()

        val resultsById = msg.toolResults.associateBy { it.toolCallId }
        for (tc in msg.toolCalls) {
            val card = AiChatToolCallCard(
                toolCall = tc,
                toolResult = resultsById[tc.id]
            )
            toolCallCards.add(card)
            toolCardsContainer.addChild(card)
        }

        if (msg.toolCalls.isEmpty() && msg.toolResults.isNotEmpty()) {
            for (tr in msg.toolResults) {
                val syntheticCall = AiToolCall(
                    id = tr.toolCallId,
                    name = tr.toolName,
                    argumentsJson = "{}",
                    status = if (tr.isError) AiToolCallStatus.FAILED else AiToolCallStatus.COMPLETED
                )
                val card = AiChatToolCallCard(toolCall = syntheticCall, toolResult = tr)
                toolCallCards.add(card)
                toolCardsContainer.addChild(card)
            }
        }
    }

    private fun formatUsd(amount: Double): String {
        val scaled = ((amount * 10000.0) + 0.5).toLong()
        val whole = scaled / 10000
        val frac = (scaled % 10000).toString().padStart(4, '0')
        return "$whole.$frac"
    }
}
