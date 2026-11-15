package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

class IosSeekBarImpl : IosBaseNativeImpl(
    uiViewClassName = "SeekBar",
    componentClassPrefix = "seek-bar"
), SeekBarNativeImpl<IosNativeView> {

    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 0.25
    private var currentValue: Double = 0.0
    private var bufferedRanges: List<Pair<Double, Double>> = emptyList()

    private var accentColor: RgbaColor = RgbaColor(13, 110, 253, 255)
    private var bufferedColor: RgbaColor = RgbaColor(255, 255, 255, 128)
    private var trackColor: RgbaColor = RgbaColor(255, 255, 255, 56)
    private var thumbColor: RgbaColor = RgbaColor(13, 110, 253, 255)

    private var trackHeightPx: Float = 5f
    private var thumbRadiusPx: Float = 7f

    private var onValueChangedListener: ((Double) -> Unit)? = null
    private var onSeekStartListener: ((Double) -> Unit)? = null
    private var onSeekCommitListener: ((Double) -> Unit)? = null

    init {
        syncAttributes()
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max.coerceAtLeast(min)
        stepVal = step.coerceAtLeast(0.001)
        currentValue = currentValue.coerceIn(minVal, maxVal)
        syncAttributes()
    }

    override fun setValue(value: Double) {
        currentValue = value.coerceIn(minVal, maxVal)
        syncAttributes()
    }

    override fun getValue(): Double = currentValue

    override fun setBufferedRanges(ranges: List<Pair<Double, Double>>, max: Double) {
        if (max > minVal) {
            maxVal = max
        }
        bufferedRanges = ranges.mapNotNull { (s, e) ->
            val clampedStart = s.coerceIn(minVal, maxVal)
            val clampedEnd = e.coerceIn(minVal, maxVal)
            if (clampedEnd > clampedStart) clampedStart to clampedEnd else null
        }
        syncAttributes()
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
        syncAttributes()
    }

    override fun setDimensions(trackHeightPx: Float, thumbRadiusPx: Float) {
        this.trackHeightPx = trackHeightPx.coerceAtLeast(1f)
        this.thumbRadiusPx = thumbRadiusPx.coerceAtLeast(0f)
        syncAttributes()
    }

    override fun setOnValueChanged(listener: ((Double) -> Unit)?) {
        this.onValueChangedListener = listener
    }

    override fun setOnSeekStart(listener: ((Double) -> Unit)?) {
        this.onSeekStartListener = listener
    }

    override fun setOnSeekCommit(listener: ((Double) -> Unit)?) {
        this.onSeekCommitListener = listener
    }

    fun dispatchValueChanged(value: Double) {
        currentValue = value.coerceIn(minVal, maxVal)
        syncAttributes()
        onValueChangedListener?.invoke(currentValue)
    }

    private fun syncAttributes() {
        val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val playedFraction = (((currentValue - minVal) / span).toFloat()).coerceIn(0f, 1f)
        val bufferedEnd = bufferedRanges.maxOfOrNull { it.second } ?: 0.0
        val bufferedFraction = if (bufferedEnd > minVal) {
            (((bufferedEnd - minVal) / span).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val serializedRanges = bufferedRanges.joinToString(",") { "${it.first}-${it.second}" }

        nativeWidget.metadata["min"] = minVal.toString()
        nativeWidget.metadata["max"] = maxVal.toString()
        nativeWidget.metadata["step"] = stepVal.toString()
        nativeWidget.metadata["value"] = currentValue.toString()
        nativeWidget.metadata["bufferedRanges"] = serializedRanges
        nativeWidget.metadata["bufferedEnd"] = bufferedEnd.toString()
        nativeWidget.metadata["playedFraction"] = playedFraction.toString()
        nativeWidget.metadata["bufferedFraction"] = bufferedFraction.toString()
        nativeWidget.metadata["accentColor"] = accentColor.toCssRgbaString()
        nativeWidget.metadata["bufferedColor"] = bufferedColor.toCssRgbaString()
        nativeWidget.metadata["trackColor"] = trackColor.toCssRgbaString()
        nativeWidget.metadata["thumbColor"] = thumbColor.toCssRgbaString()
        val thumbInsetPx = kotlin.math.ceil(thumbRadiusPx + 2f).toInt().coerceAtLeast(6)
        nativeWidget.metadata["trackHeightPx"] = trackHeightPx.toString()
        nativeWidget.metadata["thumbRadiusPx"] = thumbRadiusPx.toString()
        nativeWidget.metadata["thumbInsetPx"] = thumbInsetPx.toString()
    }
}
