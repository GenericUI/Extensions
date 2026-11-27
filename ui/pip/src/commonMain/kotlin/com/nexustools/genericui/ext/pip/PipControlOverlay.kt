package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.AutoHideSpec
import com.nexustools.genericui.ext.overlay.OverlayActions
import com.nexustools.genericui.ext.overlay.OverlayWindowTitleBarConfig

/**
 * Floating Picture-in-Picture control HUD overlay extending [AbstractOverlay].
 *
 * When mounted inside a windowed PiP shell, enables the embedded [windowTitleBar] (`32px`,
 * drag-to-move, close button, and macOS vibrancy + leading controls when supported) with
 * [restoreButton] hosted inside [windowTitleBar.trailingAccessorySlot]. When standalone or
 * hosted in a non-window backend (such as Web Document PiP), falls back gracefully to a
 * translucent top chrome bar while keeping rectangular `0px` corners and 2000ms auto-hide.
 */
class PipControlOverlay(
    var onRestoreRequested: () -> Unit = {},
    var onCloseRequested: () -> Unit = {},
    var onDragMoveRequested: () -> Unit = {},
    actions: List<PipAction> = emptyList(),
    var onPlayPauseRequested: (() -> Unit)? = null,
    var onDoubleClickRequested: (() -> Unit)? = null
) : AbstractOverlay(
    tagName = "genericui:pip-overlay",
    classPrefix = "gui-pip-overlay",
    autoHideSpec = AutoHideSpec(
        delayMs = AUTO_HIDE_DELAY_MS,
        fadeInMs = 150,
        fadeOutMs = 180,
        hideImmediatelyOnPointerLeave = false,
        instantHideOnTap = false,
        hideCursorWhenOverlayHidden = false
    )
) {

    private val pipOverlayActions: OverlayActions = OverlayActions(
        onPlayPause = {
            showTemporarily()
            onPlayPauseRequested?.invoke()
        },
        onToggleFullscreen = {
            showTemporarily()
            (onDoubleClickRequested ?: onRestoreRequested).invoke()
        }
    )

    val dragSurface: Container = Container("genericui:pip-drag-surface", "pip-drag-surface").apply {
        addClass("pip-drag-surface")
        setAttribute("data-pip-drag-surface", "true")
    }

    internal val chromeContainer: LayeredContainer = LayeredContainer(
        tagName = "genericui:pip-chrome",
        classPrefix = "gui-pip-chrome"
    ).apply {
        addClass("gui-pip-chrome")
    }

    internal val topScrim: StyledContainer = StyledContainer(
        tagName = "genericui:pip-top-scrim",
        classPrefix = "pip-top-scrim"
    ).apply {
        addClass("pip-top-scrim")
    }

    private val bottomScrim: StyledContainer = StyledContainer(
        tagName = "genericui:pip-bottom-scrim",
        classPrefix = "pip-bottom-scrim"
    ).apply {
        addClass("pip-bottom-scrim")
    }

    val titleLabel: Text
        get() = windowTitleBar.titleLabel

    val closeButton: Button
        get() = windowTitleBar.closeButton

    internal val fallbackTitleLabel: Text = Text("", paragraph = false, classPrefix = "pip-title").apply {
        addClass("pip-title")
        isVisible = false
    }

    val restoreButton: Button = createChromeButton(
        text = "⤢",
        tooltip = "Restore",
        buttonId = "pip-restore-btn"
    ) {
        showTemporarily()
        onRestoreRequested()
    }

    internal val fallbackCloseButton: Button = createChromeButton(
        text = "✕",
        tooltip = "Close PiP",
        buttonId = "pip-fallback-close-btn"
    ) {
        val barClick = windowTitleBar.closeButton.onClick
        if (barClick != null) {
            barClick.invoke()
        } else {
            showTemporarily()
            onCloseRequested()
        }
    }

    private val fallbackControlsRow: HStack = HStack(gapPx = 4).apply {
        addChild(restoreButton)
        addChild(fallbackCloseButton)
    }

    internal val topBar: HStack = HStack(gapPx = 6).apply {
        addClass("pip-top-bar")
        setJustifyContent(JustifyContent.BETWEEN)
        setAlignItems(AlignItems.CENTER)
        addChild(fallbackTitleLabel)
        addChild(Spacer.fill())
        addChild(fallbackControlsRow)
    }

    private val bottomBar: HStack = HStack(gapPx = 8).apply {
        addClass("pip-bottom-bar")
        setJustifyContent(JustifyContent.CENTER)
        setAlignItems(AlignItems.CENTER)
    }

    private val actionButtonList = mutableListOf<Button>()
    private var actionsList: List<PipAction> = emptyList()
    private var restoreMountedInTitleBar: Boolean = false

    val currentActions: List<PipAction>
        get() = actionsList

    val actionButtons: List<Button>
        get() = actionButtonList.toList()

    var titleText: String = ""
        private set

    var isChromeVisible: Boolean = true
        private set

    init {
        PipExtension.ensureStyleSheetRegistered()
        addClass("gui-pip-overlay")
        setAttribute("borderRadiusPx", "0")
        setAttribute("data-pip-overlay", "true")

        autoHideController.targetComponent = chromeContainer
        windowTitleBar.onCloseAction = {
            showTemporarily()
            onCloseRequested()
        }
        windowTitleBar.onDragMoveAction = {
            showTemporarily()
            onDragMoveRequested()
        }
        windowTitleBarConfig = OverlayWindowTitleBarConfig(
            enabled = true,
            heightPx = 32,
            showTitle = true,
            showMinimizeButton = false,
            showMaximizeButton = false,
            showCloseButton = true,
            controlsPlacement = TitleBarControlsPlacement.PLATFORM_DEFAULT
        )

        addLayer(dragSurface, LayerConstraint.fill(zIndex = 0))
        chromeContainer.addLayer(
            topScrim,
            LayerConstraint.float(
                alignment = LayerAlignment.TOP_START,
                zIndex = 0,
                passThroughPointerEvents = true
            )
        )
        chromeContainer.addLayer(
            bottomScrim,
            LayerConstraint.float(
                alignment = LayerAlignment.BOTTOM_START,
                zIndex = 0,
                passThroughPointerEvents = true
            )
        )
        chromeContainer.addLayer(
            topBar,
            LayerConstraint.float(alignment = LayerAlignment.TOP_START, zIndex = 1)
        )
        chromeContainer.addLayer(
            bottomBar,
            LayerConstraint.float(alignment = LayerAlignment.BOTTOM_CENTER, zIndex = 1)
        )
        addLayer(
            chromeContainer,
            LayerConstraint.fill(zIndex = 1, passThroughPointerEvents = true)
        )

        inputRouter.allowInputInPipOverlay = true
        windowDragEnabled = true
        onWindowDragMove = {
            showTemporarily()
            onDragMoveRequested()
        }
        super.updateActions(pipOverlayActions)
        inputRouter.bindStageSurface(
            component = dragSurface,
            stateProvider = { state },
            actionsProvider = { this.actions }
        )
        inputRouter.isolateInteractiveComponent(restoreButton)
        inputRouter.isolateInteractiveComponent(fallbackCloseButton)
        inputRouter.isolateInteractiveComponent(windowTitleBar)

        updateActions(actions)
        syncChromeMode()
        showTemporarily()
    }

    override fun ensureWindowTitleBarLayerMounted() {
        val constraint = LayerConstraint.float(
            alignment = LayerAlignment.TOP_START,
            offsetXPx = 0,
            offsetYPx = 0,
            zIndex = WINDOW_TITLE_BAR_Z_INDEX,
            passThroughPointerEvents = false
        )
        if (windowTitleBar.parent !== chromeContainer) {
            (windowTitleBar.parent as? LayeredContainer)?.removeLayer(windowTitleBar)
            chromeContainer.addLayer(windowTitleBar, constraint)
        } else if (chromeContainer.getLayerConstraint(windowTitleBar) != constraint) {
            chromeContainer.setLayerConstraint(windowTitleBar, constraint)
        }
    }

    override fun detachWindowTitleBarLayer() {
        if (windowTitleBar.parent === chromeContainer) {
            chromeContainer.removeLayer(windowTitleBar)
        } else {
            super.detachWindowTitleBarLayer()
        }
    }

    override fun updateActions(newActions: OverlayActions) {
        super.updateActions(
            newActions.copy(
                onPlayPause = newActions.onPlayPause ?: pipOverlayActions.onPlayPause,
                onToggleFullscreen = newActions.onToggleFullscreen ?: pipOverlayActions.onToggleFullscreen
            )
        )
    }

    override fun onHudVisibilityChanged(visible: Boolean, opacity: Float) {
        // Keep the outer drag/hit overlay mounted at full hit-testing so hover/tap/drag can wake the HUD.
        this.isVisible = true
        this.removeClass("hidden")
        applyChromeVisibility(visible)
        super.onHudVisibilityChanged(visible, opacity)
    }

    public override fun syncTopBarWindowTitleBarOffset() {
        syncChromeMode()
    }

    fun updateActions(actions: List<PipAction>) {
        actionsList = actions.toList()
        for (existing in bottomBar.children.toList()) {
            bottomBar.removeChild(existing)
            existing.dispose()
        }
        actionButtonList.clear()

        for (action in actionsList) {
            val btn = createChromeButton(
                text = glyphFor(action),
                tooltip = action.label,
                buttonId = "pip-action-${action.id}"
            ) {
                if (action.enabled) {
                    showTemporarily()
                    action.onTrigger()
                }
            }.apply {
                isEnabled = action.enabled
                setAttribute("data-pip-action-id", action.id)
                setAttribute("data-pip-action-icon", action.icon.name)
            }
            inputRouter.isolateInteractiveComponent(btn)
            actionButtonList.add(btn)
            bottomBar.addChild(btn)
        }

        val hasActions = actionsList.isNotEmpty()
        bottomBar.isVisible = isChromeVisible && hasActions
        bottomScrim.isVisible = isChromeVisible && hasActions
    }

    fun setOverlayTitle(title: String) {
        val normalized = title.trim()
        titleText = normalized
        windowTitleBarConfig = windowTitleBarConfig.copy(title = normalized.ifEmpty { null })
        fallbackTitleLabel.text = normalized
        syncChromeMode()
    }

    fun showTemporarily() {
        if (isDisposed) return
        autoHideController.notifyUserActivity()
    }

    private fun isWmManagingWindowChrome(): Boolean {
        val win = windowTitleBar.attachedWindow ?: resolveAttachedWindow() ?: return false
        val caps = win.titleCapabilities
        return caps.isTitleBarRequired && (!caps.supportsCustomTitleBar || !caps.canEmbedComponentsInTitleBar)
    }

    private fun syncChromeMode() {
        val windowedActive = windowTitleBar.isWindowedTitleBarActive
        val wmManagesChrome = !windowedActive && isWmManagingWindowChrome()
        if (windowedActive != restoreMountedInTitleBar) {
            restoreMountedInTitleBar = windowedActive
            if (windowedActive) {
                fallbackControlsRow.removeChild(restoreButton)
                restoreButton.addClass("pip-restore-btn-titlebar")
                windowTitleBar.addTrailingAccessory(restoreButton)
            } else {
                windowTitleBar.removeAccessory(restoreButton)
                restoreButton.removeClass("pip-restore-btn-titlebar")
                fallbackControlsRow.addChild(restoreButton, 0)
            }
        }
        updateTopFallbackChromeVisibility(
            visible = isChromeVisible,
            windowedActive = windowedActive,
            wmManagesChrome = wmManagesChrome
        )
    }

    private fun applyChromeVisibility(visible: Boolean) {
        isChromeVisible = visible
        chromeContainer.isVisible = true
        chromeContainer.setAttribute("data-hud-opacity", if (visible) "1.0" else "0.0")
        val windowedActive = windowTitleBar.isWindowedTitleBarActive
        val wmManagesChrome = !windowedActive && isWmManagingWindowChrome()
        updateTopFallbackChromeVisibility(
            visible = visible,
            windowedActive = windowedActive,
            wmManagesChrome = wmManagesChrome
        )
        val showActions = visible && actionsList.isNotEmpty()
        bottomScrim.isVisible = showActions
        bottomBar.isVisible = showActions
        if (showActions) {
            bottomBar.removeClass("pip-bar-inactive")
        } else {
            bottomBar.addClass("pip-bar-inactive")
        }
    }

    private fun updateTopFallbackChromeVisibility(
        visible: Boolean,
        windowedActive: Boolean,
        wmManagesChrome: Boolean
    ) {
        val showFallbackTop = visible && !windowedActive
        fallbackTitleLabel.isVisible = showFallbackTop && !wmManagesChrome && titleText.isNotEmpty()
        fallbackCloseButton.isVisible = showFallbackTop && !wmManagesChrome
        topScrim.isVisible = showFallbackTop && !wmManagesChrome
        topBar.isVisible = showFallbackTop
        if (showFallbackTop) {
            topBar.removeClass("pip-bar-inactive")
        } else {
            topBar.addClass("pip-bar-inactive")
        }
    }

    private fun createChromeButton(
        text: String,
        tooltip: String,
        buttonId: String,
        onClickAction: () -> Unit
    ): StyledButton = StyledButton(
        text = text,
        type = null,
        size = BootstrapSize.SM
    ).apply {
        id = buttonId
        addClass("pip-chrome-btn")
        tooltipText = tooltip
        onPointerEnter = { showTemporarily() }
        onPointerMove = { showTemporarily() }
        onClick = onClickAction
    }

    private fun glyphFor(action: PipAction): String = when (action.icon) {
        PipActionIcon.PLAY -> "▶"
        PipActionIcon.PAUSE -> "⏸"
        PipActionIcon.PREVIOUS -> "⏮"
        PipActionIcon.NEXT -> "⏭"
        PipActionIcon.SKIP_BACKWARD -> "⏪"
        PipActionIcon.SKIP_FORWARD -> "⏩"
        PipActionIcon.MUTE -> "🔇"
        PipActionIcon.UNMUTE -> "🔊"
        PipActionIcon.RESTORE -> "⤢"
        PipActionIcon.CLOSE -> "✕"
        PipActionIcon.CUSTOM -> action.label.trim().take(2).ifEmpty { "•" }
    }

    companion object {
        const val AUTO_HIDE_DELAY_MS: Int = 2000
    }
}
