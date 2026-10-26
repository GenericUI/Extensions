package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Generic Mobile (Android / iOS) overlay preset:
 * - `AutoHideSpec(timeoutMs = 3500, fadeDurationMs = 200, instantHideOnTap = true)`
 * - Top [GradientScrim] (`#EE000000 -> #99000000 -> transparent`) + [MetadataHeader] (title, subtitle,
 *   outlined `topBadges` chips, trailing action button bar)
 * - [CenterTransportCluster] (`-30s` / `Play-Pause` with [LoadingIndicator] / `+30s`)
 * - [SubtitleCueView] in [OverlaySlot.SUBTITLE_CUE]
 * - [OverlayStatusPill] in [OverlaySlot.BOTTOM_STATUS] (visible whenever `state.statusText` is non-empty)
 * - Bottom row in [OverlaySlot.BOTTOM_BAR]: formatted time pill (`state.formattedTimePair`) on the left +
 *   trailing circle action button bar (with Settings/Tracks modal button, Fullscreen button, and helper
 *   to add custom circle buttons)
 * - [SliderSeekBar] in [OverlaySlot.BOTTOM_SEEK] below the button row (using the app's primary `themeColor` by default)
 * - [OverlayModalDialog] in [OverlaySlot.POPOVER] for tracks, playback speed, and custom sections
 * - Tap on empty surface toggles overlay visibility (instant hide on tap).
 */
open class MobileOverlayPreset(
    overlayId: String = "mobile-overlay",
    skipSeconds: Double = CenterTransportCluster.DEFAULT_SKIP_SECONDS
) : PresetOverlay(
    overlayId = overlayId,
    autoHideSpec = AutoHideSpec(
        timeoutMs = 3500,
        fadeDurationMs = 200,
        instantHideOnTap = true
    )
) {

    val topScrim: GradientScrim = GradientScrim.mobileTopScrim(
        elementId = "$overlayId-top-scrim"
    )

    val bottomScrim: GradientScrim = GradientScrim.mobileBottomScrim(
        elementId = "$overlayId-bottom-scrim"
    )

    val centerTransportCluster: CenterTransportCluster = CenterTransportCluster(
        elementId = "$overlayId-center-transport",
        slot = OverlaySlot.CENTER,
        skipSeconds = skipSeconds,
        showSkipBackward = true,
        showSkipForward = true,
        autoHideController = autoHideController
    )

    val statusPill: OverlayStatusPill = OverlayStatusPill(
        elementId = "$overlayId-status-pill",
        slot = OverlaySlot.BOTTOM_STATUS
    )

    val timePill: OverlayPillButton = OverlayPillButton(
        elementId = "$overlayId-time-pill",
        text = "0:00 / --:--",
        variant = OverlayBadgeVariant.NEUTRAL,
        slot = OverlaySlot.BOTTOM_BAR,
        themeColorHex = effectiveThemeColorHex,
        autoHideController = autoHideController
    )

    val customTrailingButtonsContainer: HStack = HStack(gap = SpacingScale.S2).apply {
        setAlignItems(AlignItems.CENTER)
    }

    val optionsButton: OverlayIconButton = OverlayIconButton(
        elementId = "$overlayId-options",
        symbol = "⚙",
        tooltip = "Playback Options",
        slot = OverlaySlot.BOTTOM_BAR,
        circleSizeDp = ACTION_CIRCLE_SIZE_DP,
        autoHideController = autoHideController
    ) {
        modalDialog.toggle()
    }

    val fullscreenButton: OverlayIconButton = OverlayIconButton(
        elementId = "$overlayId-fullscreen",
        symbol = SYMBOL_ENTER_FULLSCREEN,
        tooltip = "Toggle Fullscreen",
        slot = OverlaySlot.BOTTOM_BAR,
        circleSizeDp = ACTION_CIRCLE_SIZE_DP,
        autoHideController = autoHideController
    ) {
        actions.onToggleFullscreen?.invoke()
    }

    val sliderSeekBar: SliderSeekBar = SliderSeekBar(
        elementId = "$overlayId-slider-seek",
        slot = OverlaySlot.BOTTOM_SEEK,
        themeColorHex = themeColorHex,
        autoHideController = autoHideController
    ) { _, formattedPair ->
        timePill.text = formattedPair
    }

    val modalDialog: OverlayModalDialog = OverlayModalDialog(
        elementId = "$overlayId-modal-dialog",
        slot = OverlaySlot.POPOVER,
        autoHideController = autoHideController
    )

    init {
        htmlTag = "genericui:mobile-overlay-preset"
        inputRouter.isTouchPrimaryMode = true

        // Reorder bottom slots so BOTTOM_BAR (time pill + circle buttons) sits above BOTTOM_SEEK (SliderSeekBar)
        removeChild(bottomSeekSlotContainer)
        removeChild(bottomBarSlotContainer)
        removeChild(bottomStatusSlotContainer)
        removeChild(popoverSlotContainer)
        removeChild(curtainSlotContainer)
        addChild(bottomStatusSlotContainer)
        addChild(bottomBarSlotContainer)
        addChild(bottomSeekSlotContainer)
        addChild(popoverSlotContainer)
        addChild(curtainSlotContainer)

        metadataHeader.showInlineStatusText = false
        topScrim.addChild(metadataHeader)
        addElement(topScrim)
        addElement(metadataHeader)

        addElement(centerTransportCluster)
        addElement(subtitleCueView)
        addElement(statusPill)

        bottomButtonBar.addLeading(timePill)
        bottomButtonBar.addTrailing(customTrailingButtonsContainer)
        bottomButtonBar.addTrailing(optionsButton)
        bottomButtonBar.addTrailing(fullscreenButton)
        addElement(bottomScrim)
        addElement(bottomButtonBar)

        addElement(sliderSeekBar)
        addElement(modalDialog)
        addElement(curtain)
        addElement(belowHostBadgeStrip)
    }

    fun addBottomCircleButton(
        id: String,
        symbol: String,
        tooltip: String? = null,
        sizeDp: Int = ACTION_CIRCLE_SIZE_DP,
        onClick: () -> Unit
    ): OverlayIconButton {
        val button = OverlayIconButton(
            elementId = id,
            symbol = symbol,
            tooltip = tooltip,
            slot = OverlaySlot.BOTTOM_BAR,
            circleSizeDp = sizeDp,
            autoHideController = autoHideController,
            onAction = onClick
        )
        customTrailingButtonsContainer.addChild(button)
        return button
    }

    override fun addCenterControl(component: AbstractComponent) {
        centerTransportCluster.addTrailingControl(component)
    }

    override fun addBottomTrailingControl(component: AbstractComponent) {
        customTrailingButtonsContainer.addChild(component)
    }

    override fun registerCustomMenuSection(
        sectionId: String,
        title: String,
        content: AbstractComponent
    ) {
        modalDialog.registerCustomSection(sectionId, title, content)
    }

    override fun onStateUpdated(state: OverlayState, actions: OverlayActions) {
        super.onStateUpdated(state, actions)
        sliderSeekBar.themeColorHex = themeColorHex
        timePill.themeColorHex = effectiveThemeColorHex
        if (!sliderSeekBar.isScrubbing) {
            timePill.text = state.formattedTimePair
        }
        fullscreenButton.symbol = if (state.isFullscreen) SYMBOL_EXIT_FULLSCREEN else SYMBOL_ENTER_FULLSCREEN
        fullscreenButton.tooltipText = if (state.isFullscreen) "Exit Fullscreen" else "Fullscreen"
    }

    companion object {
        const val ACTION_CIRCLE_SIZE_DP: Int = 44
        const val SYMBOL_ENTER_FULLSCREEN: String = "⛶"
        const val SYMBOL_EXIT_FULLSCREEN: String = "🗗"
    }
}
