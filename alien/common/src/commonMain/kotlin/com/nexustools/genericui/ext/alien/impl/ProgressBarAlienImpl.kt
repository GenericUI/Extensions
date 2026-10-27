package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Self-rendered Bootstrap 5.3 `.progress` + `.progress-bar` (`ProgressBarAlienImpl`)
 * implementing [ProgressBarNativeImpl].
 */
class ProgressBarAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "progress",
    tagName = "div",
    session = session
), ProgressBarNativeImpl<Any?> {

    private var progressValue: Float = 0f
    private var indeterminateState: Boolean = false
    private var labelValue: String? = null

    init {
        node.customMeasure = { availW, _, _ ->
            Size(width = if (availW > 0) availW else 240, height = 16)
        }
        node.customPaint = { ctx, bounds, style ->
            val trackColor = if (style.darkTheme) {
                AlienBootstrapThemeTokens.SECONDARY_BG_DARK
            } else {
                AlienBootstrapThemeTokens.SECONDARY_BG_LIGHT
            }
            val barType = node.styleSnapshot.type?.takeIf { it.isSemantic }
            val fillColor = if (barType != null) {
                AlienBootstrapThemeTokens.baseColorForType(barType)
            } else {
                style.accentColor
            }
            val r = 6f

            ctx.save()
            ctx.setFillColor(trackColor)
            AlienBootstrapPainter.fillRoundedRect(
                ctx,
                bounds.x.toFloat(),
                bounds.y.toFloat(),
                bounds.width.toFloat(),
                bounds.height.toFloat(),
                r
            )

            val frac = if (indeterminateState) 0.35f else progressValue.coerceIn(0f, 1f)
            val fillW = (bounds.width * frac).coerceAtLeast(0f)
            if (fillW > 0f) {
                ctx.setFillColor(fillColor)
                AlienBootstrapPainter.fillRoundedRect(
                    ctx,
                    bounds.x.toFloat(),
                    bounds.y.toFloat(),
                    fillW,
                    bounds.height.toFloat(),
                    r
                )
            }
            ctx.restore()

            val lbl = labelValue
            if (!lbl.isNullOrBlank()) {
                val fillRect = Rect(bounds.x, bounds.y, fillW.toInt().coerceAtLeast(bounds.width), bounds.height)
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = lbl,
                    contentBounds = fillRect,
                    style = style.copy(fontSizePx = 12f, textColor = RgbaColor.WHITE, textAlign = "center"),
                    verticalCenter = true,
                    maxLines = 1
                )
            }
        }
    }

    override fun setProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        if (progressValue != clamped) {
            progressValue = clamped
            node.invalidateBounds()
        }
    }

    override fun getProgress(): Float = progressValue

    override fun setIndeterminate(indeterminate: Boolean) {
        indeterminateState = indeterminate
        node.hasContinuousAnimation = indeterminate
        node.invalidateBounds()
    }

    override fun isIndeterminate(): Boolean = indeterminateState

    override fun setLabel(label: String?) {
        labelValue = label
        node.invalidateBounds()
    }
}
