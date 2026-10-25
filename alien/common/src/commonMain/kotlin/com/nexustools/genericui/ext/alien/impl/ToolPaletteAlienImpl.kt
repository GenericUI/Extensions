package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.style.RgbaColor

/**
 * Self-rendered GIMP/Photoshop tool palette (`ToolPaletteAlienImpl`) implementing [ToolPaletteNativeImpl].
 */
class ToolPaletteAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "tool-palette",
    tagName = "div",
    session = session
), ToolPaletteNativeImpl<Any?> {

    private var columnsCount: Int = 2
    private var toolsList: List<ToolPaletteItem> = emptyList()
    private var activeId: String? = null
    private var fgColor: RgbaColor = RgbaColor.BLACK
    private var bgColor: RgbaColor = RgbaColor.WHITE
    private var onToolSelectedListener: ((String) -> Unit)? = null
    private var onColorsSwappedListener: ((RgbaColor, RgbaColor) -> Unit)? = null

    init {
        node.customMeasure = { _, _, _ ->
            val cols = columnsCount.coerceAtLeast(1)
            val rows = ((toolsList.size + cols - 1) / cols).coerceAtLeast(1)
            Size(cols * 38 + 8, rows * 38 + 56)
        }
        node.customPaint = { ctx, bounds, style ->
            val cols = columnsCount.coerceAtLeast(1)
            val cellSize = 36
            for ((idx, tool) in toolsList.withIndex()) {
                val col = idx % cols
                val row = idx / cols
                val tx = bounds.x + 4 + col * 38
                val ty = bounds.y + 4 + row * 38
                val isSelected = tool.id == activeId
                if (isSelected) {
                    ctx.setFillColor(AlienBootstrapThemeTokens.PRIMARY)
                    AlienBootstrapPainter.fillRoundedRect(
                        ctx,
                        tx.toFloat(),
                        ty.toFloat(),
                        cellSize.toFloat(),
                        cellSize.toFloat(),
                        6f
                    )
                }
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = tool.iconSvgOrPath.ifBlank { tool.name.take(2) },
                    contentBounds = Rect(tx, ty, cellSize, cellSize),
                    style = style.copy(
                        textColor = if (isSelected) RgbaColor.WHITE else style.textColor,
                        textAlign = "center"
                    ),
                    verticalCenter = true,
                    maxLines = 1
                )
            }
        }
        node.onPointerDown = { ev ->
            val cols = columnsCount.coerceAtLeast(1)
            val localX = (ev.x.toInt() - node.bounds.x - 4).coerceAtLeast(0)
            val localY = (ev.y.toInt() - node.bounds.y - 4).coerceAtLeast(0)
            val col = localX / 38
            val row = localY / 38
            val idx = row * cols + col
            if (idx in toolsList.indices) {
                val chosenId = toolsList[idx].id
                activeId = chosenId
                onToolSelectedListener?.invoke(chosenId)
                node.invalidateBounds()
            }
        }
    }

    override fun setColumns(columns: Int) {
        columnsCount = columns.coerceAtLeast(1)
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setTools(tools: List<ToolPaletteItem>, activeToolId: String?) {
        toolsList = tools.toList()
        activeId = activeToolId
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setActiveToolId(toolId: String?) {
        activeId = toolId
        node.invalidateBounds()
    }

    override fun setForegroundColor(color: RgbaColor) {
        fgColor = color
        node.invalidateBounds()
    }

    override fun setBackgroundColor(color: RgbaColor) {
        bgColor = color
        node.invalidateBounds()
    }

    override fun setOnToolSelected(listener: ((String) -> Unit)?) {
        onToolSelectedListener = listener
    }

    override fun setOnColorsSwapped(listener: ((RgbaColor, RgbaColor) -> Unit)?) {
        onColorsSwappedListener = listener
    }
}
