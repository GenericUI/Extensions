package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Generic center transport cluster (used by [MobileOverlayPreset]):
 * - Optional `-skip` circle button (`56dp`, calling `actions.onSkipDeltaSec(-skipSeconds)`)
 * - Center `Play/Pause` circle button (`64dp`, automatically swapping to [LoadingIndicator] while `state.isLoading == true`)
 * - Optional `+skip` circle button (`56dp`, calling `actions.onSkipDeltaSec(+skipSeconds)`)
 */
open class CenterTransportCluster(
    override val elementId: String = "center-transport-cluster",
    override val slot: OverlaySlot = OverlaySlot.CENTER,
    skipSeconds: Double = DEFAULT_SKIP_SECONDS,
    showSkipBackward: Boolean = true,
    showSkipForward: Boolean = true,
    autoHideController: AutoHideController? = null
) : HStack(gap = SpacingScale.S4), OverlayElement {

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
            skipBackwardButton.autoHideController = value
            playPauseButton.autoHideController = value
            skipForwardButton.autoHideController = value
        }

    var skipSeconds: Double = skipSeconds
        set(value) {
            field = value.coerceAtLeast(1.0)
            updateSkipButtonLabels()
        }

    var showSkipBackward: Boolean = showSkipBackward
        set(value) {
            field = value
            skipBackwardButton.isVisible = value
        }

    var showSkipForward: Boolean = showSkipForward
        set(value) {
            field = value
            skipForwardButton.isVisible = value
        }

    val leadingControlsContainer: HStack = HStack(gap = SpacingScale.S2).apply {
        setAlignItems(AlignItems.CENTER)
    }

    val skipBackwardButton: OverlayIconButton = OverlayIconButton(
        elementId = "$elementId-skip-back",
        symbol = formatSkipLabel(-skipSeconds),
        tooltip = "Skip backward ${skipSeconds.toInt()}s",
        slot = OverlaySlot.CENTER,
        circleSizeDp = SKIP_BUTTON_SIZE_DP,
        autoHideController = autoHideController
    ) {
        triggerSkip(-this.skipSeconds)
    }

    val playPauseButton: OverlayIconButton = OverlayIconButton(
        elementId = "$elementId-play-pause",
        symbol = SYMBOL_PLAY,
        tooltip = "Play",
        slot = OverlaySlot.CENTER,
        circleSizeDp = PLAY_PAUSE_BUTTON_SIZE_DP,
        autoHideController = autoHideController
    ) {
        currentActions.triggerPlayPause(currentState.isPlaying)
    }

    val loadingIndicator: LoadingIndicator = LoadingIndicator(
        elementId = "$elementId-loading",
        slot = OverlaySlot.CENTER,
        sizeDp = PLAY_PAUSE_BUTTON_SIZE_DP
    )

    val skipForwardButton: OverlayIconButton = OverlayIconButton(
        elementId = "$elementId-skip-forward",
        symbol = formatSkipLabel(skipSeconds),
        tooltip = "Skip forward ${skipSeconds.toInt()}s",
        slot = OverlaySlot.CENTER,
        circleSizeDp = SKIP_BUTTON_SIZE_DP,
        autoHideController = autoHideController
    ) {
        triggerSkip(this.skipSeconds)
    }

    val trailingControlsContainer: HStack = HStack(gap = SpacingScale.S2).apply {
        setAlignItems(AlignItems.CENTER)
    }

    init {
        htmlTag = "genericui:overlay-center-transport"
        addClass("genericui-overlay-center-transport")
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.CENTER)

        skipBackwardButton.isVisible = showSkipBackward
        skipForwardButton.isVisible = showSkipForward

        addChild(leadingControlsContainer)
        addChild(skipBackwardButton)
        addChild(playPauseButton)
        addChild(loadingIndicator)
        addChild(skipForwardButton)
        addChild(trailingControlsContainer)
    }

    fun addLeadingControl(control: AbstractComponent) {
        leadingControlsContainer.addChild(control)
    }

    fun addTrailingControl(control: AbstractComponent) {
        trailingControlsContainer.addChild(control)
    }

    private fun triggerSkip(deltaSec: Double) {
        val onSkip = currentActions.onSkipDeltaSec
        if (onSkip != null) {
            onSkip.invoke(deltaSec)
        } else {
            val rawTarget = currentState.positionSec + deltaSec
            val clamped = if (currentState.durationSec > 0.0) {
                rawTarget.coerceIn(0.0, (currentState.durationSec - 0.5).coerceAtLeast(0.0))
            } else {
                rawTarget.coerceAtLeast(0.0)
            }
            currentActions.onSeekToSec?.invoke(clamped)
        }
    }

    private fun updateSkipButtonLabels() {
        val secs = skipSeconds.toInt()
        skipBackwardButton.symbol = formatSkipLabel(-skipSeconds)
        skipBackwardButton.tooltipText = "Skip backward ${secs}s"
        skipForwardButton.symbol = formatSkipLabel(skipSeconds)
        skipForwardButton.tooltipText = "Skip forward ${secs}s"
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentState = state
        currentActions = actions

        loadingIndicator.onStateChanged(state, actions)
        playPauseButton.isVisible = !state.isLoading
        playPauseButton.symbol = if (state.isPlaying) SYMBOL_PAUSE else SYMBOL_PLAY
        playPauseButton.tooltipText = if (state.isPlaying) "Pause" else "Play"

        skipBackwardButton.isVisible = showSkipBackward
        skipForwardButton.isVisible = showSkipForward

        for (child in leadingControlsContainer.children + trailingControlsContainer.children) {
            (child as? OverlayElement)?.onStateChanged(state, actions)
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<HStack>.dispose()
    }

    companion object {
        const val DEFAULT_SKIP_SECONDS: Double = 30.0
        const val SKIP_BUTTON_SIZE_DP: Int = 56
        const val PLAY_PAUSE_BUTTON_SIZE_DP: Int = 64
        const val SYMBOL_PLAY: String = "▶"
        const val SYMBOL_PAUSE: String = "⏸"

        private fun formatSkipLabel(deltaSec: Double): String {
            val absInt = kotlin.math.abs(deltaSec.toInt())
            return if (deltaSec < 0) "−${absInt}s" else "+${absInt}s"
        }
    }
}
