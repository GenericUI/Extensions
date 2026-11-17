package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.table` component (`TableAlienImpl`) implementing [TableNativeImpl].
 */
class TableAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "table",
    tagName = "table",
    session = session
), TableNativeImpl<Any?> {

    private var headersList: List<String> = emptyList()
    private var rowsList: List<List<String>> = emptyList()
    private var stripedState: Boolean = false
    private var hoverableState: Boolean = false

    init {
        node.customMeasure = { availW, _, _ ->
            val rowH = 38
            val totalRows = 1 + rowsList.size
            Size(
                width = if (availW > 0) availW else 480,
                height = totalRows * rowH
            )
        }
        node.customPaint = { ctx, bounds, style ->
            val cols = maxOf(headersList.size, rowsList.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
            val colW = (bounds.width / cols).coerceAtLeast(1)
            val rowH = 38
            val borderColor = if (style.darkTheme) {
                AlienBootstrapThemeTokens.BORDER_COLOR_DARK
            } else {
                AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            }

            var curY = bounds.y
            // Paint header row
            if (headersList.isNotEmpty()) {
                for ((cIdx, hdr) in headersList.withIndex()) {
                    val cellRect = Rect(bounds.x + cIdx * colW + 8, curY, (colW - 16).coerceAtLeast(0), rowH)
                    AlienBootstrapPainter.paintText(
                        ctx = ctx,
                        text = hdr,
                        contentBounds = cellRect,
                        style = style.copy(fontWeight = 700),
                        verticalCenter = true,
                        maxLines = 1
                    )
                }
                curY += rowH
                ctx.setStrokeColor(borderColor)
                ctx.setLineWidth(2f)
                ctx.beginPath()
                ctx.moveTo(bounds.x.toFloat(), curY.toFloat())
                ctx.lineTo(bounds.right.toFloat(), curY.toFloat())
                ctx.stroke()
            }

            // Paint data rows
            for ((rIdx, row) in rowsList.withIndex()) {
                if (curY + rowH > bounds.bottom) break
                if (stripedState && rIdx % 2 == 1) {
                    val stripeBg = if (style.darkTheme) {
                        AlienBootstrapThemeTokens.SECONDARY_BG_DARK.withAlpha(0.35f)
                    } else {
                        AlienBootstrapThemeTokens.TERTIARY_BG_LIGHT
                    }
                    ctx.setFillColor(stripeBg)
                    ctx.fillRect(bounds.x.toFloat(), curY.toFloat(), bounds.width.toFloat(), rowH.toFloat())
                }
                for ((cIdx, cellText) in row.withIndex()) {
                    val cellRect = Rect(bounds.x + cIdx * colW + 8, curY, (colW - 16).coerceAtLeast(0), rowH)
                    AlienBootstrapPainter.paintText(
                        ctx = ctx,
                        text = cellText,
                        contentBounds = cellRect,
                        style = style,
                        verticalCenter = true,
                        maxLines = 1
                    )
                }
                curY += rowH
                ctx.setStrokeColor(borderColor)
                ctx.setLineWidth(1f)
                ctx.beginPath()
                ctx.moveTo(bounds.x.toFloat(), curY.toFloat())
                ctx.lineTo(bounds.right.toFloat(), curY.toFloat())
                ctx.stroke()
            }
        }
    }

    override fun setHeaders(headers: List<String>) {
        headersList = headers.toList()
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setRows(rows: List<List<String>>) {
        rowsList = rows.map { it.toList() }
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setStriped(striped: Boolean) {
        stripedState = striped
        node.invalidateBounds()
    }

    override fun setHoverable(hoverable: Boolean) {
        hoverableState = hoverable
        node.invalidateBounds()
    }
}
