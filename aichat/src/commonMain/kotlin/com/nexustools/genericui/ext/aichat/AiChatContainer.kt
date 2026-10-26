package com.nexustools.genericui.ext.aichat

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.Divider
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Complete cross-platform AI Studio Chat UI extension container combining:
 * - Multi-backend connection selector (`LM_STUDIO`, `OPENAI`, `ANTHROPIC`, `GEMINI`, `GEMINI_GOOGLE_ONE`,
 *   `XAI_GROK`, `OLLAMA`, `CUSTOM_OPENAI`, `CUSTOM_CLAUDE`, `OPENAI_COMPATIBLE`)
 * - Header actions bar (`⚙️ AI Provider`, `🧰 Tools` catalog inspector, `🗑️ Clear`) and live connection status badge
 * - Quick-action starter prompt chips ([AiQuickPromptChip])
 * - Virtualized/scrollable message timeline ([AiChatMessageView]) with collapsible reasoning accordions
 *   and live tool-call status cards
 * - Dual-send prompt composer with text input, Fast Send (`➡️`, non-reasoning `NONE` mode),
 *   and Brain Reasoning Send (`🧠`, with `Low` / `Medium` / `Max` reasoning level selector)
 */
class AiChatContainer(
    val session: AiChatSession = AiChatSession(),
    width: Int = 720,
    height: Int = 800
) : VStack() {

    private val scope = CoroutineScope(Dispatchers.Default)

    val headerToolbar: HStack = HStack(gap = SpacingScale.S1)
    val headerActionRow: HStack
        get() = headerToolbar
    val headerContainer: HStack
        get() = headerToolbar
    val connectionStatusBar: HStack = HStack(gap = SpacingScale.S1)
    val quickChipsToolbar: HStack = HStack(gap = SpacingScale.S1)
    val messageScrollView: ScrollView = ScrollView()
    private val messageListColumn: VStack = VStack(gap = SpacingScale.S1)
    val composerToolbar: HStack = HStack(gap = SpacingScale.S1)

    val connectionStatusBadge: Text = Text()
    val providerSettingsButton: Button = Button("⚙️ AI Provider", BootstrapType.SECONDARY, BootstrapSize.SM)
    val providerConfigButton: Button
        get() = providerSettingsButton
    val toolsCatalogButton: Button = Button("🧰 Tools", BootstrapType.SECONDARY, BootstrapSize.SM)
    val mcpToolsButton: Button
        get() = toolsCatalogButton
    val clearChatButton: Button = Button("🗑️ Clear", BootstrapType.DANGER, BootstrapSize.SM)
    val clearHistoryButton: Button
        get() = clearChatButton

    val promptInput: TextField = TextField(
        placeholder = "Ask AI to draw vectors, paint layers, apply GIMP filters, or animate... (Enter to send)"
    )
    val fastSendButton: Button = Button("➡️", BootstrapType.PRIMARY, BootstrapSize.SM)
    val brainSendButton: Button = Button("🧠 Med", BootstrapType.INFO, BootstrapSize.SM)
    val reasoningLevelSelect: Select = Select(
        options = listOf(
            SelectOption("low", "🧠 Low"),
            SelectOption("medium", "🧠 Medium"),
            SelectOption("max", "🧠 Max")
        ),
        selectedValue = "medium"
    )
    val stopButton: Button = Button("⏹", BootstrapType.DANGER, BootstrapSize.SM)

    var activeReasoningLevel: ReasoningEffort = ReasoningEffort.MEDIUM
        set(value) {
            field = value
            val shortTag = when (value) {
                ReasoningEffort.LOW -> "Low"
                ReasoningEffort.HIGH, ReasoningEffort.MAX -> "Max"
                else -> "Med"
            }
            brainSendButton.text = "🧠 $shortTag"
            refreshToolbarSummary()
        }

    var welcomeMessageMarkdown: String = ""
    private val quickPromptChips = mutableListOf<AiQuickPromptChip>()

    var onOpenProviderSettings: (() -> Unit)? = null
    var onOpenToolsCatalog: (() -> Unit)? = null
    var onPromptExecutedCallback: ((AiChatMessage) -> Unit)? = null

    var draftPromptText: String = ""
        get() = promptInput.value.ifEmpty { field }
        set(value) {
            field = value
            if (promptInput.value != value) {
                promptInput.value = value
            }
            refreshComposerSummary()
        }

    private val stagedAttachments = mutableListOf<AiAttachment>()
    private val messageViews = mutableListOf<AiChatMessageView>()

    var showReasoningEffortSelector: Boolean = true
        set(value) {
            field = value
            brainSendButton.isVisible = value
            reasoningLevelSelect.isVisible = value
            refreshToolbarSummary()
        }

    init {
        addClass("ai-chat-container")
        setPreferredSize(Size(width, height))
        setPaddingAll(SpacingScale.S2)

        providerSettingsButton.tooltipText = "Configure AI Provider (LM Studio / OpenAI / Claude / Gemini / Grok)"
        providerSettingsButton.setOnClick {
            val customHandler = onOpenProviderSettings
            if (customHandler != null) {
                customHandler()
            } else {
                cycleBackendProvider()
            }
        }

        toolsCatalogButton.tooltipText = "View Available AI Tools & Schemas"
        toolsCatalogButton.setOnClick {
            val customHandler = onOpenToolsCatalog
            if (customHandler != null) {
                customHandler()
            } else {
                appendToolsCatalogMessage()
            }
        }

        clearChatButton.tooltipText = "Clear Chat Conversation"
        clearChatButton.setOnClick {
            resetToWelcomeMessage()
        }

        headerToolbar.addChild(providerSettingsButton)
        headerToolbar.addChild(toolsCatalogButton)
        headerToolbar.addChild(clearChatButton)

        connectionStatusBar.addChild(connectionStatusBadge)

        promptInput.onValueChanged = { text ->
            draftPromptText = text
        }
        promptInput.onSubmit = { text ->
            val trimmed = text.trim()
            if (trimmed.isNotEmpty()) {
                submitPromptAsync(trimmed, ReasoningEffort.NONE)
            }
        }

        fastSendButton.tooltipText = "Fast Send (Non-Reasoning Mode — Enter)"
        fastSendButton.setOnClick {
            val text = draftPromptText.trim()
            if (text.isNotEmpty()) {
                submitPromptAsync(text, ReasoningEffort.NONE)
            }
        }

        brainSendButton.tooltipText = "Send with Reasoning Mode (Low / Medium / Max)"
        brainSendButton.setOnClick {
            val text = draftPromptText.trim()
            if (text.isNotEmpty()) {
                submitPromptAsync(text, activeReasoningLevel)
            } else {
                activeReasoningLevel = when (activeReasoningLevel) {
                    ReasoningEffort.LOW -> ReasoningEffort.MEDIUM
                    ReasoningEffort.MEDIUM -> ReasoningEffort.MAX
                    else -> ReasoningEffort.LOW
                }
                reasoningLevelSelect.selectedValue = when (activeReasoningLevel) {
                    ReasoningEffort.LOW -> "low"
                    ReasoningEffort.MEDIUM -> "medium"
                    else -> "max"
                }
            }
        }

        reasoningLevelSelect.tooltipText = "Select Reasoning Effort (Low / Medium / Max)"
        reasoningLevelSelect.onSelectionChanged = { selected ->
            activeReasoningLevel = when (selected.lowercase()) {
                "low" -> ReasoningEffort.LOW
                "max", "high" -> ReasoningEffort.MAX
                else -> ReasoningEffort.MEDIUM
            }
        }

        stopButton.tooltipText = "Stop Active AI Stream"
        stopButton.isVisible = false
        stopButton.setOnClick {
            session.cancelStreaming()
            stopButton.isVisible = false
            refreshToolbarSummary()
        }

        composerToolbar.addChild(promptInput)
        composerToolbar.addChild(fastSendButton)
        composerToolbar.addChild(brainSendButton)
        composerToolbar.addChild(reasoningLevelSelect)
        composerToolbar.addChild(stopButton)

        messageScrollView.addChild(messageListColumn)

        addChild(headerToolbar)
        addChild(connectionStatusBar)
        addChild(quickChipsToolbar)
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

    /**
     * The [TitleBar] that currently hosts the header action buttons ([providerSettingsButton],
     * [toolsCatalogButton], [clearChatButton]), or `null` while they live in [headerToolbar].
     */
    var boundHeaderTitleBar: TitleBar? = null
        private set

    /**
     * `true` while the header action buttons are embedded in [boundHeaderTitleBar] instead of [headerToolbar].
     */
    val areHeaderActionsInTitleBar: Boolean
        get() = boundHeaderTitleBar != null

    /**
     * The header action buttons in display order (`⚙️ AI Provider`, `🧰 Tools`, `🗑️ Clear`).
     */
    val headerActionButtons: List<Button>
        get() = listOf(providerSettingsButton, toolsCatalogButton, clearChatButton)

    /**
     * Places the header action buttons (`⚙️ AI Provider`, `🧰 Tools`, `🗑️ Clear`) in [titleBar]
     * or in [headerToolbar] according to [capabilities], ensuring they are rendered **only once**:
     * - When [TitleCapabilities.canEmbedComponentsInTitleBar] is `true`, the buttons are detached from
     *   [headerToolbar] (which is hidden once it has no remaining children), marked with
     *   `mountedInTitleBar = true`, and appended to [titleBar]'s trailing components (falling back to
     *   leading / center components when the host does not support trailing components). Returns `true`.
     * - Otherwise the buttons are removed from any previously bound [TitleBar], have
     *   `mountedInTitleBar = false` restored, and are placed at the front of a visible [headerToolbar].
     *   Returns `false`.
     *
     * Idempotent and reversible: calling it again (with the same or different [titleBar] / [capabilities])
     * re-homes the buttons without ever duplicating them.
     */
    fun bindHeaderActionsToTitleBar(
        titleBar: TitleBar,
        capabilities: TitleCapabilities = TitleCapabilities()
    ): Boolean {
        if (!capabilities.canEmbedComponentsInTitleBar) {
            unbindHeaderActionsFromTitleBar(titleBar)
            return false
        }

        val previous = boundHeaderTitleBar
        if (previous != null && previous !== titleBar) {
            removeHeaderActionsFrom(previous)
        }

        for (button in headerActionButtons) {
            headerToolbar.removeChild(button)
        }
        if (headerToolbar.children.isEmpty()) {
            headerToolbar.isVisible = false
        }

        titleBar.setHostEmbedsComponents(true)
        val buttons: List<AbstractComponent> = headerActionButtons
        val alreadyPlaced = when {
            capabilities.supportsTrailingComponents -> titleBar.trailingComponents
            capabilities.supportsLeadingComponents -> titleBar.leadingComponents
            else -> titleBar.centerComponents
        }
        val placedInOtherSlot = (titleBar.leadingComponents + titleBar.centerComponents + titleBar.trailingComponents)
            .count { comp -> buttons.any { it === comp } } != alreadyPlaced.count { comp -> buttons.any { it === comp } }
        val inExpectedOrder = alreadyPlaced.filter { comp -> buttons.any { it === comp } }
            .let { placed -> placed.size == buttons.size && placed.indices.all { placed[it] === buttons[it] } }
        if (placedInOtherSlot || !inExpectedOrder) {
            removeHeaderActionsFrom(titleBar)
            titleBar.setHostEmbedsComponents(true)
            for (button in buttons) {
                when {
                    capabilities.supportsTrailingComponents -> titleBar.addTrailing(button)
                    capabilities.supportsLeadingComponents -> titleBar.addLeading(button)
                    else -> titleBar.addCenter(button)
                }
            }
        }
        for (button in headerActionButtons) {
            button.mountedInTitleBar = true
            button.setAttribute("data-mounted-in-titlebar", "true")
        }
        boundHeaderTitleBar = titleBar
        return true
    }

    /**
     * Removes the header action buttons from [titleBar] (and from any other previously bound [TitleBar]),
     * resets `mountedInTitleBar = false` on each button, and restores them to the front of a visible
     * [headerToolbar] in their original order.
     */
    fun unbindHeaderActionsFromTitleBar(titleBar: TitleBar? = boundHeaderTitleBar) {
        if (titleBar != null) {
            removeHeaderActionsFrom(titleBar)
        }
        val previous = boundHeaderTitleBar
        if (previous != null && previous !== titleBar) {
            removeHeaderActionsFrom(previous)
        }
        boundHeaderTitleBar = null

        val buttons = headerActionButtons
        for (button in buttons) {
            button.mountedInTitleBar = false
            button.removeAttribute("data-mounted-in-titlebar")
        }
        val alreadyInOrder = headerToolbar.children.size >= buttons.size &&
            buttons.indices.all { headerToolbar.children[it] === buttons[it] }
        if (!alreadyInOrder) {
            for (button in buttons) {
                headerToolbar.removeChild(button)
            }
            buttons.forEachIndexed { index, button ->
                headerToolbar.addChild(button, index)
            }
        }
        if (!headerToolbar.isVisible) {
            headerToolbar.isVisible = true
        }
    }

    /**
     * [TitleBar] exposes no per-component removal, so the remaining components are re-added after
     * [TitleBar.clearComponents]. No-op when [titleBar] holds none of the header action buttons.
     */
    private fun removeHeaderActionsFrom(titleBar: TitleBar) {
        val buttons: List<AbstractComponent> = headerActionButtons
        val isHeaderAction: (AbstractComponent) -> Boolean = { comp -> buttons.any { it === comp } }
        val leading = titleBar.leadingComponents.toList()
        val center = titleBar.centerComponents.toList()
        val trailing = titleBar.trailingComponents.toList()
        val allExisting = leading + center + trailing
        if (allExisting.none(isHeaderAction)) {
            for (button in buttons) {
                button.mountedInTitleBar = false
                button.removeAttribute("data-mounted-in-titlebar")
            }
            return
        }
        val wasEmbedding = allExisting.any { it.mountedInTitleBar }
        titleBar.clearComponents()
        titleBar.setHostEmbedsComponents(wasEmbedding)
        leading.filterNot(isHeaderAction).forEach { titleBar.addLeading(it) }
        center.filterNot(isHeaderAction).forEach { titleBar.addCenter(it) }
        trailing.filterNot(isHeaderAction).forEach { titleBar.addTrailing(it) }
        for (button in buttons) {
            button.mountedInTitleBar = false
            button.removeAttribute("data-mounted-in-titlebar")
            if (button.parent != null && button.parent !== headerToolbar) {
                (button.parent as? com.nexustools.genericui.core.AbstractContainer)?.removeChild(button)
            }
        }
    }

    fun getMessageViews(): List<AiChatMessageView> = messageViews.toList()

    fun getQuickPromptChips(): List<AiQuickPromptChip> = quickPromptChips.toList()

    fun setQuickPromptChips(chips: List<AiQuickPromptChip>) {
        quickPromptChips.clear()
        quickPromptChips.addAll(chips)
        quickChipsToolbar.clearChildren()
        for (chip in chips) {
            val chipBtn = Button(chip.label, BootstrapType.SECONDARY, BootstrapSize.SM).apply {
                tooltipText = chip.tooltip
                setOnClick {
                    submitPromptAsync(chip.prompt, ReasoningEffort.NONE)
                }
            }
            quickChipsToolbar.addChild(chipBtn)
        }
    }

    fun resetToWelcomeMessage() {
        session.clearHistory()
        if (welcomeMessageMarkdown.isNotBlank()) {
            session.appendMessage(
                AiChatMessage(
                    id = "welcome_assistant",
                    role = AiMessageRole.ASSISTANT,
                    content = welcomeMessageMarkdown,
                    modelId = session.config.model
                )
            )
        }
        refreshToolbarSummary()
    }

    fun cycleBackendProvider() {
        val order = listOf(
            AiBackendType.LM_STUDIO,
            AiBackendType.OPENAI,
            AiBackendType.ANTHROPIC,
            AiBackendType.GEMINI,
            AiBackendType.GEMINI_GOOGLE_ONE,
            AiBackendType.XAI_GROK,
            AiBackendType.OLLAMA,
            AiBackendType.CUSTOM_OPENAI,
            AiBackendType.CUSTOM_CLAUDE
        )
        val curIdx = order.indexOf(session.config.backendType).coerceAtLeast(0)
        val next = order[(curIdx + 1) % order.size]
        selectBackend(next, next.defaultModel, session.config.apiKey, next.defaultBaseUrl)
    }

    fun appendToolsCatalogMessage() {
        val tools = session.toolExecutor.getToolDefinitions()
        val body = buildString {
            appendLine("### 🧰 Registered AI Studio Tools (${tools.size} Ready)")
            if (tools.isEmpty()) {
                appendLine("No tools currently registered.")
            } else {
                for (tool in tools) {
                    appendLine("- **`${tool.name}`** — ${tool.description}")
                }
            }
        }.trim()
        session.appendMessage(
            AiChatMessage(
                id = "tools_catalog_${session.messages.size + 1}",
                role = AiMessageRole.ASSISTANT,
                content = body,
                modelId = session.config.model
            )
        )
    }

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
        if (effort != ReasoningEffort.NONE) {
            activeReasoningLevel = effort
        }
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
     * Submits [promptText] asynchronously on the background scope, updating UI state and
     * executing any tool calls.
     */
    fun submitPromptAsync(
        promptText: String = draftPromptText,
        effortOverride: ReasoningEffort? = null
    ) {
        val text = promptText.trim()
        if (text.isEmpty() || session.isStreaming) return
        if (effortOverride != null) {
            session.setReasoningEffort(effortOverride)
        }
        draftPromptText = ""
        promptInput.value = ""
        stopButton.isVisible = true
        scope.launch {
            val result = submitPrompt(text)
            stopButton.isVisible = false
            onPromptExecutedCallback?.invoke(result)
        }
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
        promptInput.value = ""
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

    fun refreshToolbarSummary() {
        val cfg = session.config
        val toolCount = session.toolExecutor.getToolDefinitions().size
        val hostShort = cfg.baseUrl.removeSuffix("/v1").removeSuffix("/")
        val badgeText = "🔌 ${cfg.backendType.displayName} ($hostShort • ${cfg.model}) • $toolCount Tools Ready"
        connectionStatusBadge.text = badgeText

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
        val raw = promptInput.value
        composerToolbar.tooltipText =
            "Draft: ${raw.length} chars • Attachments: ${stagedAttachments.size}"
    }
}
