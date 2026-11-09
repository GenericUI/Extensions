package com.nexustools.genericui.ext.media.desktop

import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.desktop.widget.DesktopRangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.roundToInt

open class DesktopSeekBarNativeImpl<W : DesktopNativeWidget>(
    nativeWidget: W
) : DesktopRangeSliderNativeImpl<W>(nativeWidget), SeekBarNativeImpl<W> {

    private var bufferedRanges: List<Pair<Double, Double>> = emptyList()

    private var accentColor: RgbaColor = RgbaColor(13, 110, 253, 255)
    private var bufferedColor: RgbaColor = RgbaColor(255, 255, 255, 128)
    private var trackColor: RgbaColor = RgbaColor(255, 255, 255, 56)
    private var thumbColor: RgbaColor = RgbaColor(13, 110, 253, 255)

    private var trackHeightPx: Float = 5f
    private var thumbRadiusPx: Float = 7f

    private var onSeekStartListener: ((Double) -> Unit)? = null
    private var onSeekCommitListener: ((Double) -> Unit)? = null
    private var isDraggingKnob: Boolean = false

    override val handlesNativePointerScrubbing: Boolean
        get() = true

    init {
        syncAttributesAndInvalidate()
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        super.setRange(min, max, step)
        syncAttributesAndInvalidate()
    }

    override fun setValue(value: Double) {
        val clamped = value.coerceIn(minVal, maxVal)
        if (kotlin.math.abs(currentVal - clamped) < 1e-6 && nativeWidget.attributes.containsKey("value")) return
        currentVal = clamped
        syncValueAndInvalidate()
    }

    private fun syncValueAndInvalidate() {
        val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val playedFraction = (((currentVal - minVal) / span).toFloat()).coerceIn(0f, 1f)
        nativeWidget.attributes["value"] = currentVal.toString()
        nativeWidget.attributes["playedFraction"] = playedFraction.toString()
        nativeWidget.notifyTreeInvalidated(visualOnly = true)
    }

    override fun getValue(): Double = currentVal

    override fun setBufferedRanges(ranges: List<Pair<Double, Double>>, max: Double) {
        if (max > minVal) {
            maxVal = max
        }
        bufferedRanges = ranges.mapNotNull { (s, e) ->
            val clampedStart = s.coerceIn(minVal, maxVal)
            val clampedEnd = e.coerceIn(minVal, maxVal)
            if (clampedEnd > clampedStart) clampedStart to clampedEnd else null
        }
        syncAttributesAndInvalidate()
    }

    override fun setColors(
        accentColor: RgbaColor,
        bufferedColor: RgbaColor,
        trackColor: RgbaColor,
        thumbColor: RgbaColor
    ) {
        this.accentColor = accentColor
        this.bufferedColor = bufferedColor
        this.trackColor = trackColor
        this.thumbColor = thumbColor
        syncAttributesAndInvalidate()
    }

    override fun setDimensions(trackHeightPx: Float, thumbRadiusPx: Float) {
        this.trackHeightPx = trackHeightPx.coerceAtLeast(1f)
        this.thumbRadiusPx = thumbRadiusPx.coerceAtLeast(0f)
        syncAttributesAndInvalidate()
    }

    override fun setOnValueChanged(listener: ((Double) -> Unit)?) {
        super.setOnValueChanged(listener)
        nativeWidget.attributes["onValueChanged"] = { payload: Any? ->
            val v = (payload as? Number)?.toDouble()
                ?: payload?.toString()?.toDoubleOrNull()
            if (v != null) {
                currentVal = v.coerceIn(minVal, maxVal)
                syncValueAndInvalidate()
                listener?.invoke(currentVal)
            }
        }
    }

    override fun setOnSeekStart(listener: ((Double) -> Unit)?) {
        this.onSeekStartListener = listener
    }

    override fun setOnSeekCommit(listener: ((Double) -> Unit)?) {
        this.onSeekCommitListener = listener
    }

    override fun setNormalizedFraction(fraction: Double) {
        updateFromTrackFraction(fraction.toFloat(), commit = !isDraggingKnob)
    }

    override fun beginDragFromFraction(fraction: Double) {
        isDraggingKnob = true
        val stepped = resolveSteppedValueFromFraction(fraction)
        currentVal = stepped
        syncValueAndInvalidate()
        onSeekStartListener?.invoke(currentVal)
        onValueChangedListener?.invoke(currentVal)
    }

    override fun updateDragFromFraction(fraction: Double) {
        if (!isDraggingKnob) {
            beginDragFromFraction(fraction)
            return
        }
        updateFromTrackFraction(fraction.toFloat(), commit = false)
    }

    override fun endDragFromFraction(fraction: Double) {
        isDraggingKnob = false
        updateFromTrackFraction(fraction.toFloat(), commit = true)
    }

    private fun resolveSteppedValueFromFraction(fraction: Double): Double {
        val clampedFraction = fraction.coerceIn(0.0, 1.0)
        val raw = minVal + clampedFraction * (maxVal - minVal)
        return if (stepVal > 0.0) {
            (minVal + ((raw - minVal) / stepVal).roundToInt() * stepVal).coerceIn(minVal, maxVal)
        } else {
            raw.coerceIn(minVal, maxVal)
        }
    }

    fun updateFromTrackFraction(fraction: Float, commit: Boolean = false) {
        val stepped = resolveSteppedValueFromFraction(fraction.toDouble())
        currentVal = stepped
        syncValueAndInvalidate()
        onValueChangedListener?.invoke(currentVal)
        if (commit) {
            onSeekCommitListener?.invoke(currentVal)
        }
    }

    private fun syncAttributesAndInvalidate() {
        val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val playedFraction = (((currentVal - minVal) / span).toFloat()).coerceIn(0f, 1f)
        val bufferedEnd = bufferedRanges.maxOfOrNull { it.second } ?: 0.0
        val bufferedFraction = if (bufferedEnd > minVal) {
            (((bufferedEnd - minVal) / span).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val serializedRanges = bufferedRanges.joinToString(",") { "${it.first}-${it.second}" }

        nativeWidget.attributes["min"] = minVal.toString()
        nativeWidget.attributes["max"] = maxVal.toString()
        nativeWidget.attributes["step"] = stepVal.toString()
        nativeWidget.attributes["value"] = currentVal.toString()
        nativeWidget.attributes["bufferedRanges"] = serializedRanges
        nativeWidget.attributes["bufferedEnd"] = bufferedEnd.toString()
        nativeWidget.attributes["playedFraction"] = playedFraction.toString()
        nativeWidget.attributes["bufferedFraction"] = bufferedFraction.toString()
        nativeWidget.attributes["accentColor"] = accentColor.toCssRgbaString()
        nativeWidget.attributes["bufferedColor"] = bufferedColor.toCssRgbaString()
        nativeWidget.attributes["trackColor"] = trackColor.toCssRgbaString()
        nativeWidget.attributes["thumbColor"] = thumbColor.toCssRgbaString()
        val thumbInsetPx = kotlin.math.ceil(thumbRadiusPx + 2f).toInt().coerceAtLeast(6)
        nativeWidget.attributes["trackHeightPx"] = trackHeightPx.toString()
        nativeWidget.attributes["thumbRadiusPx"] = thumbRadiusPx.toString()
        nativeWidget.attributes["thumbInsetPx"] = thumbInsetPx.toString()
        nativeWidget.notifyTreeInvalidated(visualOnly = true)
    }
}
