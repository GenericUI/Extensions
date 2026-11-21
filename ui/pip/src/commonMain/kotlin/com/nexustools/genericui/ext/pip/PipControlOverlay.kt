package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.AutoHideSpec
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient

/**
 * Floating Picture-in-Picture control HUD overlay extending [AbstractOverlay].
 *
 * Provides a background drag/hover surface, top-right Restore (`⤢`) and Close (`✕`) buttons,
 * a bottom transport strip populated from [PipAction] descriptors, dark gradient edge scrims,
 * and automatic fade/hide after 2000ms of pointer inactivity.
 */
class PipControlOverlay(
    var onRestoreRequested: () -> Unit = {},
    var onCloseRequested: () -> Unit = {},
    var onDragMoveRequested: () -> Unit = {},
    actions: List<PipAction> = emptyList()
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

    val dragSurface: Container = Container("genericui:pip-drag-surface", "pip-drag-surface").apply {
        style.width = CssLength.percent(100f)
        style.height = CssLength.percent(100f)
        style.backgroundColor = RgbaColor.TRANSPARENT
        cursor = "grab"
        setAttribute("data-pip-drag-surface", "true")
        onPointerDown = {
            showTemporarily()
            onDragMoveRequested()
        }
        onPointerEnter = { showTemporarily() }
        onPointerMove = { showTemporarily() }
    }

    private val chromeContainer: LayeredContainer = LayeredContainer(
        tagName = "genericui:pip-chrome",
        classPrefix = "gui-pip-chrome"
    ).apply {
        style.width = CssLength.percent(100f)
        style.height = CssLength.percent(100f)
        style.backgroundColor = RgbaColor.TRANSPARENT
    }

    private val topScrim: Container = Container("genericui:pip-top-scrim", "pip-top-scrim").apply {
        style.width = CssLength.percent(100f)
        style.height = CssLength.px(44)
        style.backgroundGradient = LinearGradient(
            180f,
            RgbaColor(0, 0, 0, 175),
            RgbaColor(0, 0, 0, 0)
        )
        style.pointerEvents = CssPointerEvents.NONE
    }

    private val bottomScrim: Container = Container("genericui:pip-bottom-scrim", "pip-bottom-scrim").apply {
        style.width = CssLength.percent(100f)
        style.height = CssLength.px(56)
        style.backgroundGradient = LinearGradient(
            0f,
            RgbaColor(0, 0, 0, 190),
            RgbaColor(0, 0, 0, 0)
        )
        style.pointerEvents = CssPointerEvents.NONE
    }

    val titleLabel: Text = Text("", paragraph = false, classPrefix = "pip-title").apply {
        style.color = RgbaColor.WHITE
        style.fontSize = CssLength.px(12)
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

    val closeButton: Button = createChromeButton(
        text = "✕",
        tooltip = "Close PiP",
        buttonId = "pip-close-btn"
    ) {
        showTemporarily()
        onCloseRequested()
    }

    private val topBar: HStack = HStack(gapPx = 6).apply {
        style.width = CssLength.percent(100f)
        setPadding(EdgeInsets(top = 6, right = 8, bottom = 4, left = 8))
        setJustifyContent(JustifyContent.BETWEEN)
        setAlignItems(AlignItems.CENTER)
        addChild(titleLabel)
        addChild(Spacer.fill())
        addChild(
            HStack(gapPx = 4).apply {
                addChild(restoreButton)
                addChild(closeButton)
            }
        )
    }

    private val bottomBar: HStack = HStack(gapPx = 8).apply {
        style.width = CssLength.percent(100f)
        setPadding(EdgeInsets(top = 4, right = 8, bottom = 8, left = 8))
        setJustifyContent(JustifyContent.CENTER)
        setAlignItems(AlignItems.CENTER)
    }

    private val actionButtonList = mutableListOf<Button>()
    private var actionsList: List<PipAction> = emptyList()

    val currentActions: List<PipAction>
        get() = actionsList

    val actionButtons: List<Button>
        get() = actionButtonList.toList()

    var titleText: String = ""
        private set

    var isChromeVisible: Boolean = true
        private set

    init {
        setAttribute("data-pip-overlay", "true")
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

        onPointerEnter = { showTemporarily() }
        onPointerMove = { showTemporarily() }
        onPointerDown = { showTemporarily() }

        autoHideController.onVisibilityChanged = { visible, opacityValue ->
            // Keep the outer drag/hit overlay mounted at full hit-testing so hover/tap/drag can wake the HUD.
            this.isVisible = true
            this.opacity = 1.0f
            this.style.pointerEvents = CssPointerEvents.AUTO
            this.style.cursor = null
            applyChromeVisibility(visible, opacityValue)
        }

        updateActions(actions)
        showTemporarily()
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
        titleLabel.text = normalized
        titleLabel.isVisible = normalized.isNotEmpty()
    }

    fun showTemporarily() {
        if (isDisposed) return
        autoHideController.notifyUserActivity()
        applyChromeVisibility(true, 1.0f)
    }

    private fun applyChromeVisibility(visible: Boolean, opacityValue: Float) {
        isChromeVisible = visible
        chromeContainer.isVisible = true
        chromeContainer.opacity = if (visible) opacityValue.coerceIn(0f, 1f) else 0f
        chromeContainer.style.pointerEvents = CssPointerEvents.NONE
        topScrim.isVisible = visible
        topBar.isVisible = visible
        topBar.style.pointerEvents = if (visible) CssPointerEvents.AUTO else CssPointerEvents.NONE
        val showActions = visible && actionsList.isNotEmpty()
        bottomScrim.isVisible = showActions
        bottomBar.isVisible = showActions
        bottomBar.style.pointerEvents = if (showActions) CssPointerEvents.AUTO else CssPointerEvents.NONE
    }

    private fun createChromeButton(
        text: String,
        tooltip: String,
        buttonId: String,
        onClickAction: () -> Unit
    ): Button = Button(
        text = text,
        type = null,
        size = BootstrapSize.SM
    ).apply {
        id = buttonId
        tooltipText = tooltip
        cursor = "pointer"
        style.color = RgbaColor.WHITE
        style.backgroundColor = RgbaColor(20, 20, 20, 185)
        style.borderRadius = CssCornerRadii.px(6)
        setPadding(EdgeInsets.symmetric(vertical = 3, horizontal = 7))
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
