package com.nexustools.genericui.web

import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import kotlin.math.ceil
import kotlin.math.round

class WebSeekBarImpl : WebBaseNativeImpl("genericui:seekbar", "seek-bar"), SeekBarNativeImpl<WebDomElement> {

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
    private var onTrackHoverMoveListener: ((Double, Boolean) -> Unit)? = null
    private var onTrackHoverExitListener: (() -> Unit)? = null

    override val supportsNativeTrackHover: Boolean
        get() = true

    override val handlesNativePointerScrubbing: Boolean
        get() = true

    val trackElement = WebDomElement("div").apply {
        addClass("genericui-seekbar-track")
        setAttribute("class", "genericui-seekbar-track")
        setStyle("position", "relative")
        setStyle("width", "100%")
        setStyle("height", "5px")
        setStyle("border-radius", "999px")
        setStyle("background-color", "rgba(255, 255, 255, 0.22)")
        setStyle("overflow", "visible")
    }

    val bufferedBarElement = WebDomElement("div").apply {
        addClass("genericui-seekbar-buffered")
        setAttribute("class", "genericui-seekbar-buffered")
        setStyle("position", "absolute")
        setStyle("left", "0%")
        setStyle("top", "0")
        setStyle("bottom", "0")
        setStyle("width", "0%")
        setStyle("border-radius", "999px")
        setStyle("background-color", "rgba(255, 255, 255, 0.50)")
        setStyle("pointer-events", "none")
        setStyle("transition", "width 120ms linear")
    }

    val playedBarElement = WebDomElement("div").apply {
        addClass("genericui-seekbar-played")
        setAttribute("class", "genericui-seekbar-played")
        setStyle("position", "absolute")
        setStyle("left", "0")
        setStyle("top", "0")
        setStyle("bottom", "0")
        setStyle("width", "0%")
        setStyle("border-radius", "999px")
        setStyle("background-color", "#ff6600")
        setStyle("pointer-events", "none")
    }

    val thumbElement = WebDomElement("div").apply {
        addClass("genericui-seekbar-thumb")
        setAttribute("class", "genericui-seekbar-thumb")
        setStyle("position", "absolute")
        setStyle("top", "50%")
        setStyle("left", "0%")
        setStyle("width", "14px")
        setStyle("height", "14px")
        setStyle("border-radius", "50%")
        setStyle("transform", "translate(-50%, -50%)")
        setStyle("background-color", "#ff6600")
        setStyle("box-shadow", "0 1px 4px rgba(0,0,0,0.55)")
        setStyle("pointer-events", "none")
        setStyle("transition", "transform 120ms ease")
    }

    val rangeInputElement = WebDomElement("input").apply {
        addClass("genericui-seekbar-input")
        setAttribute("class", "genericui-seekbar-input")
        setAttribute("type", "range")
        setAttribute("min", "0.0")
        setAttribute("max", "100.0")
        setAttribute("step", "0.25")
        setAttribute("value", "0.0")
        value = "0.0"
        setStyle("position", "absolute")
        setStyle("left", "0")
        setStyle("top", "-6px")
        setStyle("width", "100%")
        setStyle("height", "calc(100% + 12px)")
        setStyle("margin", "0")
        setStyle("padding", "0")
        setStyle("opacity", "0")
        setStyle("cursor", "pointer")
        setStyle("z-index", "3")
    }

    init {
        ensureHostFlexStyles()

        trackElement.appendChild(bufferedBarElement)
        trackElement.appendChild(playedBarElement)
        trackElement.appendChild(thumbElement)

        nativeWidget.appendChild(trackElement)
        nativeWidget.appendChild(rangeInputElement)

        val handleInputFromRange: () -> Unit = {
            val raw = rangeInputElement.value.toDoubleOrNull()
                ?: rangeInputElement.getAttribute("value")?.toDoubleOrNull()
            if (raw != null) {
                currentValue = raw.coerceIn(minVal, maxVal)
                nativeWidget.value = currentValue.toString()
                nativeWidget.setAttribute("value", currentValue.toString())
                updateVisualStyles()
                onValueChangedListener?.invoke(currentValue)
            }
        }

        val handleInputFromWidget: () -> Unit = {
            val raw = nativeWidget.value.toDoubleOrNull()
                ?: nativeWidget.getAttribute("value")?.toDoubleOrNull()
                ?: rangeInputElement.value.toDoubleOrNull()
                ?: rangeInputElement.getAttribute("value")?.toDoubleOrNull()
            if (raw != null) {
                currentValue = raw.coerceIn(minVal, maxVal)
                rangeInputElement.value = currentValue.toString()
                rangeInputElement.setAttribute("value", currentValue.toString())
                updateVisualStyles()
                onValueChangedListener?.invoke(currentValue)
            }
        }

        rangeInputElement.addEventListener("input") {
            handleInputFromRange()
        }
        rangeInputElement.addEventListener("change") {
            handleInputFromRange()
            onSeekCommitListener?.invoke(currentValue)
        }

        nativeWidget.addEventListener("input") {
            handleInputFromWidget()
        }
        nativeWidget.addEventListener("change") {
            handleInputFromWidget()
            onSeekCommitListener?.invoke(currentValue)
        }

        val handleHoverMove: (Any?) -> Unit = { ev ->
            val listener = onTrackHoverMoveListener
            if (listener != null) {
                val pointerX: Float = when (ev) {
                    is com.nexustools.genericui.event.PointerEvent -> ev.x
                    is Number -> ev.toFloat()
                    is String -> ev.split(",").firstOrNull()?.trim()?.toFloatOrNull() ?: 0f
                    else -> 0f
                }
                val horizontalInsetPx = ceil(thumbRadiusPx + 2f).toInt().coerceAtLeast(6)
                val totalW = nativeWidget.clientWidth.takeIf { it > 0 } ?: 200
                val trackW = (totalW - horizontalInsetPx * 2).coerceAtLeast(1)
                val trackX = (pointerX - horizontalInsetPx).coerceIn(0f, trackW.toFloat())
                val fraction = (trackX / trackW.toDouble()).coerceIn(0.0, 1.0)
                val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
                val playedFrac = ((currentValue - minVal) / span).coerceIn(0.0, 1.0)
                val knobCenterX = horizontalInsetPx + playedFrac * trackW
                val knobThreshold = maxOf(9.0, thumbRadiusPx.toDouble() + 2.0)
                val isOverKnob = kotlin.math.abs(pointerX.toDouble() - knobCenterX) <= knobThreshold
                listener.invoke(fraction, isOverKnob)
            }
        }
        val handleHoverLeave: (Any?) -> Unit = {
            onTrackHoverExitListener?.invoke()
        }

        nativeWidget.addEventListener("pointermove", handleHoverMove)
        nativeWidget.addEventListener("mousemove", handleHoverMove)
        nativeWidget.addEventListener("mouseleave", handleHoverLeave)
        nativeWidget.addEventListener("pointerleave", handleHoverLeave)
        rangeInputElement.addEventListener("pointermove", handleHoverMove)
        rangeInputElement.addEventListener("mousemove", handleHoverMove)
        rangeInputElement.addEventListener("mouseleave", handleHoverLeave)
        rangeInputElement.addEventListener("pointerleave", handleHoverLeave)

        WebBrowserDomBridge.attachSeekBarScrubbing(
            containerElement = nativeWidget,
            rangeInputElement = rangeInputElement,
            onScrubStart = { fraction ->
                val nextVal = resolveValueFromFraction(fraction)
                applyScrubValue(nextVal)
                onSeekStartListener?.invoke(currentValue)
                onValueChangedListener?.invoke(currentValue)
            },
            onScrubMove = { fraction ->
                val nextVal = resolveValueFromFraction(fraction)
                applyScrubValue(nextVal)
                onValueChangedListener?.invoke(currentValue)
            },
            onScrubEnd = { fraction ->
                val nextVal = resolveValueFromFraction(fraction)
                val changed = kotlin.math.abs(nextVal - currentValue) > 1e-6
                applyScrubValue(nextVal)
                if (changed) {
                    onValueChangedListener?.invoke(currentValue)
                }
                onSeekCommitListener?.invoke(currentValue)
            }
        )

        updateVisualStyles()
    }

    private fun resolveValueFromFraction(fraction: Double): Double {
        val clampedFrac = fraction.coerceIn(0.0, 1.0)
        val span = (maxVal - minVal).takeIf { it > 0.0 } ?: return minVal
        val raw = minVal + clampedFrac * span
        val stepped = if (stepVal > 0.0) {
            minVal + round((raw - minVal) / stepVal) * stepVal
        } else {
            raw
        }
        return stepped.coerceIn(minVal, maxVal)
    }

    private fun applyScrubValue(nextVal: Double) {
        currentValue = nextVal.coerceIn(minVal, maxVal)
        val str = currentValue.toString()
        rangeInputElement.value = str
        rangeInputElement.setAttribute("value", str)
        nativeWidget.value = str
        nativeWidget.setAttribute("value", str)
        updateVisualStyles()
    }

    private fun ensureHostFlexStyles() {
        if (isVisible()) {
            nativeWidget.setStyle("display", "flex")
        }
        nativeWidget.setStyle("align-items", "center")
        nativeWidget.setStyle("position", "relative")
        nativeWidget.setStyle("box-sizing", "border-box")
        if ("width" !in nativeWidget.style) {
            nativeWidget.setStyle("width", "100%")
        }
        val horizontalInsetPx = ceil(thumbRadiusPx + 2f).toInt().coerceAtLeast(6)
        nativeWidget.setStyle("padding-left", "${horizontalInsetPx}px")
        nativeWidget.setStyle("padding-right", "${horizontalInsetPx}px")
        val thumbDiameter = thumbRadiusPx * 2f
        val containerHeight = maxOf(20f, thumbDiameter + 6f)
        nativeWidget.setStyle("height", "${containerHeight}px")
        nativeWidget.setStyle("cursor", "pointer")
        nativeWidget.setStyle("touch-action", "none")
        nativeWidget.setStyle("user-select", "none")
    }

    override fun setVisible(visible: Boolean) {
        super<WebBaseNativeImpl>.setVisible(visible)
        if (visible) {
            ensureHostFlexStyles()
        }
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        super<WebBaseNativeImpl>.applyComponentStyle(style)
        ensureHostFlexStyles()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super<WebBaseNativeImpl>.applyComputedStyle(style)
        ensureHostFlexStyles()
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max.coerceAtLeast(min)
        stepVal = step.coerceAtLeast(0.001)
        currentValue = currentValue.coerceIn(minVal, maxVal)

        rangeInputElement.setAttribute("min", minVal.toString())
        rangeInputElement.setAttribute("max", maxVal.toString())
        rangeInputElement.setAttribute("step", stepVal.toString())

        nativeWidget.setAttribute("min", minVal.toString())
        nativeWidget.setAttribute("max", maxVal.toString())
        nativeWidget.setAttribute("step", stepVal.toString())
        nativeWidget.setAttribute("data-min", minVal.toString())
        nativeWidget.setAttribute("data-max", maxVal.toString())
        nativeWidget.setAttribute("data-step", stepVal.toString())

        updateVisualStyles()
    }

    override fun setValue(value: Double) {
        currentValue = value.coerceIn(minVal, maxVal)
        val str = currentValue.toString()
        rangeInputElement.value = str
        rangeInputElement.setAttribute("value", str)
        nativeWidget.value = str
        nativeWidget.setAttribute("value", str)
        updateVisualStyles()
    }

    override fun getValue(): Double = currentValue

    override fun setBufferedRanges(ranges: List<Pair<Double, Double>>, max: Double) {
        if (max > minVal) {
            maxVal = max
            rangeInputElement.setAttribute("max", maxVal.toString())
            nativeWidget.setAttribute("max", maxVal.toString())
            nativeWidget.setAttribute("data-max", maxVal.toString())
        }
        bufferedRanges = ranges.mapNotNull { (s, e) ->
            val clampedStart = s.coerceIn(minVal, maxVal)
            val clampedEnd = e.coerceIn(minVal, maxVal)
            if (clampedEnd > clampedStart) clampedStart to clampedEnd else null
        }
        updateVisualStyles()
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
        updateVisualStyles()
    }

    override fun setDimensions(trackHeightPx: Float, thumbRadiusPx: Float) {
        this.trackHeightPx = trackHeightPx.coerceAtLeast(1f)
        this.thumbRadiusPx = thumbRadiusPx.coerceAtLeast(0f)
        updateVisualStyles()
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

    override fun setOnTrackHoverMove(listener: ((fraction: Double, isOverKnob: Boolean) -> Unit)?) {
        this.onTrackHoverMoveListener = listener
    }

    override fun setOnTrackHoverExit(listener: (() -> Unit)?) {
        this.onTrackHoverExitListener = listener
    }

    private fun updateVisualStyles() {
        val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val playedPct = (((currentValue - minVal) / span) * 100.0).coerceIn(0.0, 100.0)
        val bufferedEnd = bufferedRanges.maxOfOrNull { it.second } ?: 0.0
        val bufferedPct = if (bufferedEnd > minVal) {
            (((bufferedEnd - minVal) / span) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        val accentCss = accentColor.toCssRgbaString()
        val bufferedCss = bufferedColor.toCssRgbaString()
        val trackCss = trackColor.toCssRgbaString()
        val thumbCss = thumbColor.toCssRgbaString()

        trackElement.setStyle("height", "${trackHeightPx}px")
        trackElement.setStyle("background-color", trackCss)

        bufferedBarElement.setStyle("width", "$bufferedPct%")
        bufferedBarElement.setStyle("background-color", bufferedCss)

        playedBarElement.setStyle("width", "$playedPct%")
        playedBarElement.setStyle("background-color", accentCss)

        val thumbDiameter = thumbRadiusPx * 2f
        thumbElement.setStyle("left", "$playedPct%")
        thumbElement.setStyle("width", "${thumbDiameter}px")
        thumbElement.setStyle("height", "${thumbDiameter}px")
        thumbElement.setStyle("background-color", thumbCss)

        val containerHeight = maxOf(18f, thumbDiameter + 6f)
        val horizontalInsetPx = ceil(thumbRadiusPx + 2f).toInt().coerceAtLeast(6)
        nativeWidget.setStyle("box-sizing", "border-box")
        nativeWidget.setStyle("padding-left", "${horizontalInsetPx}px")
        nativeWidget.setStyle("padding-right", "${horizontalInsetPx}px")
        nativeWidget.setStyle("height", "${containerHeight}px")

        val serializedRanges = bufferedRanges.joinToString(",") { "${it.first}-${it.second}" }
        nativeWidget.setAttribute("data-value", currentValue.toString())
        nativeWidget.setAttribute("data-buffered-end", bufferedEnd.toString())
        nativeWidget.setAttribute("data-buffered-ranges", serializedRanges)
        nativeWidget.setAttribute("data-buffered-percent", bufferedPct.toString())
        nativeWidget.setAttribute("data-played-percent", playedPct.toString())
        nativeWidget.setAttribute("data-accent-color", accentCss)
        nativeWidget.setAttribute("data-buffered-color", bufferedCss)
        nativeWidget.setAttribute("data-track-color", trackCss)
        nativeWidget.setAttribute("data-thumb-color", thumbCss)
    }
}
