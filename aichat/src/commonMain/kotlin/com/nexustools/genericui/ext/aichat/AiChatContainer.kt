package com.nexustools.genericui.ext.aichat

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Divider
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.geometry.Size

/**
 * Complete cross-platform AI Chat UI extension container combining:
 * - Multi-backend connection selector (`OPENAI`, `ANTHROPIC`, `GEMINI`, `XAI_GROK`, `OLLAMA`, `OPENAI_COMPATIBLE`)
 * - Optional reasoning effort selector ([ReasoningEffort])
 * - Virtualized/scrollable message timeline ([AiChatMessageView]) with collapsible reasoning accordions
 *   and live tool-call status cards
 * - Multi-modal prompt composer with attachment staging and automated multi-turn tool execution via [AiChatSession].
 */
class AiChatContainer(
    val session: AiChatSession = AiChatSession(),
    width: Int = 720,
    height: Int = 800
) : VStack() {

    val headerToolbar: HStack = HStack()
    val messageScrollView: ScrollView = ScrollView()
    private val messageListColumn: VStack = VStack()
    val composerToolbar: HStack = HStack()

    var draftPromptText: String = ""
    private val stagedAttachments = mutableListOf<AiAttachment>()
    private val messageViews = mutableListOf<AiChatMessageView>()

    var showReasoningEffortSelector: Boolean = true
        set(value) {
            field = value
            refreshToolbarSummary()
        }

    init {
        addClass("ai-chat-container")
        setPreferredSize(Size(width, height))
        setPaddingAll(SpacingScale.S2)

        messageScrollView.addChild(messageListColumn)

        addChild(headerToolbar)
        addChild(Divider())
        addChild(messageScrollView)
        addChild(Divider())
        addChild(composerToolbar)

        session.addOnMessagesChangedListener { messages ->
            syncMessageViews(messages)
        }
        session.addOnStreamChunkListener {
            messageScrollView.scrollToBottom()
            refreshToolbarSummary()
        }

        refreshToolbarSummary()
        syncMessageViews(session.messages)
    }

    fun getMessageViews(): List<AiChatMessageView> = messageViews.toList()

    fun selectBackend(
        backendType: AiBackendType,
        model: String = backendType.defaultModel,
        apiKey: String = session.config.apiKey,
        baseUrl: String = backendType.defaultBaseUrl
    ) {
        session.switchBackend(backendType, model, apiKey, baseUrl)
        refreshToolbarSummary()
    }

    fun selectReasoningEffort(effort: ReasoningEffort, budgetTokens: Int? = null) {
        session.setReasoningEffort(effort, budgetTokens)
        refreshToolbarSummary()
    }

    fun stageAttachment(attachment: AiAttachment) {
        stagedAttachments.add(attachment)
        refreshComposerSummary()
    }

    fun clearStagedAttachments() {
        stagedAttachments.clear()
        refreshComposerSummary()
    }

    /**
     * Submits [promptText] (or [draftPromptText] if omitted) to [session], streaming the assistant
     * response and automatically running any tool calls.
     */
    suspend fun submitPrompt(
        promptText: String = draftPromptText,
        onChunk: suspend (AiStreamChunk) -> Unit = {}
    ): AiChatMessage {
        val text = promptText.trim()
        val attachments = stagedAttachments.toList()
        draftPromptText = ""
        stagedAttachments.clear()
        refreshComposerSummary()
        return session.sendUserMessage(text, attachments, onChunk)
    }

    private fun syncMessageViews(messages: List<AiChatMessage>) {
        if (messageViews.size > messages.size) {
            messageListColumn.clearChildren()
            messageViews.clear()
        }

        for (i in messages.indices) {
            val msg = messages[i]
            if (i < messageViews.size) {
                messageViews[i].bindMessage(msg)
            } else {
                val view = AiChatMessageView(msg)
                messageViews.add(view)
                messageListColumn.addChild(view)
            }
        }
        messageScrollView.scrollToBottom()
        refreshToolbarSummary()
    }

    private fun refreshToolbarSummary() {
        val cfg = session.config
        val reasoningLabel = if (showReasoningEffortSelector) {
            " • Reasoning: ${cfg.reasoningConfig.effort.displayName}"
        } else {
            ""
        }
        val usage = session.cumulativeUsage
        headerToolbar.tooltipText =
            "Backend: ${cfg.backendType.displayName} (${cfg.model})$reasoningLabel • Total Tokens: ${usage.totalTokens}"
    }

    private fun refreshComposerSummary() {
        composerToolbar.tooltipText =
            "Draft: ${draftPromptText.length} chars • Attachments: ${stagedAttachments.size}"
    }
}
