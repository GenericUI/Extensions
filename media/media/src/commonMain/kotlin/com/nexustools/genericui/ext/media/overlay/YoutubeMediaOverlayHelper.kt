package com.nexustools.genericui.ext.media.overlay

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.media.ExpandingVolumeSlider
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.overlay.OverlayActions
import com.nexustools.genericui.ext.overlay.OverlayElement
import com.nexustools.genericui.ext.overlay.OverlayState
import com.nexustools.genericui.ext.overlay.OverlayTimeFormatter
import com.nexustools.genericui.ext.overlay.YoutubePlayerOverlay
import kotlin.math.roundToInt

/**
 * Attaches `:extensions:media:media`'s [SeekBar] (with hover timestamp preview via
 * [YoutubePlayerOverlay.showTimelineHover]) and optional 3-wave [ExpandingVolumeSlider]
 * directly into a [YoutubePlayerOverlay]'s `timelineContainer` and `leftAccessoryContainer`.
 *
 * Keeps `tooltipText` `null` on all media controls by default and automatically stays synchronized
 * with [YoutubePlayerOverlay.state] updates via a passive [OverlayElement] listener. Visual
 * layout and theme colors come purely from `media/style.scss` and consumer-compiled `theme.scss`.
 */
class YoutubeMediaOverlayHelper(
    val overlay: YoutubePlayerOverlay,
    val includeVolumeSlider: Boolean = true,
    expandedVolumeWidthPx: Int = 92
) : Disposable {

    private var disposedState: Boolean = false
    private var isInternalSync: Boolean = false
    private var currentDurationSec: Double = overlay.state.durationSec.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    private var lastSyncedVolumePercent: Int = Int.MIN_VALUE
    private var lastSyncedMuted: Boolean? = null

    override val isDisposed: Boolean
        get() = disposedState

    val seekBar: SeekBar = SeekBar(
        value = 0.0,
        min = 0.0,
        max = 100.0,
        step = 0.25
    ).apply {
        addClass("youtube-media-overlay-seekbar")
    }

    val volumeSlider: ExpandingVolumeSlider? = if (includeVolumeSlider) {
        ExpandingVolumeSlider(
            value = overlay.state.volumePercent.toDouble(),
            muted = overlay.state.isMuted,
            expandedSliderWidthPx = expandedVolumeWidthPx
        )
    } else {
        null
    }

    private val stateSyncElement: OverlayElement = object : OverlayElement(
        component = Container("genericui-media-overlay-sync-bridge").apply {
            isVisible = false
        },
        constraint = LayerConstraint.float(zIndex = -1, passThroughPointerEvents = true),
        elementId = "youtube-media-overlay-helper-sync"
    ) {
        override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
            syncState(state)
        }
    }

    init {
        isolateSubtree(seekBar)
        volumeSlider?.let { vol ->
            isolateSubtree(vol)
        }
        overlay.timelineContainer.addChild(seekBar)
        volumeSlider?.let { overlay.leftAccessoryContainer.addChild(it) }

        seekBar.onSeekStart = {
            if (!disposedState) {
                overlay.notifyUserActivity()
            }
        }
        seekBar.onValueChanged = { targetSec ->
            if (!isInternalSync && !disposedState) {
                overlay.notifyUserActivity()
                if (seekBar.isScrubbing && currentDurationSec > 0.0) {
                    val hoverLabel = OverlayTimeFormatter.formatPosition(targetSec, currentDurationSec)
                    overlay.showTimelineHover(seekBar.playedFraction.toDouble(), hoverLabel)
                    overlay.setTimeText(
                        OverlayTimeFormatter.formatPositionAndDuration(targetSec, currentDurationSec)
                    )
                }
                overlay.actions.onSeekToSec?.invoke(targetSec)
            }
        }
        seekBar.onSeekCommit = { targetSec ->
            if (!disposedState) {
                overlay.notifyUserActivity()
                overlay.hideTimelineHover()
                overlay.actions.onSeekToSec?.invoke(targetSec)
            }
        }
        seekBar.onTrackHoverMove = { fraction, _ ->
            if (!disposedState) {
                val dur = currentDurationSec
                if (dur > 0.0) {
                    val clamped = fraction.coerceIn(0.0, 1.0)
                    val hoverLabel = OverlayTimeFormatter.formatPosition(clamped * dur, dur)
                    overlay.showTimelineHover(clamped, hoverLabel)
                } else {
                    overlay.hideTimelineHover()
                }
            }
        }
        seekBar.onTrackHoverExit = {
            if (!disposedState) {
                overlay.hideTimelineHover()
            }
        }

        volumeSlider?.let { vol ->
            vol.onValueChanged = { pct ->
                if (!isInternalSync && !disposedState) {
                    overlay.notifyUserActivity()
                    overlay.actions.onVolumeChange?.invoke(pct.roundToInt().coerceIn(0, 100))
                }
            }
            vol.onMuteToggle = {
                if (!isInternalSync && !disposedState) {
                    overlay.notifyUserActivity()
                    overlay.actions.onMuteToggle?.invoke()
                }
            }
            vol.onExpandedChanged = {
                if (!disposedState) {
                    overlay.notifyUserActivity()
                }
            }
        }

        overlay.addElement(stateSyncElement, stateSyncElement.constraint)
        syncState(overlay.state)
    }

    private fun isolateSubtree(component: AbstractComponent) {
        overlay.inputRouter.isolateInteractiveComponent(component)
        if (component is Composite) {
            for (child in component.internalCompositeChildren()) {
                isolateSubtree(child)
            }
        } else if (component is AbstractContainer) {
            for (child in component.children) {
                isolateSubtree(child)
            }
        }
    }

    fun syncState(state: OverlayState) {
        if (disposedState) return
        val dur = if (state.durationSec.isFinite() && state.durationSec > 0.0) state.durationSec else 0.0
        currentDurationSec = dur
        isInternalSync = true
        try {
            if (dur > 0.0) {
                val targetValue = if (seekBar.isScrubbing) seekBar.value else state.positionSec.coerceIn(0.0, dur)
                seekBar.updateProgressAndBuffer(
                    currentValue = targetValue,
                    maxValue = dur,
                    bufferedEndValue = state.bufferedEndSec.coerceIn(0.0, dur),
                    ranges = state.bufferedRanges.map { it.startSec to it.endSec }
                )
            } else {
                seekBar.updateProgressAndBuffer(
                    currentValue = 0.0,
                    maxValue = 100.0,
                    bufferedEndValue = 0.0,
                    ranges = emptyList()
                )
            }
            volumeSlider?.let { vol ->
                if (!vol.isScrubbing && (state.volumePercent != lastSyncedVolumePercent || state.isMuted != lastSyncedMuted)) {
                    lastSyncedVolumePercent = state.volumePercent
                    lastSyncedMuted = state.isMuted
                    vol.updateVolumeState(state.volumePercent.toDouble(), state.isMuted)
                }
            }
        } finally {
            isInternalSync = false
        }
    }

    fun updateVolumeState(volumePercent: Int, muted: Boolean) {
        if (disposedState) return
        if (volumePercent == lastSyncedVolumePercent && muted == lastSyncedMuted) return
        lastSyncedVolumePercent = volumePercent
        lastSyncedMuted = muted
        isInternalSync = true
        try {
            volumeSlider?.updateVolumeState(volumePercent.toDouble(), muted)
        } finally {
            isInternalSync = false
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        overlay.removeElement(stateSyncElement)
        overlay.timelineContainer.removeChild(seekBar)
        volumeSlider?.let { vol ->
            overlay.leftAccessoryContainer.removeChild(vol)
            vol.dispose()
        }
    }
}
