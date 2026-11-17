@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toCgColor
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CALayer
import platform.UIKit.UIControlEventTouchCancel
import platform.UIKit.UIControlEventTouchDown
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlEventTouchUpOutside
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISlider
import platform.darwin.NSObject
import platform.objc.sel_registerName
import kotlin.math.round

/**
 * Native UIKit `UISlider` + buffered-range `CALayer` implementation of [SeekBarNativeImpl] for
 * iOS & iPadOS (`BackendTarget.IOS` and `BackendTarget.IPadOS`).
 */
class IosSeekBarImpl(
    val slider: UISlider = UISlider(frame = CGRectMake(0.0, 0.0, 260.0, 32.0))
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(slider),
    componentClassPrefix = "seek-bar"
), SeekBarNativeImpl<UikitViewHandle> {

    private class SliderEventTarget(
        private val onTouchDownAction: () -> Unit,
        private val onValueChangedAction: () -> Unit,
        private val onTouchUpAction: () -> Unit
    ) : NSObject() {
        @ObjCAction
        fun handleTouchDown(sender: NSObject?) = onTouchDownAction()

        @ObjCAction
        fun handleValueChanged(sender: NSObject?) = onValueChangedAction()

        @ObjCAction
        fun handleTouchUp(sender: NSObject?) = onTouchUpAction()

        companion object {
            val SEL_TOUCH_DOWN get() = sel_registerName("handleTouchDown:")
            val SEL_VALUE_CHANGED get() = sel_registerName("handleValueChanged:")
            val SEL_TOUCH_UP get() = sel_registerName("handleTouchUp:")
        }
    }

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

    private val bufferedProgressLayer = CALayer().apply {
        cornerRadius = 2.5
    }

    private val sliderTarget = SliderEventTarget(
        onTouchDownAction = {
            onSeekStartListener?.invoke(currentValue)
        },
        onValueChangedAction = {
            val stepped = quantize(slider.value.toDouble())
            currentValue = stepped
            slider.value = stepped.toFloat()
            syncSliderVisuals()
            onValueChangedListener?.invoke(stepped)
        },
        onTouchUpAction = {
            val stepped = quantize(slider.value.toDouble())
            currentValue = stepped
            slider.value = stepped.toFloat()
            syncSliderVisuals()
            onSeekCommitListener?.invoke(stepped)
        }
    )

    init {
        slider.continuous = true
        slider.layer.insertSublayer(bufferedProgressLayer, 0u)
        slider.addTarget(
            target = sliderTarget,
            action = SliderEventTarget.SEL_TOUCH_DOWN,
            forControlEvents = UIControlEventTouchDown
        )
        slider.addTarget(
            target = sliderTarget,
            action = SliderEventTarget.SEL_VALUE_CHANGED,
            forControlEvents = UIControlEventValueChanged
        )
        slider.addTarget(
            target = sliderTarget,
            action = SliderEventTarget.SEL_TOUCH_UP,
            forControlEvents = UIControlEventTouchUpInside or UIControlEventTouchUpOutside or UIControlEventTouchCancel
        )
        syncSliderVisuals()
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max.coerceAtLeast(min)
        stepVal = step.coerceAtLeast(0.001)
        currentValue = quantize(currentValue)
        syncSliderVisuals()
    }

    override fun setValue(value: Double) {
        currentValue = quantize(value)
        syncSliderVisuals()
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
        syncSliderVisuals()
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
        syncSliderVisuals()
    }

    override fun setDimensions(trackHeightPx: Float, thumbRadiusPx: Float) {
        this.trackHeightPx = trackHeightPx.coerceAtLeast(1f)
        this.thumbRadiusPx = thumbRadiusPx.coerceAtLeast(0f)
        syncSliderVisuals()
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
        currentValue = quantize(value)
        syncSliderVisuals()
        onValueChangedListener?.invoke(currentValue)
    }

    private fun quantize(raw: Double): Double {
        val clamped = raw.coerceIn(minVal, maxVal)
        if (stepVal <= 0.0) return clamped
        val steps = round((clamped - minVal) / stepVal)
        return (minVal + steps * stepVal).coerceIn(minVal, maxVal)
    }

    private fun syncSliderVisuals() {
        slider.minimumValue = minVal.toFloat()
        slider.maximumValue = maxVal.toFloat()
        slider.value = currentValue.toFloat()
        slider.minimumTrackTintColor = accentColor.toUiColor()
        slider.maximumTrackTintColor = trackColor.toUiColor()
        slider.thumbTintColor = thumbColor.toUiColor()
        bufferedProgressLayer.backgroundColor = bufferedColor.toCgColor()

        val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val bufferedEnd = bufferedRanges.maxOfOrNull { it.second } ?: 0.0
        val bufferedFraction = if (bufferedEnd > minVal) {
            ((bufferedEnd - minVal) / span).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        slider.bounds.useContents {
            val h = trackHeightPx.toDouble().coerceAtLeast(2.0)
            val y = ((size.height - h) / 2.0).coerceAtLeast(0.0)
            val w = (size.width * bufferedFraction).coerceAtLeast(0.0)
            bufferedProgressLayer.setFrame(CGRectMake(0.0, y, w, h))
            bufferedProgressLayer.cornerRadius = h / 2.0
        }
    }
}
