package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Self-rendered Bootstrap 5.3 `.form-check` checkbox, switch (`.form-switch`), and radio button
 * (`CheckboxAlienImpl`) implementing [CheckboxNativeImpl].
 */
class CheckboxAlienImpl(
    val switchStyle: Boolean = false,
    val radioStyle: Boolean = false,
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "form-check",
    tagName = "div",
    session = session
), CheckboxNativeImpl<Any?> {

    private var labelValue: String = ""
    private var onCheckedChangedListener: ((Boolean) -> Unit)? = null

    init {
        node.focusRingEnabled = true
        node.customMeasure = { _, _, style ->
            val boxW = if (switchStyle) 32 else 16
            val textW = (labelValue.length * style.fontSizePx * 0.56f).toInt()
            Size(
                width = boxW + 8 + textW + style.padding.left + style.padding.right,
                height = maxOf(22, style.fontSizePx.toInt() + 6) + style.padding.top + style.padding.bottom
            )
        }
        node.customPaint = { ctx, bounds, style ->
            val boxW = if (switchStyle) 32f else 16f
            val boxH = 16f
            val boxX = bounds.x + style.padding.left.toFloat()
            val boxY = bounds.y + (bounds.height - boxH) * 0.5f
            val radius = when {
                switchStyle || radioStyle -> 8f
                else -> 4f
            }

            ctx.save()
            val fillColor = if (node.checkedState) {
                style.accentColor
            } else if (style.darkTheme) {
                AlienBootstrapThemeTokens.BODY_BG_DARK
            } else {
                AlienBootstrapThemeTokens.BODY_BG_LIGHT
            }
            val strokeColor = if (node.checkedState) {
                style.accentColor
            } else if (style.darkTheme) {
                AlienBootstrapThemeTokens.BORDER_COLOR_DARK
            } else {
                AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            }

            ctx.setFillColor(fillColor)
            AlienBootstrapPainter.fillRoundedRect(ctx, boxX, boxY, boxW, boxH, radius)
            ctx.setStrokeColor(strokeColor)
            ctx.setLineWidth(1f)
            AlienBootstrapPainter.strokeRoundedRect(ctx, boxX, boxY, boxW, boxH, radius)

            if (switchStyle) {
                val knobRadius = 5f
                val knobCx = if (node.checkedState) boxX + boxW - 8f else boxX + 8f
                val knobCy = boxY + boxH * 0.5f
                ctx.setFillColor(if (node.checkedState) RgbaColor.WHITE else strokeColor)
                AlienBootstrapPainter.fillRoundedRect(
                    ctx,
                    knobCx - knobRadius,
                    knobCy - knobRadius,
                    knobRadius * 2f,
                    knobRadius * 2f,
                    knobRadius
                )
            } else if (node.checkedState) {
                ctx.setStrokeColor(RgbaColor.WHITE)
                ctx.setLineWidth(2f)
                ctx.beginPath()
                ctx.moveTo(boxX + 4f, boxY + 8f)
                ctx.lineTo(boxX + 7f, boxY + 11.5f)
                ctx.lineTo(boxX + 12.5f, boxY + 4.5f)
                ctx.stroke()
            }
            ctx.restore()

            if (labelValue.isNotEmpty()) {
                val labelRect = Rect(
                    x = (boxX + boxW + 8f).toInt(),
                    y = bounds.y,
                    width = (bounds.right - (boxX + boxW + 8f).toInt()).coerceAtLeast(0),
                    height = bounds.height
                )
                AlienBootstrapPainter.paintText(ctx, labelValue, labelRect, style, verticalCenter = true, maxLines = 1)
            }
        }
        node.onClick = {
            if (node.enabled) {
                val next = if (radioStyle) true else !node.checkedState
                if (node.checkedState != next) {
                    node.checkedState = next
                    onCheckedChangedListener?.invoke(next)
                    node.invalidateBounds()
                }
            }
        }
    }

    override fun setLabel(label: String) {
        if (labelValue != label) {
            labelValue = label
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getLabel(): String = labelValue

    override fun setChecked(checked: Boolean) {
        if (node.checkedState != checked) {
            node.checkedState = checked
            node.invalidateBounds()
        }
    }

    override fun isChecked(): Boolean = node.checkedState

    override fun setOnCheckedChanged(listener: ((Boolean) -> Unit)?) {
        onCheckedChangedListener = listener
    }
}
