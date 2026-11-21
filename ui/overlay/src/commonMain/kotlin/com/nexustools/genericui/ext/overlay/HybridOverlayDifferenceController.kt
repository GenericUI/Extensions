package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * Coordinates responsive differences on [YoutubePlayerOverlay] between
 * [HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT] and [HybridOverlayLayoutMode.MEDIUM_LARGE]
 * using `:common`'s [HybridBreakpointSpec], container dimensions, and active [BackendTarget].
 *
 * - In [HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT]: switches [YoutubePlayerOverlay] to
 *   [YoutubeTimelinePlacement.BELOW_CONTROLS], reveals the center transport cluster
 *   (`[centerRewindButton (56x56), centerPlayPauseButton (64x64), centerFastForwardButton (56x56)]`),
 *   styles those center buttons with hardware `backdropFilter = CssFilter(blur = CssLength.px(16f), saturate = 1.2f)`
 *   + frosted glass fill (`rgba(20, 24, 32, 0.38)`) + subtle border (`rgba(255, 255, 255, 0.22)`),
 *   and hides duplicate bottom transport buttons per [spec].
 * - In [HybridOverlayLayoutMode.MEDIUM_LARGE]: restores [YoutubeTimelinePlacement.ABOVE_CONTROLS],
 *   shows the bottom bar transport buttons per `overlay.config`, and resets center transport styling.
 */
class HybridOverlayDifferenceController(
    val overlay: YoutubePlayerOverlay,
    var spec: HybridOverlayDifferenceSpec = HybridOverlayDifferenceSpec(),
    initialMode: HybridOverlayLayoutMode? = null,
    val forcedMode: HybridOverlayLayoutMode? = null,
    bindToOverlayResize: Boolean = true,
    var onLayoutModeChanged: ((HybridOverlayLayoutMode) -> Unit)? = null
) {

    var currentMode: HybridOverlayLayoutMode = forcedMode ?: initialMode ?: resolveInitialMode()
        private set

    val isCompactMode: Boolean
        get() = currentMode == HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT

    init {
        applyMode(currentMode, force = true)
        if (bindToOverlayResize) {
            overlay.differenceControllerResizeHook = { widthPx, heightPx ->
                if (forcedMode == null && widthPx > 0 && heightPx > 0) {
                    updateForSize(widthPx = widthPx, heightPx = heightPx)
                }
            }
        }
    }

    fun evaluateMode(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp),
        backendTarget: BackendTarget? = GenericUI.activeFactory?.backendTarget
    ): HybridOverlayLayoutMode {
        forcedMode?.let { return it }
        if (backendTarget != null && backendTarget.isMobile) {
            if (spec.breakpointSpec.isSmallScreenMobile(smallestScreenWidthDp)) {
                val isPortrait = viewportWidthDp <= viewportHeightDp
                if (spec.compactOnMobileLandscape || isPortrait || viewportWidthDp < spec.compactWidthBreakpointDp) {
                    return HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT
                }
            }
            return if (viewportWidthDp < spec.compactWidthBreakpointDp) {
                HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT
            } else {
                HybridOverlayLayoutMode.MEDIUM_LARGE
            }
        }
        return if (viewportWidthDp < spec.compactWidthBreakpointDp) {
            HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT
        } else {
            HybridOverlayLayoutMode.MEDIUM_LARGE
        }
    }

    fun updateForViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp),
        backendTarget: BackendTarget? = GenericUI.activeFactory?.backendTarget
    ): HybridOverlayLayoutMode {
        val next = evaluateMode(
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            smallestScreenWidthDp = smallestScreenWidthDp,
            backendTarget = backendTarget
        )
        setMode(next)
        return next
    }

    fun updateForSize(
        widthPx: Int,
        heightPx: Int,
        densityScale: Float = 1.0f,
        smallestScreenWidthDp: Int? = null,
        backendTarget: BackendTarget? = GenericUI.activeFactory?.backendTarget
    ): HybridOverlayLayoutMode {
        if (widthPx <= 0 || heightPx <= 0) {
            return currentMode
        }
        val widthDp = spec.breakpointSpec.toDp(widthPx, densityScale)
        val heightDp = spec.breakpointSpec.toDp(heightPx, densityScale)
        val effectiveSmallest = smallestScreenWidthDp ?: min(widthDp, heightDp)
        return updateForViewport(
            viewportWidthDp = widthDp,
            viewportHeightDp = heightDp,
            smallestScreenWidthDp = effectiveSmallest,
            backendTarget = backendTarget
        )
    }

    fun updateForWindow(
        config: WindowConfiguration,
        backendTarget: BackendTarget? = GenericUI.activeFactory?.backendTarget
    ): HybridOverlayLayoutMode = updateForSize(
        widthPx = config.widthPx,
        heightPx = config.heightPx,
        densityScale = config.densityScale,
        smallestScreenWidthDp = config.smallestScreenWidthDp,
        backendTarget = backendTarget
    )

    fun setMode(mode: HybridOverlayLayoutMode, force: Boolean = false) {
        val resolved = forcedMode ?: mode
        if (!force && resolved == currentMode) {
            return
        }
        applyMode(resolved, force = true)
    }

    fun detach() {
        overlay.differenceControllerResizeHook = null
    }

    private fun resolveInitialMode(): HybridOverlayLayoutMode {
        if (overlay.targetWidthPx > 0 && overlay.targetHeightPx > 0) {
            return evaluateMode(
                viewportWidthDp = overlay.targetWidthPx,
                viewportHeightDp = overlay.targetHeightPx
            )
        }
        val backend = GenericUI.activeFactory?.backendTarget
        if (backend != null && backend.isMobile && backend != BackendTarget.IPadOS) {
            return HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT
        }
        return HybridOverlayLayoutMode.MEDIUM_LARGE
    }

    private fun applyMode(mode: HybridOverlayLayoutMode, force: Boolean) {
        if (!force && currentMode == mode) return
        currentMode = mode
        overlay.setAttribute("data-overlay-layout-mode", mode.name)

        when (mode) {
            HybridOverlayLayoutMode.SMALL_MOBILE_PORTRAIT -> {
                overlay.setTimelinePlacement(spec.compactTimelinePlacement)
                overlay.centerTransportRow.isVisible = spec.showCenterTransportInCompact
                overlay.centerRewindButton.isVisible = spec.showCenterRewindFastForwardInCompact
                overlay.centerFastForwardButton.isVisible = spec.showCenterRewindFastForwardInCompact
                overlay.centerPlayPauseButton.isVisible = !overlay.isLoading
                overlay.centerPreviousButton.isVisible = spec.showCenterPreviousNextInCompact
                overlay.centerNextButton.isVisible = spec.showCenterPreviousNextInCompact
                overlay.centerStopButton.isVisible = spec.showCenterStopInCompact

                overlay.setCenterTransportBackdropFilter(
                    filter = spec.compactCenterBackdropFilter,
                    fillColor = spec.compactCenterFillColor,
                    borderColor = spec.compactCenterBorderColor
                )

                if (spec.hideDuplicateBottomTransportInCompact) {
                    overlay.playPauseButton.isVisible = false
                    overlay.rewindButton.isVisible = false
                    overlay.fastForwardButton.isVisible = false
                } else {
                    overlay.playPauseButton.isVisible = overlay.config.showPlayPause
                    overlay.rewindButton.isVisible = overlay.config.showRewind
                    overlay.fastForwardButton.isVisible = overlay.config.showFastForward
                }
            }
            HybridOverlayLayoutMode.MEDIUM_LARGE -> {
                overlay.setTimelinePlacement(spec.mediumLargeTimelinePlacement)
                overlay.playPauseButton.isVisible = overlay.config.showPlayPause
                overlay.rewindButton.isVisible = overlay.config.showRewind
                overlay.fastForwardButton.isVisible = overlay.config.showFastForward
                overlay.previousButton.isVisible = overlay.config.showPrevious
                overlay.nextButton.isVisible = overlay.config.showNext
                overlay.stopButton.isVisible = overlay.config.showStop

                overlay.centerTransportRow.isVisible = spec.showCenterTransportInMediumLarge
                overlay.centerRewindButton.isVisible =
                    !spec.hideCenterSecondaryInMediumLarge && overlay.config.showCenterRewindFastForward
                overlay.centerFastForwardButton.isVisible =
                    !spec.hideCenterSecondaryInMediumLarge && overlay.config.showCenterRewindFastForward
                overlay.centerPreviousButton.isVisible =
                    !spec.hideCenterSecondaryInMediumLarge && overlay.config.showCenterPreviousNext
                overlay.centerNextButton.isVisible =
                    !spec.hideCenterSecondaryInMediumLarge && overlay.config.showCenterPreviousNext
                overlay.centerStopButton.isVisible =
                    !spec.hideCenterSecondaryInMediumLarge && overlay.config.showCenterStop
                overlay.centerPlayPauseButton.isVisible =
                    !overlay.isLoading && overlay.config.showCenterPlayPause

                overlay.setCenterTransportBackdropFilter(
                    filter = spec.mediumLargeCenterBackdropFilter,
                    fillColor = spec.mediumLargeCenterFillColor,
                    borderColor = spec.mediumLargeCenterBorderColor
                )
            }
        }

        onLayoutModeChanged?.invoke(mode)
    }
}
