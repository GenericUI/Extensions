package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Native implementation contract for [com.nexustools.genericui.component.media.SeekBar].
 */
interface SeekBarNativeImpl<out W> : NativeImpl<W> {
    fun setRange(min: Double, max: Double, step: Double)
    fun setValue(value: Double)
    fun getValue(): Double
    fun setBufferedRanges(ranges: List<Pair<Double, Double>>, max: Double)
    fun setColors(
        accentColor: RgbaColor,
        bufferedColor: RgbaColor,
        trackColor: RgbaColor,
        thumbColor: RgbaColor
    )
    fun setDimensions(trackHeightPx: Float, thumbRadiusPx: Float)
    fun setOnValueChanged(listener: ((Double) -> Unit)?)
    fun setOnSeekStart(listener: ((Double) -> Unit)?) {}
    fun setOnSeekCommit(listener: ((Double) -> Unit)?) {}
    val supportsNativeTrackHover: Boolean
        get() = false
    val handlesNativePointerScrubbing: Boolean
        get() = false
    fun setOnTrackHoverMove(listener: ((fraction: Double, isOverKnob: Boolean) -> Unit)?) {}
    fun setOnTrackHoverExit(listener: (() -> Unit)?) {}
}
