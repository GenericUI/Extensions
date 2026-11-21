package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.widget.SeekBar
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.threading.GenericUiThreading
import kotlin.math.round

/**
 * Native Android `android.widget.SeekBar` implementation of [SeekBarNativeImpl].
 */
class AndroidSeekBarImpl : AndroidBaseNativeImpl(
    viewClassName = "android.widget.SeekBar",
    componentClassPrefix = "seek-bar"
), SeekBarNativeImpl<AndroidViewHandle> {

    private companion object {
        const val NATIVE_RESOLUTION: Int = 1000
    }

    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 0.25
    private var currentValue: Double = 0.0
    private var bufferedRanges: List<Pair<Double, Double>> = emptyList()
    private var suppressNativeCallback: Boolean = false

    private var accentColor: RgbaColor = RgbaColor(13, 110, 253, 255)
    private var bufferedColor: RgbaColor = RgbaColor(255, 255, 255, 128)
    private var trackColor: RgbaColor = RgbaColor(255, 255, 255, 56)
    private var thumbColor: RgbaColor = RgbaColor(13, 110, 253, 255)

    private var trackHeightPx: Float = 5f
    private var thumbRadiusPx: Float = 7f

    private var onValueChangedListener: ((Double) -> Unit)? = null
    private var onSeekStartListener: ((Double) -> Unit)? = null
    private var onSeekCommitListener: ((Double) -> Unit)? = null

    private fun dispatchToLogic(block: () -> Unit) {
        if (GenericUI.activeFactory != null &&
            GenericUiThreading.isSplitThreadingEnabled &&
            !GenericUiThreading.isOnLogicThread()
        ) {
            GenericUiThreading.runOnLogic(block)
        } else {
            block()
        }
    }

    override fun createDefaultAndroidView(context: Context): View? = SeekBar(context).apply {
        max = NATIVE_RESOLUTION
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || suppressNativeCallback) return
                val mapped = quantize(minVal + (progress.toDouble() / NATIVE_RESOLUTION) * (maxVal - minVal))
                currentValue = mapped
                dispatchToLogic { onValueChangedListener?.invoke(mapped) }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                val snapshot = currentValue
                dispatchToLogic { onSeekStartListener?.invoke(snapshot) }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val snapshot = currentValue
                dispatchToLogic { onSeekCommitListener?.invoke(snapshot) }
            }
        })
    }

    init {
        syncAttributes()
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max.coerceAtLeast(min)
        stepVal = step.coerceAtLeast(0.001)
        currentValue = quantize(currentValue)
        syncAttributes()
    }

    override fun setValue(value: Double) {
        currentValue = quantize(value)
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

    private fun quantize(raw: Double): Double {
        val clamped = raw.coerceIn(minVal, maxVal)
        if (stepVal <= 0.0) return clamped
        val steps = round((clamped - minVal) / stepVal)
        return (minVal + steps * stepVal).coerceIn(minVal, maxVal)
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
        val progressVal = (playedFraction * NATIVE_RESOLUTION).toInt()
        val secondaryProgressVal = (bufferedFraction * NATIVE_RESOLUTION).toInt()

        nativeWidget.attributes["min"] = minVal.toString()
        nativeWidget.attributes["max"] = maxVal.toString()
        nativeWidget.attributes["step"] = stepVal.toString()
        nativeWidget.attributes["value"] = currentValue.toString()
        nativeWidget.attributes["bufferedRanges"] = serializedRanges
        nativeWidget.attributes["bufferedEnd"] = bufferedEnd.toString()
        nativeWidget.attributes["secondaryProgress"] = secondaryProgressVal.toString()
        nativeWidget.attributes["progress"] = progressVal.toString()
        nativeWidget.attributes["accentColor"] = accentColor.toCssRgbaString()
        nativeWidget.attributes["bufferedColor"] = bufferedColor.toCssRgbaString()
        nativeWidget.attributes["trackColor"] = trackColor.toCssRgbaString()
        nativeWidget.attributes["thumbColor"] = thumbColor.toCssRgbaString()
        val thumbInsetPx = kotlin.math.ceil(thumbRadiusPx + 2f).toInt().coerceAtLeast(6)
        nativeWidget.attributes["trackHeightPx"] = trackHeightPx.toString()
        nativeWidget.attributes["thumbRadiusPx"] = thumbRadiusPx.toString()
        nativeWidget.attributes["thumbInsetPx"] = thumbInsetPx.toString()
        nativeWidget.attributes["padding-left"] = "${thumbInsetPx}px"
        nativeWidget.attributes["padding-right"] = "${thumbInsetPx}px"

        (nativeWidget.androidView as? SeekBar)?.let { sb ->
            AndroidViewHandle.runOnMainThread {
                runCatching {
                    suppressNativeCallback = true
                    sb.max = NATIVE_RESOLUTION
                    sb.progress = progressVal
                    sb.secondaryProgress = secondaryProgressVal
                }
                suppressNativeCallback = false
            }
        }
    }
}
