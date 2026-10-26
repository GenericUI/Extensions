package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * System + Web seek bar (`6px` track that expands to `10px` on hover, colored with the app's
 * primary `themeColor` by default):
 * - Disabled when `state.durationSec <= 0.0`.
 * - Renders buffered range bar ([bufferedBar]) and playback progress bar ([progressBar]).
 * - Displays a hover time tooltip ([hoverTooltipLabel]) formatted via [OverlayTimeFormatter.formatPosition].
 * - Supports both **drag-with-live-preview** (pinning [AutoHideController] with reason `"seeking"`,
 *   updating preview time, and committing `actions.onSeekToSec(clampedSec)` on pointer release where
 *   `clampedSec = target.coerceIn(0.0, (durationSec - 0.5).coerceAtLeast(0.0))`) and **plain click-to-seek**.
 */
open class TrackSeekBar(
    override val elementId: String = "track-seek-bar",
    override val slot: OverlaySlot = OverlaySlot.BOTTOM_SEEK,
    themeColorHex: String? = null,
    var autoHideController: AutoHideController? = null,
    var onScrubPreviewChanged: ((previewSec: Double, formattedPreview: String) -> Unit)? = null
) : Container("genericui-overlay-track-seek-bar"), OverlayElement {

    private var disposedState: Boolean = false
    private var currentState: OverlayState = OverlayState()
    private var currentActions: OverlayActions = OverlayActions()

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var themeColorHex: String? = themeColorHex
        set(value) {
            field = value
            applyThemeColor()
        }

    val effectiveThemeColorHex: String
        get() = themeColorHex?.takeIf { it.isNotBlank() }
            ?: OverlayExtension.effectiveDefaultThemeColorHex()

    var isScrubbing: Boolean = false
        private set

    var previewPositionSec: Double = 0.0
        private set

    var hoverPositionSec: Double = 0.0
        private set

    val previewTimeText: String
        get() = OverlayTimeFormatter.formatPosition(previewPositionSec, currentState.durationSec)

    val hoverTimeText: String
        get() = OverlayTimeFormatter.formatPosition(hoverPositionSec, currentState.durationSec)

    val displayedPositionSec: Double
        get() = if (isScrubbing) previewPositionSec else currentState.positionSec

    val displayedProgressFraction: Double
        get() = if (currentState.durationSec > 0.0) {
            (displayedPositionSec / currentState.durationSec).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

    val bufferedFraction: Double
        get() = currentState.bufferedFraction

    val trackBackground: Container = Container("genericui-overlay-track-bg").apply {
        positionMode = PositionMode.RELATIVE
        customBackgroundColor = RgbaColor(255, 255, 255, 64)
        borderRadiusPx = 3f
    }

    val bufferedBar: Container = Container("genericui-overlay-track-buffered").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "0px"
        topOffset = "0px"
        customBackgroundColor = RgbaColor(255, 255, 255, 115)
        borderRadiusPx = 3f
    }

    val progressBar: Container = Container("genericui-overlay-track-progress").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "0px"
        topOffset = "0px"
        borderRadiusPx = 3f
    }

    val hoverTooltipLabel: Label = Label("0:00", "span", "genericui-overlay-track-tooltip").apply {
        customBackgroundColor = RgbaColor(0, 0, 0, 0xCC)
        textColor = RgbaColor.WHITE
        borderRadiusPx = 4f
        setPadding(EdgeInsets.symmetric(vertical = 2, horizontal = 6))
        setStyleProperty("font-size", "11px")
        isVisible = false
    }

    init {
        htmlTag = "genericui:overlay-track-seek-bar"
        positionMode = PositionMode.RELATIVE
        heightPx = DEFAULT_TRACK_HEIGHT_PX
        trackBackground.heightPx = DEFAULT_TRACK_HEIGHT_PX
        bufferedBar.heightPx = DEFAULT_TRACK_HEIGHT_PX
        progressBar.heightPx = DEFAULT_TRACK_HEIGHT_PX
        isEnabled = false
        cursor = "pointer"

        trackBackground.addChild(bufferedBar)
        trackBackground.addChild(progressBar)
        addChild(hoverTooltipLabel)
        addChild(trackBackground)

        applyThemeColor()
        syncVisualBars()
        bindPointerHandlers()
    }

    private fun applyThemeColor() {
        val resolvedColor = GradientScrim.parseOverlayColor(effectiveThemeColorHex) ?: RgbaColor.PRIMARY_BLUE
        progressBar.customBackgroundColor = resolvedColor
        tint = resolvedColor
    }

    private fun updateTrackHeightForInteractiveState() {
        val targetHeight = if ((isHovered || isScrubbing) && isEnabled) {
            HOVER_TRACK_HEIGHT_PX
        } else {
            DEFAULT_TRACK_HEIGHT_PX
        }
        heightPx = targetHeight
        trackBackground.heightPx = targetHeight
        bufferedBar.heightPx = targetHeight
        progressBar.heightPx = targetHeight
    }

    private fun syncVisualBars() {
        val bufPct = (bufferedFraction * 100.0).coerceIn(0.0, 100.0)
        val progPct = (displayedProgressFraction * 100.0).coerceIn(0.0, 100.0)
        bufferedBar.setStyleProperty("width", "$bufPct%")
        progressBar.setStyleProperty("width", "$progPct%")
    }

    fun clampSeekTarget(targetSec: Double, durationSec: Double = currentState.durationSec): Double {
        if (!targetSec.isFinite() || durationSec <= 0.0 || !durationSec.isFinite()) return 0.0
        val maxAllowed = (durationSec - SEEK_END_MARGIN_SEC).coerceAtLeast(0.0)
        return targetSec.coerceIn(0.0, maxAllowed)
    }

    fun updateHoverAtSeconds(targetSec: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        hoverPositionSec = targetSec.coerceIn(0.0, currentState.durationSec)
        hoverTooltipLabel.text = hoverTimeText
        hoverTooltipLabel.isVisible = true
        updateTrackHeightForInteractiveState()
    }

    fun updateHoverAtFraction(fraction: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        val sec = fraction.coerceIn(0.0, 1.0) * currentState.durationSec
        updateHoverAtSeconds(sec)
    }

    fun beginScrubAtSeconds(targetSec: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        isScrubbing = true
        autoHideController?.setPinned(PIN_REASON_SEEKING, true)
        updateTrackHeightForInteractiveState()
        updateScrubAtSeconds(targetSec)
    }

    fun beginScrubAtFraction(fraction: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        beginScrubAtSeconds(fraction.coerceIn(0.0, 1.0) * currentState.durationSec)
    }

    fun updateScrubAtSeconds(targetSec: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        if (!isScrubbing) {
            beginScrubAtSeconds(targetSec)
            return
        }
        val clamped = clampSeekTarget(targetSec)
        previewPositionSec = clamped
        hoverPositionSec = clamped
        hoverTooltipLabel.text = previewTimeText
        hoverTooltipLabel.isVisible = true
        syncVisualBars()
        onScrubPreviewChanged?.invoke(clamped, previewTimeText)
    }

    fun updateScrubAtFraction(fraction: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        updateScrubAtSeconds(fraction.coerceIn(0.0, 1.0) * currentState.durationSec)
    }

    fun endScrub(commit: Boolean = true) {
        if (!isScrubbing) return
        val committedSec = clampSeekTarget(previewPositionSec)
        isScrubbing = false
        autoHideController?.setPinned(PIN_REASON_SEEKING, false)
        updateTrackHeightForInteractiveState()
        if (!isHovered) {
            hoverTooltipLabel.isVisible = false
        }
        if (commit && isEnabled && currentState.durationSec > 0.0) {
            currentActions.onSeekToSec?.invoke(committedSec)
        }
        syncVisualBars()
    }

    fun seekByClickAtSeconds(targetSec: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        val clamped = clampSeekTarget(targetSec)
        previewPositionSec = clamped
        autoHideController?.notifyUserActivity()
        currentActions.onSeekToSec?.invoke(clamped)
        syncVisualBars()
    }

    fun seekByClickAtFraction(fraction: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        seekByClickAtSeconds(fraction.coerceIn(0.0, 1.0) * currentState.durationSec)
    }

    private fun resolveFractionFromPointerX(pointerX: Float): Double {
        val width = (getBounds().width.takeIf { it > 0 } ?: widthPx ?: 200).toFloat()
        return (pointerX / width).toDouble().coerceIn(0.0, 1.0)
    }

    private fun bindPointerHandlers() {
        onPointerEnter = { event ->
            isHovered = true
            updateTrackHeightForInteractiveState()
            if (isEnabled && currentState.durationSec > 0.0) {
                updateHoverAtFraction(resolveFractionFromPointerX(event.x))
            }
        }
        onPointerMove = { event ->
            if (isEnabled && currentState.durationSec > 0.0) {
                val fraction = resolveFractionFromPointerX(event.x)
                if (isScrubbing) {
                    updateScrubAtFraction(fraction)
                } else {
                    updateHoverAtFraction(fraction)
                }
            }
        }
        onPointerLeave = {
            isHovered = false
            if (!isScrubbing) {
                hoverTooltipLabel.isVisible = false
            }
            updateTrackHeightForInteractiveState()
        }
        onPointerDown = { event ->
            if (isEnabled && currentState.durationSec > 0.0) {
                beginScrubAtFraction(resolveFractionFromPointerX(event.x))
            }
        }
        onPointerUp = { event ->
            if (isScrubbing) {
                updateScrubAtFraction(resolveFractionFromPointerX(event.x))
                endScrub(commit = true)
            }
        }
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentState = state
        currentActions = actions
        val hasValidDuration = state.durationSec.isFinite() && state.durationSec > 0.0
        isEnabled = hasValidDuration
        if (!hasValidDuration && isScrubbing) {
            isScrubbing = false
            autoHideController?.setPinned(PIN_REASON_SEEKING, false)
            hoverTooltipLabel.isVisible = false
        }
        if (!isScrubbing) {
            previewPositionSec = state.positionSec.coerceAtLeast(0.0)
        }
        updateTrackHeightForInteractiveState()
        syncVisualBars()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        if (isScrubbing) {
            isScrubbing = false
            autoHideController?.setPinned(PIN_REASON_SEEKING, false)
        }
        super<Container>.dispose()
    }

    companion object {
        const val DEFAULT_TRACK_HEIGHT_PX: Int = 6
        const val HOVER_TRACK_HEIGHT_PX: Int = 10
        const val SEEK_END_MARGIN_SEC: Double = 0.5
        const val PIN_REASON_SEEKING: String = "seeking"
    }
}
