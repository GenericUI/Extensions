package com.nexustools.genericui.ext.aichat

import com.nexustools.genericui.ext.aichat.res.R
import com.nexustools.genericui.res.Resources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiChatStylesheetTest {

    @Test
    fun defaultStyleSheetCompilesCleanlyAndAppliesRoleAndStatusClasses() {
        val sheet = AiChatExtension.defaultStyleSheet()
        assertTrue(sheet.rules.isNotEmpty())

        val rawScss = Resources.getRawText(R.style.style)
        assertNotNull(rawScss)
        assertFalse(rawScss.contains("\$primary"))
        assertFalse(rawScss.contains("\$accent"))

        val chat = AiChatContainer().apply { applyStyleSheet(sheet) }
        assertTrue(chat.hasClass("ai-chat-container"))

        val msgView = AiChatMessageView(
            AiChatMessage(id = "m1", role = AiMessageRole.ASSISTANT, content = "Hello", reasoningContent = "Thinking")
        ).apply { applyStyleSheet(sheet) }
        assertTrue(msgView.hasClass("ai-chat-message-view"))
        assertTrue(msgView.hasClass("ai-message-role-assistant"))
        assertEquals(8f, msgView.computedStyle?.borderRadiusPx)
        assertTrue(msgView.reasoningAccordion.hasClass("ai-reasoning-accordion"))

        msgView.bindMessage(AiChatMessage(id = "m2", role = AiMessageRole.USER, content = "Hi"))
        assertFalse(msgView.hasClass("ai-message-role-assistant"))
        assertTrue(msgView.hasClass("ai-message-role-user"))

        val toolCard = AiChatToolCallCard(
            AiToolCall(id = "t1", name = "run", argumentsJson = "{}", status = AiToolCallStatus.COMPLETED)
        ).apply { applyStyleSheet(sheet) }
        assertTrue(toolCard.hasClass("ai-tool-call-card"))
        assertTrue(toolCard.hasClass("ai-tool-status-completed"))
    }
}
