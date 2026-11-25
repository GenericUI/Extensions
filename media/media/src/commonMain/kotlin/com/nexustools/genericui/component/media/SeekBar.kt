package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerDragSession
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.abs
import kotlin.math.round

/**
 * Media playback seek bar extending `:common`'s [StyledSlider] (`Composite`) directly and
 * appending only [preloadSegment] (`.seek-bar-preload`) at the end of `compositeChildren`.
 *
 * Layering (`z-index`) and track/fill/knob aesthetics come purely from `style.scss` and
 * consumer-compiled `theme.scss` (`z-index: 1` track, `2` preload with `pointer-events: none`,
 * `3` progress with `pointer-events: none`, `4` knob), with zero custom native seekbar impls
 * and zero static/theme inline Kotlin style setters.
 */
open class SeekBar(
    value: Double = 0.0,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double = 0.25
) : StyledSlider(
    min = min,
    max = max,
    value = value,
    step = step.coerceAtLeast(0.0)
) {

    /**
     * Buffered / preloaded range bar (`z-index: 2; pointer-events: none;`) appended after
     * `StyledSlider`'s `trackButton`, `progressSegment`, and `knobButton`.
     */
    protected val preloadSegment: StyledComponent = StyledComponent(
        classPrefix = PRELOAD_CLASS,
        tagName = StyledComponent.DEFAULT_TAG_NAME
    ).apply {
        addClass(PRELOAD_CLASS)
    }

    private var suppressBoundSeekPropagation: Boolean = false
    private var suppressInternalScrubCallbacks: Boolean = false
    private var suppressCommitOnNextEndScrub: Boolean = false
    private val boundPlayerSeekCallbacks = mutableListOf<(Double) -> Unit>()
    private var activeKnobDragSession: PointerDragSession? = null
    private var inEndKnobDrag: Boolean = false
    private var lastPreloadSyncMin: Double = this.min
    private var lastPreloadSyncMax: Double = this.max
    private var lastPreloadPct: Float = -1f

    var bufferedValue: Double
        get() = bufferedRanges.maxOfOrNull { it.second } ?: 0.0
        set(bufEnd) {
            val clamped = bufEnd.coerceIn(min, max)
            bufferedRanges = if (clamped > min) listOf(min to clamped) else emptyList()
        }

    var bufferedRanges: List<Pair<Double, Double>> = emptyList()
        set(ranges) {
            val normalized = ranges.mapNotNull { (start, end) ->
                val clampedStart = start.coerceIn(min, max)
                val clampedEnd = end.coerceIn(min, max)
                if (clampedEnd > clampedStart) clampedStart to clampedEnd else null
            }
            val changed = field != normalized
            field = normalized
            if (changed || lastPreloadSyncMax != max || lastPreloadSyncMin != min) {
                syncPreloadWidthPercentage()
                syncBufferedDataAttributes()
            }
        }

    var onSeekStart: ((Double) -> Unit)? = null
    var onSeekCommit: ((Double) -> Unit)? = null
    var onTrackHoverMove: ((fraction: Double, isOverKnob: Boolean) -> Unit)? = null
    var onTrackHoverExit: (() -> Unit)? = null

    val playedFraction: Float
        get() = normalizedFraction.toFloat().coerceIn(0f, 1f)

    val bufferedFraction: Float
        get() = if (max > min) ((bufferedValue - min) / (max - min)).toFloat().coerceIn(0f, 1f) else 0f

    init {
        MediaExtension.ensureStyleSheetRegistered()
        htmlTag = DEFAULT_TAG_NAME
        addClass(SEEK_BAR_CLASS)
        addCompositeChild(preloadSegment)

        syncPreloadWidthPercentage()
        syncAllDataAttributes()

        onValueChanged { newVal ->
            handleSliderValueChanged(newVal)
        }

        onScrubStateChanged = { scrubbing ->
            handleScrubStateChanged(scrubbing)
        }

        wireHoverPreviewHandlers()
    }

    private fun handleSliderValueChanged(newVal: Double) {
        if (lastPreloadSyncMax != max || lastPreloadSyncMin != min) {
            syncPreloadWidthPercentage()
            syncBufferedDataAttributes()
        }
        syncValueOnlyDataAttributes()
        if (!suppressBoundSeekPropagation) {
            notifyBoundPlayerSeek(newVal)
        }
    }

    private fun handleScrubStateChanged(scrubbing: Boolean) {
        if (suppressInternalScrubCallbacks) return
        if (scrubbing) {
            onSeekStart?.invoke(value)
        } else {
            val suppressCommit = suppressCommitOnNextEndScrub
            suppressCommitOnNextEndScrub = false
            if (!suppressCommit) {
                val clamped = value.coerceIn(min, max)
                onSeekCommit?.invoke(clamped)
                notifyBoundPlayerSeek(clamped)
            }
        }
    }

    internal fun internalPreloadSegment(): StyledComponent = preloadSegment

    internal fun mediaTrackButton(): StyledButton = trackButton

    internal fun mediaProgressSegment(): StyledComponent = progressSegment

    internal fun mediaKnobButton(): StyledButton = knobButton

    internal fun mediaCompositeChildren(): List<AbstractComponent> = compositeChildren

    fun notifyTrackHover(fraction: Double, isOverKnob: Boolean) {
        onTrackHoverMove?.invoke(fraction.coerceIn(0.0, 1.0), isOverKnob)
    }

    fun notifyTrackHoverExit() {
        onTrackHoverExit?.invoke()
    }

    fun resolveFractionFromPointerX(pointerX: Float): Double {
        val w = resolveEffectiveTrackWidthPx()
        if (w <= 1f) return 0.0
        return (pointerX / w).toDouble().coerceIn(0.0, 1.0)
    }

    fun resolveValueFromFraction(fraction: Double): Double {
        val clampedFraction = fraction.coerceIn(0.0, 1.0)
        val span = (max - min).takeIf { it > 0.0 } ?: return min
        val raw = min + clampedFraction * span
        val stepped = if (step > 0.0) {
            min + round((raw - min) / step) * step
        } else {
            raw
        }
        return stepped.coerceIn(min, max)
    }

    /**
     * Begins a global pointer drag session at [initialFraction] via [PointerDragManager],
     * delegating scrub state cleanly to [StyledSlider.beginScrub].
     */
    fun beginKnobDrag(
        initialFraction: Double,
        initialEvent: PointerEvent = PointerEvent()
    ): PointerDragSession {
        if (!isScrubbing) {
            suppressInternalScrubCallbacks = true
            try {
                beginScrub()
            } finally {
                suppressInternalScrubCallbacks = false
            }
        }
        val startVal = resolveValueFromFraction(initialFraction)
        val changed = abs(value - startVal) >= EPSILON
        suppressBoundSeekPropagation = true
        try {
            value = startVal
        } finally {
            suppressBoundSeekPropagation = false
        }
        val session = ensureGlobalKnobDragSession(initialEvent)
        onSeekStart?.invoke(startVal)
        if (!changed) {
            onValueChanged?.invoke(startVal)
        }
        return session
    }

    /**
     * Updates the seekbar knob position during an active drag from [fraction] (clamped to `0.0..1.0`).
     */
    fun updateKnobDrag(fraction: Double) {
        if (!isScrubbing) {
            beginKnobDrag(fraction)
            return
        }
        val nextVal = resolveValueFromFraction(fraction)
        val changed = abs(value - nextVal) >= EPSILON
        suppressBoundSeekPropagation = true
        try {
            value = nextVal
        } finally {
            suppressBoundSeekPropagation = false
        }
        if (!changed) {
            onValueChanged?.invoke(nextVal)
        }
        notifyBoundPlayerSeek(nextVal)
    }

    /**
     * Ends the active seekbar knob drag session and commits the seek position when [commit] is `true`.
     */
    fun endKnobDrag(commit: Boolean) {
        if (inEndKnobDrag) return
        if (!isScrubbing && activeKnobDragSession == null) return
        inEndKnobDrag = true
        try {
            val session = activeKnobDragSession
            activeKnobDragSession = null
            if (session != null && session.isActive) {
                PointerDragManager.endSession(session, session.lastEvent, cancelled = !commit)
            }
            if (!commit) {
                suppressCommitOnNextEndScrub = true
            }
            endScrub()
        } finally {
            suppressCommitOnNextEndScrub = false
            inEndKnobDrag = false
        }
    }

    fun updateProgressAndBuffer(
        currentValue: Double,
        maxValue: Double,
        bufferedEndValue: Double = this.bufferedValue,
        ranges: List<Pair<Double, Double>>? = null
    ) {
        suppressBoundSeekPropagation = true
        try {
            if (maxValue > min) {
                this.max = maxValue
            }
            if (!isScrubbing) {
                this.value = currentValue.coerceIn(min, this.max)
            }
            if (ranges != null) {
                this.bufferedRanges = ranges
            } else {
                this.bufferedValue = bufferedEndValue
            }
            syncPreloadWidthPercentage()
            syncAllDataAttributes()
        } finally {
            suppressBoundSeekPropagation = false
        }
    }

    fun bindTo(player: Player): Disposable {
        var disposed = false
        var inSync = false

        fun syncFromPlayer() {
            if (disposed || inSync) return
            inSync = true
            try {
                val dur = if (player.durationSeconds > min) player.durationSeconds else max
                val ranges = player.bufferedRanges
                val bufEnd = player.bufferedEndSeconds
                updateProgressAndBuffer(
                    currentValue = player.currentTimeSeconds,
                    maxValue = dur,
                    bufferedEndValue = bufEnd,
                    ranges = if (ranges.isNotEmpty()) ranges else null
                )
            } finally {
                inSync = false
            }
        }

        val stateObserver: () -> Unit = { syncFromPlayer() }
        when (player) {
            is VideoPlayer -> player.addStateChangeObserver(stateObserver)
            is AudioPlayer -> player.addStateChangeObserver(stateObserver)
        }

        val seekCallback: (Double) -> Unit = { targetSeconds ->
            if (!disposed && !inSync) {
                inSync = true
                try {
                    player.seekTo(targetSeconds)
                } finally {
                    inSync = false
                }
            }
        }
        boundPlayerSeekCallbacks.add(seekCallback)

        syncFromPlayer()

        return object : Disposable {
            override var isDisposed: Boolean = false
                private set

            override fun dispose() {
                if (!disposed) {
                    disposed = true
                    isDisposed = true
                    when (player) {
                        is VideoPlayer -> player.removeStateChangeObserver(stateObserver)
                        is AudioPlayer -> player.removeStateChangeObserver(stateObserver)
                    }
                    boundPlayerSeekCallbacks.remove(seekCallback)
                }
            }
        }
    }

    override fun dispose() {
        activeKnobDragSession?.cancel()
        activeKnobDragSession = null
        boundPlayerSeekCallbacks.clear()
        super.dispose()
    }

    private fun wireHoverPreviewHandlers() {
        val baseSelfMove = this.onPointerMove
        val baseTrackMove = trackButton.onPointerMove
        val baseKnobMove = knobButton.onPointerMove
        val baseSelfLeave = this.onPointerLeave

        this.onPointerMove = { event ->
            baseSelfMove?.invoke(event)
            emitTrackHover(event.x)
        }
        trackButton.onPointerMove = { event ->
            baseTrackMove?.invoke(event)
            emitTrackHover(event.x)
        }
        knobButton.onPointerMove = { event ->
            baseKnobMove?.invoke(event)
            val w = resolveEffectiveTrackWidthPx()
            val knobW = knobButton.getBounds().width.toFloat()
            val offsetPx = if (knobW > 0f) event.x - knobW / 2f else 0f
            val fraction = (playedFraction.toDouble() + (offsetPx / w).toDouble()).coerceIn(0.0, 1.0)
            notifyTrackHover(fraction, isOverKnob = true)
        }
        this.onPointerLeave = { event ->
            baseSelfLeave?.invoke(event)
            notifyTrackHoverExit()
        }
    }

    private fun emitTrackHover(localX: Float) {
        val fraction = resolveFractionFromPointerX(localX)
        val w = resolveEffectiveTrackWidthPx()
        val knobCenterX = playedFraction * w
        val knobHalfW = (knobButton.getBounds().width.takeIf { it > 0 }?.toFloat()?.div(2f)) ?: 7f
        val overKnob = abs(localX - knobCenterX) <= maxOf(9f, knobHalfW + 2f)
        notifyTrackHover(fraction, overKnob)
    }

    private fun resolveEffectiveTrackWidthPx(): Float {
        val trackW = trackButton.getBounds().width
        if (trackW > 0) return trackW.toFloat()
        val selfW = getBounds().width
        if (selfW > 0) return selfW.toFloat()
        return (widthPx?.takeIf { it > 0 } ?: 200).toFloat()
    }

    private fun ensureGlobalKnobDragSession(initialEvent: PointerEvent = PointerEvent()): PointerDragSession {
        val existing = activeKnobDragSession
        if (existing != null && existing.isActive) return existing
        return startPointerDrag(
            initialEvent = initialEvent,
            onDragMove = { moveEv ->
                if (isEnabled) {
                    updateKnobDrag(resolveFractionFromPointerX(moveEv.x))
                }
            },
            onDragEnd = { endEv, cancelled ->
                if (!inEndKnobDrag && isScrubbing) {
                    if (!cancelled && isEnabled && (endEv.x != 0f || endEv.screenX != 0f)) {
                        updateKnobDrag(resolveFractionFromPointerX(endEv.x))
                    }
                    endKnobDrag(commit = !cancelled)
                }
            }
        ).also { activeKnobDragSession = it }
    }

    private fun syncPreloadWidthPercentage() {
        lastPreloadSyncMin = min
        lastPreloadSyncMax = max
        val pct = if (max > min) {
            (((bufferedValue - min) / (max - min)) * 100.0).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }
        if (abs(pct - lastPreloadPct) < 1e-4f) return
        lastPreloadPct = pct
        preloadSegment.style.setWidth(CssLength.percent(pct))
    }

    private fun syncValueOnlyDataAttributes() {
        setAttribute("data-min", min.toString())
        setAttribute("data-max", max.toString())
        setAttribute("data-step", step.toString())
        setAttribute("data-value", value.toString())
        setAttribute("data-played-fraction", playedFraction.toString())
    }

    private fun syncBufferedDataAttributes() {
        val serialized = bufferedRanges.joinToString(",") { "${it.first}-${it.second}" }
        setAttribute("data-buffered-fraction", bufferedFraction.toString())
        setAttribute("data-buffered-end", bufferedValue.toString())
        setAttribute("data-buffered-ranges", serialized)
    }

    private fun syncAllDataAttributes() {
        syncValueOnlyDataAttributes()
        syncBufferedDataAttributes()
    }

    private fun notifyBoundPlayerSeek(targetSeconds: Double) {
        for (callback in boundPlayerSeekCallbacks.toList()) {
            callback.invoke(targetSeconds)
        }
    }

    companion object {
        const val DEFAULT_TAG_NAME: String = "genericui:seekbar"
        const val SEEK_BAR_CLASS: String = "seek-bar"
        const val PRELOAD_CLASS: String = "seek-bar-preload"
        private const val EPSILON: Double = 1e-6
    }
}
