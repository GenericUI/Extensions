package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerDragSession
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.round

/**
 * Media playback seek bar with dual-layer progress rendering:
 * - Base unloaded track ([trackColor])
 * - Preloaded / buffered media ranges ([bufferedColor], rendered beneath the played progress bar)
 * - Played progress bar ([accentColor], defaulting to the application's theme color)
 * - Scrubbing thumb ([thumbColor], defaulting to the effective accent color)
 */
open class SeekBar(
    value: Double = 0.0,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double = 0.25
) : AbstractComponent("seek-bar", "genericui:seekbar") {

    private var suppressBoundSeekPropagation: Boolean = false
    private val boundPlayerSeekCallbacks = mutableListOf<(Double) -> Unit>()

    var min: Double = min
        set(newMin) {
            if (kotlin.math.abs(field - newMin) < 1e-6) return
            field = newMin
            if (max < newMin) {
                max = newMin
            }
            val clampedVal = this.value.coerceIn(field, max)
            if (clampedVal != this.value) {
                suppressBoundSeekPropagation = true
                try {
                    this.value = clampedVal
                } finally {
                    suppressBoundSeekPropagation = false
                }
            }
            typedNative<SeekBarNativeImpl<*>>()?.setRange(field, max, step)
            syncVisualDataAttributes()
        }

    var max: Double = max.coerceAtLeast(min)
        set(newMax) {
            val coercedMax = newMax.coerceAtLeast(min)
            if (kotlin.math.abs(field - coercedMax) < 1e-6) return
            field = coercedMax
            val clampedVal = this.value.coerceIn(min, field)
            if (clampedVal != this.value) {
                suppressBoundSeekPropagation = true
                try {
                    this.value = clampedVal
                } finally {
                    suppressBoundSeekPropagation = false
                }
            }
            typedNative<SeekBarNativeImpl<*>>()?.let { impl ->
                impl.setRange(min, field, step)
                impl.setBufferedRanges(bufferedRanges, field)
            }
            syncVisualDataAttributes()
        }

    var step: Double = step.coerceAtLeast(0.001)
        set(newStep) {
            val coercedStep = newStep.coerceAtLeast(0.001)
            if (kotlin.math.abs(field - coercedStep) < 1e-6) return
            field = coercedStep
            typedNative<SeekBarNativeImpl<*>>()?.setRange(min, max, field)
            syncVisualDataAttributes()
        }

    var value: Double = value.coerceIn(this.min, this.max)
        set(newValue) {
            val clamped = newValue.coerceIn(min, max)
            val changed = kotlin.math.abs(field - clamped) >= 1e-6
            field = clamped
            if (changed) {
                typedNative<SeekBarNativeImpl<*>>()?.setValue(clamped)
                syncValueOnlyDataAttributes()
            }
            if (!suppressBoundSeekPropagation && boundPlayerSeekCallbacks.isNotEmpty()) {
                notifyBoundPlayerSeek(clamped)
            }
        }

    var bufferedValue: Double
        get() = bufferedRanges.maxOfOrNull { it.second } ?: 0.0
        set(newBufferedEnd) {
            bufferedRanges = if (newBufferedEnd > min) {
                listOf(min to newBufferedEnd.coerceIn(min, max))
            } else {
                emptyList()
            }
        }

    var bufferedRanges: List<Pair<Double, Double>> = emptyList()
        set(ranges) {
            val normalized = ranges.mapNotNull { (start, end) ->
                val clampedStart = start.coerceIn(min, max)
                val clampedEnd = end.coerceIn(min, max)
                if (clampedEnd > clampedStart) clampedStart to clampedEnd else null
            }
            if (field == normalized) return
            field = normalized
            val serialized = normalized.joinToString(",") { "${it.first}-${it.second}" }
            val bufEnd = normalized.maxOfOrNull { it.second } ?: 0.0
            setAttribute("bufferedRanges", serialized)
            setAttribute("bufferedEnd", bufEnd.toString())
            setAttribute("data-buffered-ranges", serialized)
            setAttribute("data-buffered-end", bufEnd.toString())
            typedNative<SeekBarNativeImpl<*>>()?.setBufferedRanges(field, max)
            syncBufferedOnlyDataAttributes(serialized, bufEnd)
        }

    var accentColor: RgbaColor? = null
        set(color) {
            field = color
            syncColorsToNative()
        }

    var bufferedColor: RgbaColor = RgbaColor(255, 255, 255, 128)
        set(color) {
            field = color
            syncColorsToNative()
        }

    var trackColor: RgbaColor = RgbaColor(255, 255, 255, 56)
        set(color) {
            field = color
            syncColorsToNative()
        }

    var thumbColor: RgbaColor? = null
        set(color) {
            field = color
            syncColorsToNative()
        }

    var trackHeightPx: Float = 5f
        set(height) {
            field = height.coerceAtLeast(1f)
            typedNative<SeekBarNativeImpl<*>>()?.setDimensions(field, thumbRadiusPx)
            syncVisualDataAttributes()
        }

    var thumbRadiusPx: Float = 7f
        set(radius) {
            field = radius.coerceAtLeast(0f)
            typedNative<SeekBarNativeImpl<*>>()?.setDimensions(trackHeightPx, field)
            syncVisualDataAttributes()
        }

    var isScrubbing: Boolean = false
        private set

    private var activeKnobDragSession: PointerDragSession? = null
    private var inEndKnobDrag: Boolean = false

    var onValueChanged: ((Double) -> Unit)? = null
    var onSeekStart: ((Double) -> Unit)? = null
    var onSeekCommit: ((Double) -> Unit)? = null
    var onTrackHoverMove: ((fraction: Double, isOverKnob: Boolean) -> Unit)? = null
    var onTrackHoverExit: (() -> Unit)? = null

    fun notifyTrackHover(fraction: Double, isOverKnob: Boolean) {
        onTrackHoverMove?.invoke(fraction.coerceIn(0.0, 1.0), isOverKnob)
    }

    fun notifyTrackHoverExit() {
        onTrackHoverExit?.invoke()
    }

    val playedFraction: Float
        get() = if (max > min) ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f) else 0f

    val bufferedFraction: Float
        get() = if (max > min) ((bufferedValue - min) / (max - min)).toFloat().coerceIn(0f, 1f) else 0f

    init {
        htmlTag = "genericui:seekbar"
        syncVisualDataAttributes()
        onPointerDown = { event ->
            if (typedNative<SeekBarNativeImpl<*>>()?.handlesNativePointerScrubbing != true) {
                if (isEnabled && !isScrubbing) {
                    beginKnobDrag(resolveFractionFromPointerX(event.x), event)
                }
            }
        }
        onPointerMove = { event ->
            val nativeImpl = typedNative<SeekBarNativeImpl<*>>()
            val fraction = resolveFractionFromPointerX(event.x)
            if (nativeImpl?.handlesNativePointerScrubbing != true && isEnabled && isScrubbing) {
                updateKnobDrag(fraction)
            }
            if (nativeImpl?.supportsNativeTrackHover != true) {
                val w = (getBounds().width.takeIf { it > 0 } ?: widthPx ?: 200).toFloat()
                val knobCenterX = playedFraction * w
                val knobThreshold = maxOf(9f, thumbRadiusPx + 2f)
                val overKnob = kotlin.math.abs(event.x - knobCenterX) <= knobThreshold
                notifyTrackHover(fraction, overKnob)
            }
        }
        onPointerLeave = {
            if (typedNative<SeekBarNativeImpl<*>>()?.supportsNativeTrackHover != true) {
                notifyTrackHoverExit()
            }
        }
        onPointerUp = { event ->
            if (typedNative<SeekBarNativeImpl<*>>()?.handlesNativePointerScrubbing != true && isScrubbing) {
                updateKnobDrag(resolveFractionFromPointerX(event.x))
                endKnobDrag(commit = true)
            }
        }
    }

    fun resolveFractionFromPointerX(pointerX: Float): Double {
        val w = (getBounds().width.takeIf { it > 0 } ?: widthPx ?: 200).toFloat()
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
     * Begins a global pointer drag session for this seekbar's knob via [PointerDragManager],
     * allowing dragging to continue smoothly when the pointer leaves the element bounds and
     * committing the seek when the pointer is released anywhere inside or outside the element.
     */
    fun beginKnobDrag(
        initialFraction: Double,
        initialEvent: PointerEvent = PointerEvent()
    ): PointerDragSession {
        isScrubbing = true
        val startVal = resolveValueFromFraction(initialFraction)
        suppressBoundSeekPropagation = true
        try {
            value = startVal
        } finally {
            suppressBoundSeekPropagation = false
        }
        val session = ensureGlobalKnobDragSession(initialEvent)
        onSeekStart?.invoke(startVal)
        onValueChanged?.invoke(startVal)
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
        suppressBoundSeekPropagation = true
        try {
            value = nextVal
        } finally {
            suppressBoundSeekPropagation = false
        }
        onValueChanged?.invoke(nextVal)
        notifyBoundPlayerSeek(nextVal)
    }

    /**
     * Ends the active seekbar knob drag session and commits the seek position when [commit] is `true`.
     */
    fun endKnobDrag(commit: Boolean = true) {
        if (inEndKnobDrag) return
        if (!isScrubbing && activeKnobDragSession == null) return
        inEndKnobDrag = true
        try {
            isScrubbing = false
            val session = activeKnobDragSession
            activeKnobDragSession = null
            if (session != null && session.isActive) {
                PointerDragManager.endSession(session, session.lastEvent, cancelled = !commit)
            }
            if (commit) {
                val clamped = value.coerceIn(min, max)
                onSeekCommit?.invoke(clamped)
                notifyBoundPlayerSeek(clamped)
            }
        } finally {
            inEndKnobDrag = false
        }
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

    private var cachedSheetCountForColors: Int = -1
    private var cachedSheetAccentColor: RgbaColor? = null
    private var cachedSheetAccentResolved: Boolean = false

    fun resolveEffectiveAccentColor(): RgbaColor {
        accentColor?.let { return it }

        val appTheme = Application.current?.getThemeColor()?.trim()?.takeIf { it.isNotEmpty() }
        if (appTheme != null) {
            runCatching { RgbaColor.fromCss(appTheme) }.getOrNull()?.let { parsed ->
                if (parsed.r > 40 || parsed.g > 40 || parsed.b > 40) {
                    return parsed
                }
            }
        }

        val styleSheets = Application.current?.getStyleSheets().orEmpty()
        if (styleSheets.size != cachedSheetCountForColors || !cachedSheetAccentResolved) {
            cachedSheetCountForColors = styleSheets.size
            cachedSheetAccentResolved = true
            var found: RgbaColor? = null
            for (sheet in styleSheets) {
                val raw = sheet.rawCss
                if (raw.isNotBlank()) {
                    found = extractCssCustomPropertyColor(raw, "--genericui-accent-color")
                        ?: extractCssCustomPropertyColor(raw, "--bs-primary")
                    if (found != null) break
                }
            }
            cachedSheetAccentColor = found
        }
        cachedSheetAccentColor?.let { return it }

        getStyle().accentColor?.let { return it }
        getAttribute("data-accent-color")?.trim()?.takeIf { it.isNotEmpty() }?.let { attrCss ->
            runCatching { RgbaColor.fromCss(attrCss) }.getOrNull()?.let { return it }
        }
        getStyle().color?.let { return it }

        return RgbaColor(13, 110, 253, 255)
    }

    fun resolveEffectiveThumbColor(): RgbaColor =
        thumbColor ?: resolveEffectiveAccentColor()

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

    protected override fun createNativeImpl(factory: NativeFactory): SeekBarNativeImpl<*> =
        MediaExtension.createSeekBarNativeOrFallback()

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        syncSeekBarToNative(impl as? SeekBarNativeImpl<*> ?: return)
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<SeekBarNativeImpl<*>>() ?: return
        syncSeekBarToNative(impl)
    }

    private fun syncSeekBarToNative(impl: SeekBarNativeImpl<*>) {
        impl.setRange(min, max, step)
        impl.setValue(value)
        impl.setBufferedRanges(bufferedRanges, max)
        impl.setColors(
            accentColor = resolveEffectiveAccentColor(),
            bufferedColor = bufferedColor,
            trackColor = trackColor,
            thumbColor = resolveEffectiveThumbColor()
        )
        impl.setDimensions(trackHeightPx, thumbRadiusPx)
        impl.setOnSeekStart { startVal ->
            isScrubbing = true
            if (!impl.handlesNativePointerScrubbing) {
                ensureGlobalKnobDragSession()
            }
            val clamped = startVal.coerceIn(min, max)
            suppressBoundSeekPropagation = true
            try {
                value = clamped
            } finally {
                suppressBoundSeekPropagation = false
            }
            onSeekStart?.invoke(clamped)
        }
        impl.setOnValueChanged { newVal ->
            val clamped = newVal.coerceIn(min, max)
            suppressBoundSeekPropagation = true
            try {
                value = clamped
            } finally {
                suppressBoundSeekPropagation = false
            }
            onValueChanged?.invoke(clamped)
            notifyBoundPlayerSeek(clamped)
        }
        impl.setOnSeekCommit { commitVal ->
            isScrubbing = false
            val session = activeKnobDragSession
            activeKnobDragSession = null
            if (session != null && session.isActive) {
                inEndKnobDrag = true
                try {
                    PointerDragManager.endSession(session, session.lastEvent, cancelled = false)
                } finally {
                    inEndKnobDrag = false
                }
            }
            val clamped = commitVal.coerceIn(min, max)
            suppressBoundSeekPropagation = true
            try {
                value = clamped
            } finally {
                suppressBoundSeekPropagation = false
            }
            onSeekCommit?.invoke(clamped)
            notifyBoundPlayerSeek(clamped)
        }
        impl.setOnTrackHoverMove { fraction, isOverKnob ->
            notifyTrackHover(fraction, isOverKnob)
        }
        impl.setOnTrackHoverExit {
            notifyTrackHoverExit()
        }
        syncVisualDataAttributes()
    }

    private fun syncColorsToNative() {
        val effectiveAccent = resolveEffectiveAccentColor()
        val effectiveThumb = resolveEffectiveThumbColor()
        typedNative<SeekBarNativeImpl<*>>()?.setColors(
            accentColor = effectiveAccent,
            bufferedColor = bufferedColor,
            trackColor = trackColor,
            thumbColor = effectiveThumb
        )
        syncVisualDataAttributes()
    }

    private fun syncValueOnlyDataAttributes() {
        val valStr = value.toString()
        val playedStr = playedFraction.toString()
        setAttribute("data-value", valStr)
        setAttribute("data-played-fraction", playedStr)
        typedNative<SeekBarNativeImpl<*>>()?.let { impl ->
            impl.setAttribute("data-value", valStr)
            impl.setAttribute("data-played-fraction", playedStr)
        }
    }

    private fun syncBufferedOnlyDataAttributes(serializedRanges: String, bufEnd: Double) {
        val bufferedStr = bufferedFraction.toString()
        val bufEndStr = bufEnd.toString()
        setAttribute("data-buffered-fraction", bufferedStr)
        setAttribute("data-buffered-end", bufEndStr)
        setAttribute("data-buffered-ranges", serializedRanges)
        typedNative<SeekBarNativeImpl<*>>()?.let { impl ->
            impl.setAttribute("data-buffered-fraction", bufferedStr)
            impl.setAttribute("data-buffered-end", bufEndStr)
            impl.setAttribute("data-buffered-ranges", serializedRanges)
        }
    }

    private fun syncVisualDataAttributes() {
        val minStr = min.toString()
        val maxStr = max.toString()
        val stepStr = step.toString()
        val valStr = value.toString()
        val playedStr = playedFraction.toString()
        val bufferedStr = bufferedFraction.toString()
        val bufEndStr = bufferedValue.toString()
        val rangesStr = bufferedRanges.joinToString(",") { "${it.first}-${it.second}" }
        val accentStr = resolveEffectiveAccentColor().toCssRgbaString()
        val bufColorStr = bufferedColor.toCssRgbaString()
        val trackColorStr = trackColor.toCssRgbaString()
        val thumbColorStr = resolveEffectiveThumbColor().toCssRgbaString()
        val trackHStr = trackHeightPx.toString()
        val thumbRStr = thumbRadiusPx.toString()

        setAttribute("data-min", minStr)
        setAttribute("data-max", maxStr)
        setAttribute("data-step", stepStr)
        setAttribute("data-value", valStr)
        setAttribute("data-played-fraction", playedStr)
        setAttribute("data-buffered-fraction", bufferedStr)
        setAttribute("data-buffered-end", bufEndStr)
        setAttribute("data-buffered-ranges", rangesStr)
        setAttribute("data-effective-accent-color", accentStr)
        setAttribute("data-buffered-color", bufColorStr)
        setAttribute("data-track-color", trackColorStr)
        setAttribute("data-thumb-color", thumbColorStr)
        setAttribute("data-track-height-px", trackHStr)
        setAttribute("data-thumb-radius-px", thumbRStr)

        typedNative<SeekBarNativeImpl<*>>()?.let { impl ->
            impl.setAttribute("data-min", minStr)
            impl.setAttribute("data-max", maxStr)
            impl.setAttribute("data-step", stepStr)
            impl.setAttribute("data-value", valStr)
            impl.setAttribute("data-played-fraction", playedStr)
            impl.setAttribute("data-buffered-fraction", bufferedStr)
            impl.setAttribute("data-buffered-end", bufEndStr)
            impl.setAttribute("data-buffered-ranges", rangesStr)
            impl.setAttribute("data-effective-accent-color", accentStr)
            impl.setAttribute("data-buffered-color", bufColorStr)
            impl.setAttribute("data-track-color", trackColorStr)
            impl.setAttribute("data-thumb-color", thumbColorStr)
            impl.setAttribute("data-track-height-px", trackHStr)
            impl.setAttribute("data-thumb-radius-px", thumbRStr)
        }
    }

    private fun notifyBoundPlayerSeek(targetSeconds: Double) {
        for (callback in boundPlayerSeekCallbacks.toList()) {
            callback.invoke(targetSeconds)
        }
    }

    private fun extractCssCustomPropertyColor(rawCss: String, variableName: String): RgbaColor? {
        val idx = rawCss.indexOf(variableName)
        if (idx < 0) return null
        val colonIdx = rawCss.indexOf(':', idx + variableName.length)
        if (colonIdx < 0) return null
        val endIdx = rawCss.indexOfAny(charArrayOf(';', '}'), colonIdx + 1)
        val rawValue = (if (endIdx > colonIdx) rawCss.substring(colonIdx + 1, endIdx) else rawCss.substring(colonIdx + 1))
            .trim()
        if (rawValue.isEmpty()) return null
        return runCatching { RgbaColor.fromCss(rawValue) }.getOrNull()
    }
}
