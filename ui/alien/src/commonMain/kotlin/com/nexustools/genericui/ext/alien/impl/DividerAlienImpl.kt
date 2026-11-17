package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `<hr>` divider (`DividerAlienImpl`) implementing [DividerNativeImpl].
 */
class DividerAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "hr",
    tagName = "hr",
    session = session
), DividerNativeImpl<Any?> {

    init {
        node.customMeasure = { availW, _, _ ->
            Size(width = if (availW > 0) availW else 200, height = 17)
        }
        node.customPaint = { ctx, bounds, style ->
            val lineColor = if (style.borderColor.a > 0) {
                style.borderColor
            } else if (style.darkTheme) {
                AlienBootstrapThemeTokens.BORDER_COLOR_DARK
            } else {
                AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            }
            val midY = bounds.y + bounds.height * 0.5f
            ctx.save()
            ctx.setStrokeColor(lineColor)
            ctx.setLineWidth(1f)
            ctx.beginPath()
            ctx.moveTo(bounds.x.toFloat(), midY)
            ctx.lineTo(bounds.right.toFloat(), midY)
            ctx.stroke()
            ctx.restore()
        }
    }
}
