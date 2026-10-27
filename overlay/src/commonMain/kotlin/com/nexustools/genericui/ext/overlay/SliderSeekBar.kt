package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Mobile `20dp` slider seek bar colored with the application's primary `themeColor` by default:
 * - Disabled when `state.durationSec <= 0.0`.
 * - Supports drag-with-live-preview (`isScrubbing == true`, pinning [AutoHideController] with reason `"seeking"`,
 *   updating [previewTimeLabel], and committing `actions.onSeekToSec(clampedSec)` on pointer release where
 *   `clampedSec = target.coerceIn(0.0, (durationSec - 0.5).coerceAtLeast(0.0))`).
 */
open class SliderSeekBar(
    override val elementId: String = "slider-seek-bar",
    override val slot: OverlaySlot = OverlaySlot.BOTTOM_SEEK,
    themeColorHex: String? = null,
    var autoHideController: AutoHideController? = null,
    var onScrubPreviewChanged: ((previewSec: Double, formattedTimePair: String) -> Unit)? = null
) : Container("genericui-overlay-slider-seek-bar"), OverlayElement {

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

    val previewTimeText: String
        get() = OverlayTimeFormatter.formatPosition(previewPositionSec, currentState.durationSec)

    val previewTimePairText: String
        get() = OverlayTimeFormatter.formatPositionAndDuration(previewPositionSec, currentState.durationSec)

    val displayedPositionSec: Double
        get() = if (isScrubbing) previewPositionSec else currentState.positionSec

    val slider: Slider = Slider(
        value = 0.0,
        min = 0.0,
        max = 100.0,
        step = 0.1
    ).apply {
        heightPx = SLIDER_HEIGHT_DP
        isEnabled = false
    }

    val previewTimeLabel: Label = Label("0:00 / --:--", "span", "genericui-overlay-slider-preview").apply {
        textColor = RgbaColor.WHITE
        setStyleProperty("font-size", "12px")
        isVisible = false
    }

    init {
        htmlTag = "genericui:overlay-slider-seek-bar"
        heightPx = SLIDER_HEIGHT_DP
        isEnabled = false

        addChild(previewTimeLabel)
        addChild(slider)

        applyThemeColor()

        slider.onPointerDown = {
            if (isEnabled && currentState.durationSec > 0.0) {
                beginScrub(slider.value)
            }
        }
        slider.onValueChanged = { rawValue ->
            if (isEnabled && currentState.durationSec > 0.0) {
                if (isScrubbing) {
                    updateScrub(rawValue)
                } else {
                    seekToSeconds(rawValue)
                }
            }
        }
        slider.onPointerUp = {
            if (isScrubbing) {
                endScrub(commit = true)
            }
        }
    }

    private fun applyThemeColor() {
        val resolvedColor = GradientScrim.parseOverlayColor(effectiveThemeColorHex) ?: RgbaColor.PRIMARY_BLUE
        getStyle().setAccentColor(resolvedColor)
        getStyle().setBackgroundColor(resolvedColor)
        slider.getStyle().setAccentColor(resolvedColor)
        slider.getStyle().setBackgroundColor(resolvedColor)
    }

    fun clampSeekTarget(targetSec: Double, durationSec: Double = currentState.durationSec): Double {
        if (!targetSec.isFinite() || durationSec <= 0.0 || !durationSec.isFinite()) return 0.0
        val maxAllowed = (durationSec - SEEK_END_MARGIN_SEC).coerceAtLeast(0.0)
        return targetSec.coerceIn(0.0, maxAllowed)
    }

    fun beginScrub(initialSec: Double = currentState.positionSec) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        isScrubbing = true
        previewTimeLabel.isVisible = true
        autoHideController?.setPinned(PIN_REASON_SEEKING, true)
        updateScrub(initialSec)
    }

    fun updateScrub(targetSec: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        if (!isScrubbing) {
            beginScrub(targetSec)
            return
        }
        val clamped = clampSeekTarget(targetSec)
        previewPositionSec = clamped
        slider.value = clamped
        previewTimeLabel.text = previewTimePairText
        onScrubPreviewChanged?.invoke(clamped, previewTimePairText)
    }

    fun endScrub(commit: Boolean = true) {
        if (!isScrubbing) return
        val committedSec = clampSeekTarget(previewPositionSec)
        isScrubbing = false
        previewTimeLabel.isVisible = false
        autoHideController?.setPinned(PIN_REASON_SEEKING, false)
        if (commit && isEnabled && currentState.durationSec > 0.0) {
            currentActions.onSeekToSec?.invoke(committedSec)
        }
    }

    fun seekToSeconds(targetSec: Double) {
        if (!isEnabled || currentState.durationSec <= 0.0) return
        val clamped = clampSeekTarget(targetSec)
        previewPositionSec = clamped
        slider.value = clamped
        autoHideController?.notifyUserActivity()
        currentActions.onSeekToSec?.invoke(clamped)
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentState = state
        currentActions = actions
        val hasValidDuration = state.durationSec.isFinite() && state.durationSec > 0.0
        isEnabled = hasValidDuration
        slider.isEnabled = hasValidDuration
        if (hasValidDuration) {
            slider.max = state.durationSec
            if (!isScrubbing) {
                val pos = state.positionSec.coerceIn(0.0, state.durationSec)
                previewPositionSec = pos
                slider.value = pos
                previewTimeLabel.text = state.formattedTimePair
            }
        } else {
            if (isScrubbing) {
                isScrubbing = false
                previewTimeLabel.isVisible = false
                autoHideController?.setPinned(PIN_REASON_SEEKING, false)
            }
            slider.max = 0.0
            slider.value = 0.0
            previewPositionSec = 0.0
            previewTimeLabel.text = state.formattedTimePair
        }
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
        const val SLIDER_HEIGHT_DP: Int = 20
        const val SEEK_END_MARGIN_SEC: Double = 0.5
        const val PIN_REASON_SEEKING: String = "seeking"
    }
}
