package com.nexustools.genericui.ext.aichat.dsl

import com.nexustools.genericui.ext.aichat.AiBackendType
import com.nexustools.genericui.ext.aichat.AiChatContainer
import com.nexustools.genericui.ext.aichat.AiChatMessage
import com.nexustools.genericui.ext.aichat.AiChatSession
import com.nexustools.genericui.ext.aichat.AiQuickPromptChip
import com.nexustools.genericui.ext.aichat.ReasoningEffort
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle

/**
 * Fluent DSL configuration scope for an [AiChatContainer] and its backing [AiChatSession].
 */
@GenericUiDslMarker
class AiChatDslConfigScope(
    val container: AiChatContainer,
    val session: AiChatSession = container.session
) {
    /**
     * Configures the active AI provider backend, endpoint URL, model identifier, and API key.
     */
    fun backend(
        type: AiBackendType,
        baseUrl: String = type.defaultBaseUrl,
        model: String = type.defaultModel,
        apiKey: String = ""
    ) {
        container.selectBackend(
            backendType = type,
            model = model,
            apiKey = apiKey,
            baseUrl = baseUrl
        )
    }

    /**
     * Sets the system prompt on the underlying [AiChatSession] configuration.
     */
    fun systemPrompt(prompt: String) {
        session.config = session.config.copy(systemPrompt = prompt)
        container.refreshToolbarSummary()
    }

    /**
     * Sets the initial markdown welcome message shown when the chat session is empty or cleared.
     */
    fun welcomeMessage(markdown: String) {
        container.welcomeMessageMarkdown = markdown
        if (session.messages.isEmpty()) {
            container.resetToWelcomeMessage()
        }
    }

    /**
     * Configures the reasoning effort level and whether the reasoning effort selector is visible.
     */
    fun reasoning(
        effort: ReasoningEffort,
        enabled: Boolean = effort != ReasoningEffort.NONE,
        budgetTokens: Int? = null
    ) {
        container.showReasoningEffortSelector = enabled
        container.selectReasoningEffort(
            effort = if (enabled) effort else ReasoningEffort.NONE,
            budgetTokens = budgetTokens
        )
    }

    /**
     * Appends a quick-action starter prompt chip to the chat container's toolbar.
     */
    fun quickPrompt(
        id: String,
        label: String,
        prompt: String,
        icon: String? = null,
        reasoningEffort: ReasoningEffort? = null
    ) {
        val displayLabel = if (!icon.isNullOrBlank()) "$icon $label" else label
        val tooltipSuffix = reasoningEffort?.takeIf { it != ReasoningEffort.NONE }
            ?.let { " [${it.displayName}]" }
            .orEmpty()
        val updated = container.getQuickPromptChips().toMutableList()
        updated.add(
            AiQuickPromptChip(
                label = displayLabel,
                prompt = prompt,
                tooltip = "$id$tooltipSuffix: $prompt"
            )
        )
        container.setQuickPromptChips(updated)
    }

    /**
     * Convenience overload to append a quick-action starter prompt chip using label and prompt text.
     */
    fun quickPrompt(
        label: String,
        prompt: String,
        tooltip: String = prompt
    ) {
        val updated = container.getQuickPromptChips().toMutableList()
        updated.add(AiQuickPromptChip(label = label, prompt = prompt, tooltip = tooltip))
        container.setQuickPromptChips(updated)
    }

    /**
     * Registers a callable AI tool definition and optional suspending handler with the session's tool executor.
     */
    fun tool(
        name: String,
        description: String,
        parametersJsonSchema: String = """{"type":"object","properties":{}}""",
        requiresConfirmation: Boolean = false,
        handler: suspend (String) -> String = { """{"status":"ok"}""" }
    ) {
        session.toolExecutor.registerSimpleTool(
            name = name,
            description = description,
            parametersJsonSchema = parametersJsonSchema,
            requiresConfirmation = requiresConfirmation,
            handler = handler
        )
        container.refreshToolbarSummary()
    }

    /**
     * Registers a callback invoked when the user clicks the AI Provider settings button.
     */
    fun onOpenProviderSettings(handler: () -> Unit) {
        container.onOpenProviderSettings = handler
    }

    /**
     * Registers a callback invoked when the user clicks the Tools catalog inspector button.
     */
    fun onOpenToolsInspector(handler: () -> Unit) {
        container.onOpenToolsCatalog = handler
    }

    /**
     * Registers a callback invoked when the conversation history is cleared.
     */
    fun onClearConversation(handler: () -> Unit) {
        container.clearChatButton.setOnClick {
            container.resetToWelcomeMessage()
            handler()
        }
    }

    /**
     * Registers a callback invoked whenever an assistant turn finishes executing.
     */
    fun onPromptExecuted(handler: (AiChatMessage) -> Unit) {
        container.onPromptExecutedCallback = handler
    }

    /**
     * Applies inline CSS component styles (`ComponentStyle`) to the [AiChatContainer].
     */
    fun style(block: (@GenericUiDslMarker ComponentStyle).() -> Unit) {
        container.getStyle().block()
    }

    /**
     * Applies Bootstrap 5 design-system utilities (`ComponentBootstrap`) to the [AiChatContainer].
     */
    fun bootstrap(block: (@GenericUiDslMarker ComponentBootstrap).() -> Unit) {
        container.getBootstrap().block()
    }
}
