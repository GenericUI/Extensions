package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter

/**
 * Factory helpers for standard overlay transport buttons, pill containers, time readouts,
 * and outlined feature badges matching `DesktopStageOverlaySpec` `data-overlay-role` hooks.
 */
object OverlayControlButtons {

    fun play(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.PLAY,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun pause(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.PAUSE,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun playPause(
        isPlaying: Boolean = false,
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onToggle: (() -> Unit)? = null
    ): OverlayPlayPauseButton = OverlayPlayPauseButton(
        isPlaying = isPlaying,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onPlayPauseToggle = onToggle
    )

    fun rewind(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.REWIND,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun fastForward(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.FAST_FORWARD,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun stop(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.STOP,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun next(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.NEXT,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun previous(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.PREVIOUS,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun fullscreenEnter(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.FULLSCREEN_ENTER,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun fullscreenExit(
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onClick: (() -> Unit)? = null
    ): OverlayControlButton = createSimpleButton(
        iconType = OverlayIconType.FULLSCREEN_EXIT,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onClick = onClick
    )

    fun fullscreenToggle(
        isFullscreen: Boolean = false,
        variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
        backdropFilter: CssFilter? = null,
        tooltipText: String? = null,
        onToggle: ((Boolean) -> Unit)? = null
    ): OverlayFullscreenButton = OverlayFullscreenButton(
        isFullscreen = isFullscreen,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onFullscreenToggle = onToggle
    )

    fun createControlsPill(
        role: String? = "controls-right",
        backgroundColor: RgbaColor = OverlayControlButton.DEFAULT_CIRCLE_FILL_COLOR,
        borderColor: RgbaColor = OverlayControlButton.DEFAULT_CIRCLE_BORDER_COLOR,
        backdropFilter: CssFilter? = null,
        id: String? = null
    ): StyledContainer = StyledContainer(classPrefix = "player-controls-pill").apply {
        OverlayExtension.ensureStyleSheetRegistered()
        addClass("player-controls-pill")
        if (!id.isNullOrBlank()) {
            this.id = id
        }
        if (!role.isNullOrBlank()) {
            setAttribute("data-overlay-role", role)
        }
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        if (backdropFilter != null) {
            addClass("player-btn-frosted")
            setAttribute("data-frosted", "true")
        }
    }

    fun createTimePill(
        initialText: String = "0:00 / --:--",
        backgroundColor: RgbaColor = OverlayControlButton.DEFAULT_CIRCLE_FILL_COLOR,
        borderColor: RgbaColor = OverlayControlButton.DEFAULT_CIRCLE_BORDER_COLOR,
        backdropFilter: CssFilter? = null,
        id: String? = null
    ): StyledContainer {
        OverlayExtension.ensureStyleSheetRegistered()
        val label = Text(initialText).apply {
            addClass("small player-time-label")
            setAttribute("data-overlay-role", "time-readout")
        }
        return StyledContainer(classPrefix = "player-time-pill").apply {
            addClass("player-time-pill")
            if (!id.isNullOrBlank()) {
                this.id = id
            }
            setAttribute("data-overlay-role", "time-readout")
            setDisplay(DisplayMode.FLEX)
            flexDirection = FlexDirection.ROW
            alignItems = AlignItems.CENTER
            if (backdropFilter != null) {
                addClass("player-btn-frosted")
                setAttribute("data-frosted", "true")
            }
            addChild(label)
        }
    }

    fun createOutlinedBadge(
        text: String,
        borderColor: RgbaColor = RgbaColor.WHITE,
        textColor: RgbaColor = RgbaColor.WHITE,
        id: String? = null
    ): StyledButton = StyledButton(text, BootstrapType.NONE, BootstrapSize.SM, outline = true).apply {
        OverlayExtension.ensureStyleSheetRegistered()
        addClass("px-2 py-0 fw-bold overlay-active-feature-badge")
        if (!id.isNullOrBlank()) {
            this.id = id
        }
        setAttribute("data-overlay-role", "outlined-feature-badge")
    }

    private fun createSimpleButton(
        iconType: OverlayIconType,
        variant: OverlayButtonVariant,
        accentColor: RgbaColor,
        backdropFilter: CssFilter?,
        tooltipText: String?,
        onClick: (() -> Unit)?
    ): OverlayControlButton = OverlayControlButton(
        iconType = iconType,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText
    ).apply {
        if (onClick != null) {
            this.onClick = onClick
        }
    }
}
