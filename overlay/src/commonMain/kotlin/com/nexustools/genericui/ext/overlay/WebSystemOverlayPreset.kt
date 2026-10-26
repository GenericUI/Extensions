package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic System + Web overlay preset:
 * - `AutoHideSpec(timeoutMs = 3500, fadeDurationMs = 200, hideImmediatelyOnPointerLeave = true, hideCursorWhenHidden = true)`
 * - Top and bottom [GradientScrim]s
 * - [MetadataHeader] in [OverlaySlot.TOP_BAR] (title, subtitle, and `state.statusText` line when non-empty)
 * - [SubtitleCueView] in [OverlaySlot.SUBTITLE_CUE]
 * - [TrackSeekBar] in [OverlaySlot.BOTTOM_SEEK] above the bottom button bar (colored with the app's
 *   primary `themeColor` by default, with hover tooltip and drag-with-live-preview)
 * - Bottom [OverlayButtonBar] in [OverlaySlot.BOTTOM_BAR]:
 *   - Leading: Play/Pause button, optional Next button (shown when `actions.onNext != null`),
 *     [VolumeControl], formatted time label (`state.formattedTimePair`), plus a slot for custom leading/inline controls
 *   - Trailing: custom trailing buttons slot, Settings popover trigger button ([OverlayPopoverPanel]), and Fullscreen button
 * - [OverlayBadgeStrip] in [OverlaySlot.BELOW_HOST] (rendered in `host.belowHostContainer` outside the
 *   video/canvas overlay whenever `state.belowHostBadges` is non-empty)
 * - Pointer click = play/pause (250ms debounce), double-click = `actions.onToggleFullscreen`,
 *   pointer move shows overlay, pointer leave hides immediately.
 */
open class WebSystemOverlayPreset(
    overlayId: String = "web-system-overlay"
) : PresetOverlay(
    overlayId = overlayId,
    autoHideSpec = AutoHideSpec(
        timeoutMs = 3500,
        fadeDurationMs = 200,
        hideImmediatelyOnPointerLeave = true,
        hideCursorWhenHidden = true
    )
) {

    val topScrim: GradientScrim = GradientScrim.webSystemTopScrim(
        elementId = "$overlayId-top-scrim"
    )

    val bottomScrim: GradientScrim = GradientScrim.webSystemBottomScrim(
        elementId = "$overlayId-bottom-scrim"
    )

    val loadingIndicator: LoadingIndicator = LoadingIndicator(
        elementId = "$overlayId-loading",
        slot = OverlaySlot.CENTER
    )

    val trackSeekBar: TrackSeekBar = TrackSeekBar(
        elementId = "$overlayId-track-seek",
        slot = OverlaySlot.BOTTOM_SEEK,
        themeColorHex = themeColorHex,
        autoHideController = autoHideController
    ) { previewSec, _ ->
        timeLabel.text = OverlayTimeFormatter.formatPositionAndDuration(previewSec, state.durationSec)
    }

    val playPauseButton: OverlayIconButton = OverlayIconButton(
        elementId = "$overlayId-play-pause",
        symbol = CenterTransportCluster.SYMBOL_PLAY,
        tooltip = "Play",
        slot = OverlaySlot.BOTTOM_BAR,
        circleSizeDp = null,
        autoHideController = autoHideController
    ) {
        actions.triggerPlayPause(state.isPlaying)
    }

    val nextButton: OverlayIconButton = OverlayIconButton(
        elementId = "$overlayId-next",
        symbol = SYMBOL_NEXT,
        tooltip = "Next",
        slot = OverlaySlot.BOTTOM_BAR,
        circleSizeDp = null,
        autoHideController = autoHideController
    ) {
        actions.onNext?.invoke()
    }.apply {
        isVisible = false
    }

    val volumeControl: VolumeControl = VolumeControl(
        elementId = "$overlayId-volume",
        slot = OverlaySlot.BOTTOM_BAR,
        revealSliderOnHover = true,
        autoHideController = autoHideController
    )

    val timeLabel: Label = Label("0:00 / --:--", "span", "genericui-overlay-time-label").apply {
        textColor = RgbaColor.WHITE
        setStyleProperty("font-size", "13px")
    }

    val customLeadingSlot: HStack = HStack(gap = SpacingScale.S2).apply {
        setAlignItems(AlignItems.CENTER)
    }

    val customTrailingSlot: HStack = HStack(gap = SpacingScale.S2).apply {
        setAlignItems(AlignItems.CENTER)
    }

    val settingsButton: OverlayIconButton = OverlayIconButton(
        elementId = "$overlayId-settings",
        symbol = SYMBOL_SETTINGS,
        tooltip = "Settings",
        slot = OverlaySlot.BOTTOM_BAR,
        circleSizeDp = null,
        autoHideController = autoHideController
    ) {
        popoverPanel.toggle()
    }

    val fullscreenButton: OverlayIconButton = OverlayIconButton(
        elementId = "$overlayId-fullscreen",
        symbol = MobileOverlayPreset.SYMBOL_ENTER_FULLSCREEN,
        tooltip = "Fullscreen",
        slot = OverlaySlot.BOTTOM_BAR,
        circleSizeDp = null,
        autoHideController = autoHideController
    ) {
        actions.onToggleFullscreen?.invoke()
    }

    val popoverPanel: OverlayPopoverPanel = OverlayPopoverPanel(
        elementId = "$overlayId-popover-panel",
        slot = OverlaySlot.POPOVER,
        autoHideController = autoHideController
    )

    init {
        htmlTag = "genericui:web-system-overlay-preset"
        inputRouter.isTouchPrimaryMode = false

        metadataHeader.showInlineStatusText = true
        addElement(topScrim)
        addElement(metadataHeader)

        addElement(loadingIndicator)
        addElement(subtitleCueView)
        addElement(trackSeekBar)

        bottomButtonBar.addLeading(playPauseButton)
        bottomButtonBar.addLeading(nextButton)
        bottomButtonBar.addLeading(volumeControl)
        bottomButtonBar.addLeading(timeLabel)
        bottomButtonBar.addLeading(customLeadingSlot)

        bottomButtonBar.addTrailing(customTrailingSlot)
        bottomButtonBar.addTrailing(settingsButton)
        bottomButtonBar.addTrailing(fullscreenButton)

        addElement(bottomScrim)
        addElement(bottomButtonBar)
        addElement(popoverPanel)
        addElement(curtain)
        addElement(belowHostBadgeStrip)
    }

    override fun addBottomLeadingControl(component: AbstractComponent) {
        customLeadingSlot.addChild(component)
    }

    override fun addBottomTrailingControl(component: AbstractComponent) {
        customTrailingSlot.addChild(component)
    }

    override fun registerCustomMenuSection(
        sectionId: String,
        title: String,
        content: AbstractComponent
    ) {
        popoverPanel.registerCustomSection(sectionId, title, content)
    }

    override fun onStateUpdated(state: OverlayState, actions: OverlayActions) {
        super.onStateUpdated(state, actions)
        trackSeekBar.themeColorHex = themeColorHex
        playPauseButton.symbol = if (state.isPlaying) {
            CenterTransportCluster.SYMBOL_PAUSE
        } else {
            CenterTransportCluster.SYMBOL_PLAY
        }
        playPauseButton.tooltipText = if (state.isPlaying) "Pause" else "Play"
        nextButton.isVisible = actions.onNext != null
        volumeControl.onStateChanged(state, actions)
        if (!trackSeekBar.isScrubbing) {
            timeLabel.text = state.formattedTimePair
        }
        fullscreenButton.symbol = if (state.isFullscreen) {
            MobileOverlayPreset.SYMBOL_EXIT_FULLSCREEN
        } else {
            MobileOverlayPreset.SYMBOL_ENTER_FULLSCREEN
        }
        fullscreenButton.tooltipText = if (state.isFullscreen) "Exit Fullscreen" else "Fullscreen"
    }

    companion object {
        const val SYMBOL_NEXT: String = "⏭"
        const val SYMBOL_SETTINGS: String = "⚙"
    }
}
