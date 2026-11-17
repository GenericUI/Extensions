package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * Self-rendered Bootstrap 5.3 typography component (`TextAlienImpl`) for `Text`, `Heading`, `Badge`,
 * and `Label`, implementing [TextNativeImpl].
 */
class TextAlienImpl(
    tagName: String = "span",
    componentClassPrefix: String = "",
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = componentClassPrefix,
    tagName = tagName,
    session = session
), TextNativeImpl<Any?> {

    private var textValue: String = ""
    private var maxLinesValue: Int? = null

    init {
        node.intrinsicWidth = 0f
        node.intrinsicHeight = 22f
        node.customMeasure = { availW, _, style ->
            val effectiveFontPx = resolveHeadingFontSize(style.fontSizePx)
            val charW = effectiveFontPx * 0.56f
            val rawW = (textValue.length * charW).toInt() + style.padding.left + style.padding.right
            val lineCount = if (availW > 0 && rawW > availW) {
                ((rawW + availW - 1) / availW).coerceAtLeast(1)
            } else {
                1
            }
            val clampedLines = maxLinesValue?.let { lineCount.coerceAtMost(it.coerceAtLeast(1)) } ?: lineCount
            val lineH = (effectiveFontPx * 1.45f).toInt().coerceAtLeast(node.intrinsicHeight.toInt().coerceAtLeast(18))
            val w = if (availW > 0) rawW.coerceAtMost(availW) else rawW.coerceAtLeast(node.intrinsicWidth.toInt().coerceAtLeast(16))
            val h = clampedLines * lineH + style.padding.top + style.padding.bottom
            Size(w, h)
        }
        node.customPaint = { ctx, bounds, style ->
            val effectiveStyle = style.copy(
                fontSizePx = resolveHeadingFontSize(style.fontSizePx),
                fontWeight = if (node.tagName.startsWith("h")) maxOf(style.fontWeight, 500) else style.fontWeight
            )
            val inner = Rect(
                x = bounds.x + effectiveStyle.padding.left,
                y = bounds.y + effectiveStyle.padding.top,
                width = (bounds.width - effectiveStyle.padding.left - effectiveStyle.padding.right).coerceAtLeast(0),
                height = (bounds.height - effectiveStyle.padding.top - effectiveStyle.padding.bottom).coerceAtLeast(0)
            )
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = textValue,
                contentBounds = inner,
                style = effectiveStyle,
                verticalCenter = true,
                maxLines = maxLinesValue
            )
        }
    }

    private fun resolveHeadingFontSize(basePx: Float): Float = when (node.tagName.lowercase()) {
        "h1" -> 40f
        "h2" -> 32f
        "h3" -> 28f
        "h4" -> 24f
        "h5" -> 20f
        "h6" -> 16f
        else -> basePx
    }

    override fun setText(text: String) {
        if (textValue != text) {
            textValue = text
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getText(): String = textValue

    override fun setMaxLines(maxLines: Int?) {
        if (maxLinesValue != maxLines) {
            maxLinesValue = maxLines
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getMaxLines(): Int? = maxLinesValue
}
