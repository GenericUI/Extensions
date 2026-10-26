package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Top-bar metadata header displaying `state.title` (bold), `state.subtitle` / detail text,
 * an inline [OverlayBadgeStrip] for `state.topBadges`, an optional status text line
 * (`state.statusText` on System+Web when non-empty), and a trailing [OverlayButtonBar] for
 * custom top-bar action buttons.
 */
open class MetadataHeader(
    override val elementId: String = "metadata-header",
    override val slot: OverlaySlot = OverlaySlot.TOP_BAR,
    var showInlineStatusText: Boolean = false,
    outlinedTopBadges: Boolean = true,
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex()
) : Container("genericui-overlay-metadata-header"), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    val textColumn: VStack = VStack(gap = SpacingScale.S1).apply {
        addClass("genericui-overlay-metadata-column")
    }

    val titleLabel: Label = Label("", "span", "genericui-overlay-title").apply {
        fontWeight = FontWeight.BOLD
        textColor = RgbaColor.WHITE
        setStyleProperty("font-size", "16px")
    }

    val subtitleRow: HStack = HStack(gap = SpacingScale.S2).apply {
        addClass("genericui-overlay-subtitle-row")
        setAlignItems(AlignItems.CENTER)
    }

    val subtitleLabel: Label = Label("", "span", "genericui-overlay-subtitle").apply {
        textColor = RgbaColor(220, 220, 220, 255)
        setStyleProperty("font-size", "12px")
        isVisible = false
    }

    val badgeStrip: OverlayBadgeStrip = OverlayBadgeStrip(
        elementId = "$elementId-badges",
        slot = OverlaySlot.TOP_BAR,
        outlinedStyle = outlinedTopBadges,
        themeColorHex = themeColorHex,
        autoSyncFromState = true
    )

    val statusLabel: Label = Label("", "span", "genericui-overlay-inline-status").apply {
        textColor = RgbaColor(200, 200, 200, 255)
        setStyleProperty("font-size", "12px")
        isVisible = false
    }

    val actionButtonBar: OverlayButtonBar = OverlayButtonBar(
        elementId = "$elementId-actions",
        slot = OverlaySlot.TOP_BAR
    )

    var themeColorHex: String = themeColorHex
        set(value) {
            field = value
            badgeStrip.themeColorHex = value
        }

    init {
        htmlTag = "genericui:overlay-metadata-header"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.ROW)
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.BETWEEN)

        subtitleRow.addChild(subtitleLabel)
        subtitleRow.addChild(badgeStrip)

        textColumn.addChild(titleLabel)
        textColumn.addChild(subtitleRow)
        textColumn.addChild(statusLabel)

        addChild(textColumn)
        addChild(actionButtonBar)
    }

    fun addActionButton(button: AbstractComponent) {
        actionButtonBar.addTrailing(button)
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        titleLabel.text = state.title
        titleLabel.isVisible = state.title.isNotEmpty()

        subtitleLabel.text = state.subtitle
        subtitleLabel.isVisible = state.subtitle.isNotEmpty()

        badgeStrip.onStateChanged(state, actions)
        subtitleRow.isVisible = subtitleLabel.isVisible || badgeStrip.isVisible

        statusLabel.text = state.statusText
        statusLabel.isVisible = showInlineStatusText && state.statusText.isNotEmpty()

        actionButtonBar.onStateChanged(state, actions)
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Container>.dispose()
    }
}
