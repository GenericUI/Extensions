package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.surface.CollapseNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.accordion-item` / `.collapse` (`CollapseAlienImpl`)
 * implementing [CollapseNativeImpl].
 */
class CollapseAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "accordion-item",
    tagName = "div",
    session = session
), CollapseNativeImpl<Any?> {

    private var titleValue: String = ""
    private var expandedState: Boolean = false
    private var onExpandedChangedListener: ((Boolean) -> Unit)? = null

    init {
        node.customMeasure = { availW, _, _ ->
            val headerH = 44
            val contentH = if (expandedState) {
                node.children.filter { it.visible }.sumOf { it.measuredSize.height + 8 }
            } else {
                0
            }
            Size(width = if (availW > 0) availW else 320, height = headerH + contentH)
        }
        node.customPaint = { ctx, bounds, style ->
            val headerRect = Rect(bounds.x, bounds.y, bounds.width, 44.coerceAtMost(bounds.height))
            val headerBg = if (expandedState) {
                AlienBootstrapThemeTokens.PRIMARY.withAlpha(0.12f)
            } else if (style.darkTheme) {
                AlienBootstrapThemeTokens.TERTIARY_BG_DARK
            } else {
                AlienBootstrapThemeTokens.TERTIARY_BG_LIGHT
            }
            ctx.setFillColor(headerBg)
            AlienBootstrapPainter.fillRoundedRect(
                ctx,
                headerRect.x.toFloat(),
                headerRect.y.toFloat(),
                headerRect.width.toFloat(),
                headerRect.height.toFloat(),
                style.borderRadiusPx
            )
            val textRect = Rect(headerRect.x + 16, headerRect.y, (headerRect.width - 32).coerceAtLeast(0), headerRect.height)
            val chevron = if (expandedState) "▲" else "▼"
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = "$titleValue  $chevron",
                contentBounds = textRect,
                style = style.copy(fontWeight = 500),
                verticalCenter = true,
                maxLines = 1
            )
        }
        node.onClick = {
            setExpanded(!expandedState)
            onExpandedChangedListener?.invoke(expandedState)
        }
    }

    override fun setTitle(title: String) {
        if (titleValue != title) {
            titleValue = title
            node.invalidateBounds()
        }
    }

    override fun setExpanded(expanded: Boolean) {
        if (expandedState != expanded) {
            expandedState = expanded
            for (child in node.children) {
                child.visible = expanded
            }
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun isExpanded(): Boolean = expandedState

    override fun setOnExpandedChanged(listener: ((Boolean) -> Unit)?) {
        onExpandedChangedListener = listener
    }
}
