package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter

/**
 * Declarative policy controlling visual & structural differences between
 * [HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT] and [HybridOverlayLayoutMode.MEDIUM_LARGE]
 * in [HybridOverlayDifferenceController].
 */
data class HybridOverlayDifferenceSpec(
    val breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    val compactWidthBreakpointDp: Int = breakpointSpec.compactDrawerBreakpointDp,
    val compactOnMobileLandscape: Boolean = true,
    val compactTimelinePlacement: YoutubeTimelinePlacement = YoutubeTimelinePlacement.BELOW_CONTROLS,
    val mediumLargeTimelinePlacement: YoutubeTimelinePlacement = YoutubeTimelinePlacement.ABOVE_CONTROLS,
    val showCenterTransportInCompact: Boolean = true,
    val showCenterRewindFastForwardInCompact: Boolean = true,
    val showCenterPreviousNextInCompact: Boolean = false,
    val showCenterStopInCompact: Boolean = false,
    val hideDuplicateBottomTransportInCompact: Boolean = true,
    val showCenterTransportInMediumLarge: Boolean = false,
    val hideCenterSecondaryInMediumLarge: Boolean = true,
    val compactCenterBackdropFilter: CssFilter = OverlayControlButton.DEFAULT_FROSTED_BACKDROP_FILTER,
    val compactCenterFillColor: RgbaColor = OverlayControlButton.DEFAULT_FROSTED_FILL_COLOR,
    val compactCenterBorderColor: RgbaColor = OverlayControlButton.DEFAULT_FROSTED_BORDER_COLOR,
    val mediumLargeCenterBackdropFilter: CssFilter? = null,
    val mediumLargeCenterFillColor: RgbaColor = OverlayControlButton.DEFAULT_CENTER_FILL_COLOR,
    val mediumLargeCenterBorderColor: RgbaColor = OverlayControlButton.DEFAULT_CIRCLE_BORDER_COLOR
)
