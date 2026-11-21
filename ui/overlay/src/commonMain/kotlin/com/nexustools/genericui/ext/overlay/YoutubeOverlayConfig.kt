package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient

/**
 * Configuration options for [YoutubePlayerOverlay].
 */
data class YoutubeOverlayConfig(
    val accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
    val topScrimGradient: Gradient? = DEFAULT_TOP_SCRIM_GRADIENT,
    val bottomScrimGradient: Gradient? = DEFAULT_BOTTOM_SCRIM_GRADIENT,
    val autoHideSpec: AutoHideSpec = DEFAULT_AUTO_HIDE_SPEC,
    val timelinePlacement: YoutubeTimelinePlacement = YoutubeTimelinePlacement.ABOVE_CONTROLS,
    val centerButtonBackdropFilter: CssFilter? = null,
    val centerButtonFillColor: RgbaColor = OverlayControlButton.DEFAULT_CENTER_FILL_COLOR,
    val centerButtonBorderColor: RgbaColor = OverlayControlButton.DEFAULT_CIRCLE_BORDER_COLOR,
    val showPlayPause: Boolean = true,
    val showPrevious: Boolean = false,
    val showNext: Boolean = true,
    val showStop: Boolean = false,
    val showRewind: Boolean = false,
    val showFastForward: Boolean = false,
    val showFullscreen: Boolean = true,
    val showCenterTransport: Boolean = false,
    val showCenterPlayPause: Boolean = true,
    val showCenterRewindFastForward: Boolean = false,
    val showCenterPreviousNext: Boolean = false,
    val showCenterStop: Boolean = false,
    val skipDeltaSeconds: Double = 10.0,
    val showTopBar: Boolean = true,
    val showTimePill: Boolean = true
) {
    companion object {
        val DEFAULT_TOP_SCRIM_GRADIENT: Gradient = LinearGradient(
            180f,
            RgbaColor(0, 0, 0, 225),
            RgbaColor(0, 0, 0, 0)
        )

        val DEFAULT_BOTTOM_SCRIM_GRADIENT: Gradient = LinearGradient(
            180f,
            RgbaColor(0, 0, 0, 0),
            RgbaColor(0, 0, 0, 230)
        )

        val DEFAULT_AUTO_HIDE_SPEC: AutoHideSpec = AutoHideSpec(
            delayMs = 5000,
            fadeInMs = 220,
            fadeOutMs = 220,
            hideImmediatelyOnPointerLeave = false,
            instantHideOnTap = false,
            hideCursorWhenOverlayHidden = true
        )
    }
}
