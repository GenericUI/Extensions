package com.nexustools.genericui.ext.aichat.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.aichat.AiChatContainer
import com.nexustools.genericui.ext.aichat.AiChatMessage
import com.nexustools.genericui.ext.aichat.AiChatMessageView
import com.nexustools.genericui.ext.aichat.AiChatReasoningAccordion
import com.nexustools.genericui.ext.aichat.AiChatSession
import com.nexustools.genericui.ext.aichat.AiChatToolCallCard
import com.nexustools.genericui.ext.aichat.AiToolCall
import com.nexustools.genericui.ext.aichat.AiToolResult
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker

/**
 * In-place [AiChatContainer] builder configured via [AiChatDslConfigScope].
 */
fun AbstractContainer.aiChat(
    session: AiChatSession = AiChatSession(),
    width: Int = 720,
    height: Int = 800,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker AiChatDslConfigScope.() -> Unit)? = null
): AiChatContainer {
    val chatContainer = AiChatContainer(session = session, width = width, height = height)
    if (!classes.isNullOrBlank()) {
        chatContainer.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        chatContainer.id = id
    }
    if (block != null) {
        AiChatDslConfigScope(chatContainer, session).block()
    }
    addChild(chatContainer)
    return chatContainer
}

/**
 * Alias for [aiChat] that mounts and configures an [AiChatContainer].
 */
fun AbstractContainer.aiChatContainer(
    session: AiChatSession = AiChatSession(),
    width: Int = 720,
    height: Int = 800,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker AiChatDslConfigScope.() -> Unit)? = null
): AiChatContainer = aiChat(
    session = session,
    width = width,
    height = height,
    id = id,
    classes = classes,
    block = block
)

/**
 * In-place [AiChatMessageView] builder for rendering a single [AiChatMessage].
 */
fun AbstractContainer.aiChatMessageView(
    message: AiChatMessage,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker AiChatMessageView.() -> Unit)? = null
): AiChatMessageView {
    val view = AiChatMessageView(message = message)
    if (!classes.isNullOrBlank()) {
        view.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        view.id = id
    }
    if (block != null) {
        view.block()
    }
    addChild(view)
    return view
}

/**
 * In-place [AiChatToolCallCard] builder for rendering a tool invocation and its optional result.
 */
fun AbstractContainer.aiChatToolCallCard(
    toolCall: AiToolCall,
    toolResult: AiToolResult? = null,
    initiallyExpanded: Boolean = false,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker AiChatToolCallCard.() -> Unit)? = null
): AiChatToolCallCard {
    val card = AiChatToolCallCard(
        toolCall = toolCall,
        toolResult = toolResult,
        initiallyExpanded = initiallyExpanded
    )
    if (!classes.isNullOrBlank()) {
        card.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        card.id = id
    }
    if (block != null) {
        card.block()
    }
    addChild(card)
    return card
}

/**
 * In-place [AiChatReasoningAccordion] builder for rendering collapsible chain-of-thought content.
 */
fun AbstractContainer.aiChatReasoningAccordion(
    reasoningText: String = "",
    isStreaming: Boolean = false,
    initiallyExpanded: Boolean = false,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker AiChatReasoningAccordion.() -> Unit)? = null
): AiChatReasoningAccordion {
    val accordion = AiChatReasoningAccordion(
        reasoningText = reasoningText,
        isStreaming = isStreaming,
        initiallyExpanded = initiallyExpanded
    )
    if (!classes.isNullOrBlank()) {
        accordion.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        accordion.id = id
    }
    if (block != null) {
        accordion.block()
    }
    addChild(accordion)
    return accordion
}
