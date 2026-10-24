package com.nexustools.genericui.ext.aichat

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Collapsible accordion widget that displays an AI model's chain-of-thought / reasoning stream
 * (`reasoningContent`) with live streaming status and expand/collapse toggle.
 */
class AiChatReasoningAccordion(
    reasoningText: String = "",
    isStreaming: Boolean = false,
    initiallyExpanded: Boolean = false
) : VStack() {

    var reasoningContent: String = reasoningText
        private set

    var isStreamingReasoning: Boolean = isStreaming
        private set

    var isExpanded: Boolean = initiallyExpanded
        private set

    private val headerBar = HStack()
    private val bodyContainer = Container("ai-reasoning-body")

    init {
        addClass("ai-reasoning-accordion")
        setBorderType(BootstrapType.SECONDARY)
        setBorderRadius(BorderRadiusStyle.ROUNDED)
        setPaddingAll(SpacingScale.S2)
        setMarginAll(SpacingScale.S1)

        headerBar.setFontWeight(FontWeight.MEDIUM)
        headerBar.setOnClick {
            toggleExpanded()
        }

        bodyContainer.isVisible = initiallyExpanded
        addChild(headerBar)
        addChild(bodyContainer)
        refreshView()
    }

    val headerTitle: String
        get() {
            val chevron = if (isExpanded) "▾" else "▸"
            val stateLabel = if (isStreamingReasoning) "Thinking..." else "Thought Process"
            val approxTokens = (reasoningContent.length / 4).coerceAtLeast(if (reasoningContent.isNotEmpty()) 1 else 0)
            return "$chevron $stateLabel ($approxTokens tokens)"
        }

    fun updateReasoning(content: String, streaming: Boolean = false) {
        reasoningContent = content
        isStreamingReasoning = streaming
        refreshView()
    }

    fun setExpandedState(expanded: Boolean) {
        isExpanded = expanded
        bodyContainer.isVisible = expanded
        refreshView()
    }

    fun toggleExpanded() {
        setExpandedState(!isExpanded)
    }

    private fun refreshView() {
        headerBar.tooltipText = headerTitle
        bodyContainer.tooltipText = reasoningContent
        bodyContainer.isVisible = isExpanded && reasoningContent.isNotBlank()
    }
}
