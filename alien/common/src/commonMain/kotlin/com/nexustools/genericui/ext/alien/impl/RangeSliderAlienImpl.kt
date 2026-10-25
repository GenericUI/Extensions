package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import kotlin.math.roundToInt

/**
 * Self-rendered Bootstrap 5.3 `.form-range` slider (`RangeSliderAlienImpl`) implementing [RangeSliderNativeImpl].
 */
class RangeSliderAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "form-range",
    tagName = "input",
    session = session
), RangeSliderNativeImpl<Any?> {

    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 1.0
    private var currentVal: Double = 0.0
    private var onValueChangedListener: ((Double) -> Unit)? = null

    init {
        node.focusRingEnabled = true
        node.customMeasure = { availW, _, _ ->
            Size(width = if (availW > 0) availW.coerceAtLeast(140) else 200, height = 24)
        }
        node.customPaint = { ctx, bounds, style ->
            val trackH = 8f
            val trackX = bounds.x + 8f
            val trackW = (bounds.width - 16).coerceAtLeast(1).toFloat()
            val trackY = bounds.y + (bounds.height - trackH) * 0.5f

            val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
            val fraction = ((currentVal - minVal) / span).toFloat().coerceIn(0f, 1f)
            val thumbCx = trackX + trackW * fraction
            val thumbCy = bounds.y + bounds.height * 0.5f
            val thumbR = 8f

            ctx.save()
            // Track background (`#dee2e6` light / `#495057` dark)
            val trackBg = if (style.darkTheme) {
                AlienBootstrapThemeTokens.SECONDARY_BG_DARK
            } else {
                AlienBootstrapThemeTokens.SECONDARY_BG_LIGHT
            }
            ctx.setFillColor(trackBg)
            AlienBootstrapPainter.fillRoundedRect(ctx, trackX, trackY, trackW, trackH, 4f)

            // Active fill
            if (fraction > 0f) {
                ctx.setFillColor(AlienBootstrapThemeTokens.PRIMARY.withAlpha(0.75f))
                AlienBootstrapPainter.fillRoundedRect(ctx, trackX, trackY, trackW * fraction, trackH, 4f)
            }

            // Thumb (`1rem` circle `#0d6efd`)
            ctx.setFillColor(AlienBootstrapThemeTokens.PRIMARY)
            AlienBootstrapPainter.fillRoundedRect(
                ctx,
                thumbCx - thumbR,
                thumbCy - thumbR,
                thumbR * 2f,
                thumbR * 2f,
                thumbR
            )
            ctx.restore()
        }
        node.onPointerDown = { ev ->
            updateSliderFromPointer(ev.x)
        }
        node.onPointerMove = { ev ->
            if (node.activePressedState) {
                updateSliderFromPointer(ev.x)
            }
        }
    }

    private fun updateSliderFromPointer(pointerX: Float) {
        if (!node.enabled || node.bounds.width <= 16) return
        val trackX = node.bounds.x + 8f
        val trackW = (node.bounds.width - 16).toFloat().coerceAtLeast(1f)
        val fraction = ((pointerX - trackX) / trackW).coerceIn(0f, 1f).toDouble()
        val raw = minVal + (maxVal - minVal) * fraction
        val snapped = if (stepVal > 0.0) {
            minVal + ((raw - minVal) / stepVal).roundToInt() * stepVal
        } else {
            raw
        }.coerceIn(minVal, maxVal)
        if (currentVal != snapped) {
            currentVal = snapped
            onValueChangedListener?.invoke(snapped)
            node.invalidateBounds()
        }
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max.coerceAtLeast(min)
        stepVal = step.coerceAtLeast(0.0)
        currentVal = currentVal.coerceIn(minVal, maxVal)
        node.invalidateBounds()
    }

    override fun setValue(value: Double) {
        val clamped = value.coerceIn(minVal, maxVal)
        if (currentVal != clamped) {
            currentVal = clamped
            node.invalidateBounds()
        }
    }

    override fun getValue(): Double = currentVal

    override fun setOnValueChanged(listener: ((Double) -> Unit)?) {
        onValueChangedListener = listener
    }
}
