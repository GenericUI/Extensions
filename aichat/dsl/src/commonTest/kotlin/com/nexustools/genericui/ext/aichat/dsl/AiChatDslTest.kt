package com.nexustools.genericui.ext.aichat.dsl

import com.nexustools.genericui.ext.aichat.AiBackendType
import com.nexustools.genericui.ext.aichat.AiChatMessage
import com.nexustools.genericui.ext.aichat.AiMessageRole
import com.nexustools.genericui.ext.aichat.AiToolCall
import com.nexustools.genericui.ext.aichat.AiToolCallStatus
import com.nexustools.genericui.ext.aichat.ReasoningEffort
import com.nexustools.genericui.ext.dsl.ColorRgba
import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiChatDslTest {

    @Test
    fun testAiChatDslBuildersAndConfigScope() {
        var providerSettingsOpened = false
        var toolsInspectorOpened = false
        var conversationCleared = false

        val root = buildDiv(classes = "workspace-root") {
            aiChat(id = "studio-ai-chat", classes = "custom-chat") {
                backend(
                    type = AiBackendType.ANTHROPIC,
                    baseUrl = "https://api.anthropic.com/v1",
                    model = "claude-3-7-sonnet-latest",
                    apiKey = "test-key"
                )
                systemPrompt("You are an AI Studio assistant.")
                reasoning(effort = ReasoningEffort.HIGH, enabled = true)
                quickPrompt(
                    id = "starter-1",
                    label = "Draw Star",
                    prompt = "Draw a 5-pointed vector star",
                    icon = "⭐",
                    reasoningEffort = ReasoningEffort.MEDIUM
                )
                tool(
                    name = "create_vector_shape",
                    description = "Creates a vector shape on the canvas",
                    parametersJsonSchema = """{"type":"object","properties":{"shape":{"type":"string"}}}"""
                )
                onOpenProviderSettings { providerSettingsOpened = true }
                onOpenToolsInspector { toolsInspectorOpened = true }
                onClearConversation { conversationCleared = true }
                style {
                    tint = ColorRgba(20, 30, 45, 1.0)
                }
            }

            aiChatMessageView(
                message = AiChatMessage(
                    id = "msg-1",
                    role = AiMessageRole.ASSISTANT,
                    content = "Hello from AI Studio!",
                    reasoningContent = "Evaluating request..."
                ),
                id = "msg-view-1"
            )

            aiChatToolCallCard(
                toolCall = AiToolCall(
                    id = "call-1",
                    name = "create_vector_shape",
                    argumentsJson = """{"shape":"star"}""",
                    status = AiToolCallStatus.COMPLETED
                ),
                initiallyExpanded = true,
                id = "tool-card-1"
            )

            aiChatReasoningAccordion(
                reasoningText = "Step 1: Analyze canvas bounds.",
                isStreaming = false,
                initiallyExpanded = true,
                id = "reasoning-1"
            )
        }

        assertEquals(4, root.children.size)

        val chat = root.children[0] as com.nexustools.genericui.ext.aichat.AiChatContainer
        assertEquals("studio-ai-chat", chat.id)
        assertTrue(chat.hasClass("custom-chat"))
        assertEquals(AiBackendType.ANTHROPIC, chat.session.config.backendType)
        assertEquals("claude-3-7-sonnet-latest", chat.session.config.model)
        assertEquals("You are an AI Studio assistant.", chat.session.config.systemPrompt)
        assertEquals(ReasoningEffort.HIGH, chat.session.config.reasoningConfig.effort)
        assertEquals(1, chat.getQuickPromptChips().size)
        assertTrue(chat.session.toolExecutor.hasTool("create_vector_shape"))

        chat.providerSettingsButton.onClick?.invoke()
        assertTrue(providerSettingsOpened)

        chat.toolsCatalogButton.onClick?.invoke()
        assertTrue(toolsInspectorOpened)

        chat.clearChatButton.onClick?.invoke()
        assertTrue(conversationCleared)
    }
}
