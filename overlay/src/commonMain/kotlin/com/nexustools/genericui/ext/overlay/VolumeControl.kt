package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import kotlin.math.roundToInt

/**
 * Mute toggle button + hover-revealed (or inline) `76px` volume slider (`0..100`),
 * invoking [OverlayActions.onMuteToggle] and [OverlayActions.onVolumeChange].
 */
open class VolumeControl(
    override val elementId: String = "volume-control",
    override val slot: OverlaySlot = OverlaySlot.BOTTOM_BAR,
    revealSliderOnHover: Boolean = true,
    autoHideController: AutoHideController? = null
) : HStack(gap = SpacingScale.S1), OverlayElement {

    private var disposedState: Boolean = false
    private var currentState: OverlayState = OverlayState()
    private var currentActions: OverlayActions = OverlayActions()

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var autoHideController: AutoHideController? = autoHideController
        set(value) {
            field = value
            muteButton.autoHideController = value
        }

    var revealSliderOnHover: Boolean = revealSliderOnHover
        set(value) {
            field = value
            volumeSlider.isVisible = !value || isHovered
        }

    val muteButton: OverlayIconButton = OverlayIconButton(
        elementId = "$elementId-mute",
        symbol = SYMBOL_VOLUME_HIGH,
        tooltip = "Mute",
        slot = slot,
        circleSizeDp = null,
        autoHideController = autoHideController
    ) {
        currentActions.onMuteToggle?.invoke()
    }

    val volumeSlider: Slider = Slider(
        value = 100.0,
        min = 0.0,
        max = 100.0,
        step = 1.0
    ).apply {
        widthPx = VOLUME_SLIDER_WIDTH_PX
        isVisible = !revealSliderOnHover
    }

    init {
        htmlTag = "genericui:overlay-volume-control"
        addClass("genericui-overlay-volume-control")
        setAlignItems(AlignItems.CENTER)

        addChild(muteButton)
        addChild(volumeSlider)

        volumeSlider.onValueChanged = { newValue ->
            val clamped = newValue.roundToInt().coerceIn(0, 100)
            autoHideController?.notifyUserActivity()
            currentActions.onVolumeChange?.invoke(clamped)
        }

        onPointerEnter = {
            isHovered = true
            if (this.revealSliderOnHover) {
                volumeSlider.isVisible = true
            }
        }
        onPointerLeave = {
            isHovered = false
            if (this.revealSliderOnHover) {
                volumeSlider.isVisible = false
            }
        }
    }

    fun setVolumePercent(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        volumeSlider.value = clamped.toDouble()
        autoHideController?.notifyUserActivity()
        currentActions.onVolumeChange?.invoke(clamped)
    }

    fun toggleMute() {
        autoHideController?.notifyUserActivity()
        currentActions.onMuteToggle?.invoke()
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentState = state
        currentActions = actions

        val effectiveVolume = if (state.isMuted) 0 else state.volumePercent.coerceIn(0, 100)
        volumeSlider.value = effectiveVolume.toDouble()
        muteButton.symbol = when {
            state.isMuted || effectiveVolume == 0 -> SYMBOL_VOLUME_MUTED
            effectiveVolume < 50 -> SYMBOL_VOLUME_LOW
            else -> SYMBOL_VOLUME_HIGH
        }
        muteButton.tooltipText = if (state.isMuted) "Unmute" else "Mute"
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<HStack>.dispose()
    }

    companion object {
        const val VOLUME_SLIDER_WIDTH_PX: Int = 76
        const val SYMBOL_VOLUME_HIGH: String = "🔊"
        const val SYMBOL_VOLUME_LOW: String = "🔉"
        const val SYMBOL_VOLUME_MUTED: String = "🔇"
    }
}
