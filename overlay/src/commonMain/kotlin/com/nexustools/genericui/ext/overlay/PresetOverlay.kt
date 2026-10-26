package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.AbstractComponent

/**
 * Open subclass of [AbstractOverlay] providing structured slot elements and convenience helpers
 * to mount custom buttons, pills, menu sections, curtain content, and [OverlaySlot.BELOW_HOST]
 * badges across both [MobileOverlayPreset] and [WebSystemOverlayPreset].
 */
open class PresetOverlay(
    overlayId: String = "preset-overlay",
    autoHideSpec: AutoHideSpec = AutoHideSpec(timeoutMs = 3500, fadeDurationMs = 200)
) : AbstractOverlay(
    overlayId = overlayId,
    autoHideSpec = autoHideSpec
) {

    open val metadataHeader: MetadataHeader = MetadataHeader(
        elementId = "$overlayId-metadata-header",
        slot = OverlaySlot.TOP_BAR
    )

    open val subtitleCueView: SubtitleCueView = SubtitleCueView(
        elementId = "$overlayId-subtitle-cue",
        slot = OverlaySlot.SUBTITLE_CUE
    )

    open val bottomButtonBar: OverlayButtonBar = OverlayButtonBar(
        elementId = "$overlayId-bottom-button-bar",
        slot = OverlaySlot.BOTTOM_BAR
    )

    open val curtain: OverlayCurtain = OverlayCurtain(
        elementId = "$overlayId-curtain",
        slot = OverlaySlot.CURTAIN,
        autoHideController = autoHideController
    )

    open val belowHostBadgeStrip: OverlayBadgeStrip = OverlayBadgeStrip(
        elementId = "$overlayId-below-host-badges",
        slot = OverlaySlot.BELOW_HOST,
        outlinedStyle = false,
        autoSyncFromState = true
    )

    open fun addTopBarActionButton(button: AbstractComponent) {
        metadataHeader.addActionButton(button)
    }

    open fun addTopBarIconButton(
        id: String,
        symbol: String,
        tooltip: String? = null,
        circleSizeDp: Int? = null,
        onClick: () -> Unit
    ): OverlayIconButton {
        val btn = OverlayIconButton(
            elementId = id,
            symbol = symbol,
            tooltip = tooltip,
            slot = OverlaySlot.TOP_BAR,
            circleSizeDp = circleSizeDp,
            autoHideController = autoHideController,
            onAction = onClick
        )
        addTopBarActionButton(btn)
        return btn
    }

    open fun addCenterControl(component: AbstractComponent) {
        centerSlotContainer.addChild(component)
    }

    open fun addBottomLeadingControl(component: AbstractComponent) {
        bottomButtonBar.addLeading(component)
    }

    open fun addBottomCenterControl(component: AbstractComponent) {
        bottomButtonBar.addCenter(component)
    }

    open fun addBottomTrailingControl(component: AbstractComponent) {
        bottomButtonBar.addTrailing(component)
    }

    open fun addBottomIconButton(
        id: String,
        symbol: String,
        tooltip: String? = null,
        leading: Boolean = false,
        circleSizeDp: Int? = null,
        onClick: () -> Unit
    ): OverlayIconButton {
        val btn = OverlayIconButton(
            elementId = id,
            symbol = symbol,
            tooltip = tooltip,
            slot = OverlaySlot.BOTTOM_BAR,
            circleSizeDp = circleSizeDp,
            autoHideController = autoHideController,
            onAction = onClick
        )
        if (leading) {
            addBottomLeadingControl(btn)
        } else {
            addBottomTrailingControl(btn)
        }
        return btn
    }

    open fun addBottomPillButton(
        id: String,
        text: String,
        variant: OverlayBadgeVariant = OverlayBadgeVariant.NEUTRAL,
        leading: Boolean = true,
        onClick: () -> Unit
    ): OverlayPillButton {
        val pill = OverlayPillButton(
            elementId = id,
            text = text,
            variant = variant,
            slot = OverlaySlot.BOTTOM_BAR,
            themeColorHex = effectiveThemeColorHex,
            autoHideController = autoHideController,
            onAction = onClick
        )
        if (leading) {
            addBottomLeadingControl(pill)
        } else {
            addBottomTrailingControl(pill)
        }
        return pill
    }

    open fun registerCustomMenuSection(
        sectionId: String,
        title: String,
        content: AbstractComponent
    ) {
        // Implemented by subclasses with a popover or modal dialog
    }

    open fun setCurtainContent(content: AbstractComponent?, visible: Boolean = content != null) {
        curtain.setCurtainContent(content, show = visible)
    }

    open fun showCurtain() {
        curtain.showCurtain()
    }

    open fun hideCurtain() {
        curtain.hideCurtain()
    }

    open fun setBelowHostBadges(badges: List<OverlayBadgeSpec>) {
        updateState { it.copy(belowHostBadges = badges) }
    }

    override fun onStateUpdated(state: OverlayState, actions: OverlayActions) {
        super.onStateUpdated(state, actions)
        val resolvedTheme = effectiveThemeColorHex
        metadataHeader.themeColorHex = resolvedTheme
        belowHostBadgeStrip.themeColorHex = resolvedTheme
    }
}
