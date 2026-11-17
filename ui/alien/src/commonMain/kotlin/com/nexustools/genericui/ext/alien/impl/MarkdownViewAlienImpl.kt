package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl

/**
 * Self-rendered Bootstrap 5.3 Markdown view (`MarkdownViewAlienImpl`) implementing [MarkdownViewNativeImpl].
 */
class MarkdownViewAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "markdown-body",
    tagName = "article",
    session = session
), MarkdownViewNativeImpl<Any?> {

    private var markdownSource: String = ""

    init {
        node.customMeasure = { availW, _, style ->
            val lines = markdownSource.lines().size.coerceAtLeast(1)
            val lineH = (style.fontSizePx * 1.5f).toInt().coerceAtLeast(22)
            Size(
                width = if (availW > 0) availW else 400,
                height = lines * lineH + style.padding.top + style.padding.bottom
            )
        }
        node.customPaint = { ctx, bounds, style ->
            val lines = markdownSource.lines()
            var curY = bounds.y + style.padding.top
            val availW = (bounds.width - style.padding.left - style.padding.right).coerceAtLeast(0)
            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                val (cleanText, lineFontPx, weight) = when {
                    trimmed.startsWith("### ") -> Triple(trimmed.removePrefix("### "), 20f, 600)
                    trimmed.startsWith("## ") -> Triple(trimmed.removePrefix("## "), 24f, 600)
                    trimmed.startsWith("# ") -> Triple(trimmed.removePrefix("# "), 30f, 700)
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                        Triple("• " + trimmed.drop(2), style.fontSizePx, style.fontWeight)
                    else -> Triple(trimmed, style.fontSizePx, style.fontWeight)
                }
                val lineH = (lineFontPx * 1.45f).toInt().coerceAtLeast(20)
                if (curY + lineH > bounds.bottom) break
                val lineRect = Rect(bounds.x + style.padding.left, curY, availW, lineH)
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = cleanText,
                    contentBounds = lineRect,
                    style = style.copy(fontSizePx = lineFontPx, fontWeight = weight),
                    verticalCenter = true
                )
                curY += lineH
            }
        }
    }

    override fun setMarkdown(markdown: String) {
        if (markdownSource != markdown) {
            markdownSource = markdown
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getMarkdown(): String = markdownSource
}
