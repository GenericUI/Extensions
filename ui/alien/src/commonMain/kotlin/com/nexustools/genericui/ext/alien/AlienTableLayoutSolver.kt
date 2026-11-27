package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * W3C HTML/CSS `<table>` (`display: table | inline-table`) layout solver supporting
 * `<caption>`, `<thead>`, `<tbody>`, `<tfoot>`, `<tr>`, `<td>`/`<th>` (`display: table-row`,
 * `table-cell`), `colspan`, `rowspan`, `border-collapse`, `border-spacing`, and column sizing.
 */
internal object AlienTableLayoutSolver {

    private data class CellEntry(
        val node: AlienWidgetNode,
        val style: AlienResolvedVisualStyle,
        val rowIdx: Int,
        val colIdx: Int,
        val colSpan: Int,
        val rowSpan: Int
    )

    private data class TableModel(
        val captions: List<AlienWidgetNode>,
        val rows: List<AlienWidgetNode>,
        val cells: List<CellEntry>,
        val colCount: Int,
        val spacingPx: Int
    )

    fun isFullHtmlTable(style: AlienResolvedVisualStyle, node: AlienWidgetNode): Boolean {
        val d = style.display
        if (d == "table" || d == "inline-table" || "d-table" in node.styleSnapshot.customClasses) {
            if (node.tagName.equals("table", ignoreCase = true)) return true
            return node.children.any { child ->
                val cd = child.resolveVisualStyle().display
                cd == "table-row" || cd == "table-row-group" ||
                    cd == "table-header-group" || cd == "table-footer-group" || cd == "table-caption"
            }
        }
        return false
    }

    fun measureTable(
        engine: AlienLayoutEngine,
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        innerAvailW: Int
    ): Size {
        val model = buildModel(node, style)
        val padH = style.padding.left + style.padding.right +
            style.borderLeftWidthPx.roundToInt() + style.borderRightWidthPx.roundToInt()
        val padV = style.padding.top + style.padding.bottom +
            style.borderTopWidthPx.roundToInt() + style.borderBottomWidthPx.roundToInt()

        if (model.colCount == 0 && model.captions.isEmpty()) {
            return Size(padH, padV)
        }
        val colWidths = solveColumnWidths(engine, model, innerAvailW, stretchToAvail = false)
        val rowHeights = solveRowHeights(engine, model, colWidths)

        val tableW = if (model.colCount > 0) {
            colWidths.sum() + model.spacingPx * (model.colCount + 1)
        } else 0
        var totalW = tableW
        var totalH = if (model.rows.isNotEmpty()) {
            rowHeights.sum() + model.spacingPx * (model.rows.size + 1)
        } else 0
        for (cap in model.captions) {
            totalW = max(totalW, cap.measuredSize.width)
            totalH += cap.measuredSize.height
        }
        return Size(totalW + padH, totalH + padV)
    }

    fun arrangeTable(
        engine: AlienLayoutEngine,
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        contentX: Int,
        contentY: Int,
        contentW: Int,
        updateTracker: AlienUpdateRegionTracker?
    ) {
        val model = buildModel(node, style)
        val stretch = style.display != "inline-table" && (style.widthCss != null || node.tagName.equals("table", ignoreCase = true))
        val colWidths = solveColumnWidths(engine, model, contentW, stretchToAvail = stretch)
        val rowHeights = solveRowHeights(engine, model, colWidths)

        var cursorY = contentY
        for (cap in model.captions) {
            val ch = cap.measuredSize.height.coerceAtLeast(1)
            engine.arrangeNode(cap, Rect(contentX, cursorY, contentW.coerceAtLeast(1), ch), updateTracker)
            cursorY += ch
        }

        if (model.rows.isEmpty() || model.colCount == 0) return

        val spacing = model.spacingPx
        val tableWidth = colWidths.sum() + spacing * (colWidths.size + 1)
        val colX = IntArray(colWidths.size)
        var xPos = contentX + spacing
        for (c in colWidths.indices) {
            colX[c] = xPos
            xPos += colWidths[c] + spacing
        }

        val rowY = IntArray(rowHeights.size)
        var yPos = cursorY + spacing
        for (r in rowHeights.indices) {
            rowY[r] = yPos
            engine.arrangeNode(
                model.rows[r],
                Rect(contentX, yPos, tableWidth.coerceAtLeast(1), rowHeights[r].coerceAtLeast(1)),
                updateTracker
            )
            yPos += rowHeights[r] + spacing
        }

        // Group row wrappers so cells sit cleanly in their row's local / global coordinates
        // Note: AlienRenderSession paints every node at its absolute viewport `node.bounds`.
        for (cell in model.cells) {
            val cEnd = (cell.colIdx + cell.colSpan).coerceAtMost(colWidths.size)
            val rEnd = (cell.rowIdx + cell.rowSpan).coerceAtMost(rowHeights.size)
            var cw = 0
            for (c in cell.colIdx until cEnd) {
                cw += colWidths[c]
                if (c > cell.colIdx) cw += spacing
            }
            var ch = 0
            for (r in cell.rowIdx until rEnd) {
                ch += rowHeights[r]
                if (r > cell.rowIdx) ch += spacing
            }
            val cx = colX[cell.colIdx]
            val cy = rowY[cell.rowIdx]
            engine.arrangeNode(cell.node, Rect(cx, cy, cw.coerceAtLeast(1), ch.coerceAtLeast(1)), updateTracker)
        }
    }

    private fun buildModel(tableNode: AlienWidgetNode, tableStyle: AlienResolvedVisualStyle): TableModel {
        val captions = mutableListOf<AlienWidgetNode>()
        val theadRows = mutableListOf<AlienWidgetNode>()
        val bodyRows = mutableListOf<AlienWidgetNode>()
        val tfootRows = mutableListOf<AlienWidgetNode>()

        for (child in tableNode.children) {
            val cs = child.resolveVisualStyle()
            if (!child.visible || cs.display == "none") continue
            when {
                cs.display == "table-caption" || child.tagName.equals("caption", ignoreCase = true) -> captions.add(child)
                cs.display == "table-header-group" || child.tagName.equals("thead", ignoreCase = true) -> {
                    for (r in child.children) {
                        val rs = r.resolveVisualStyle()
                        if (r.visible && rs.display != "none") theadRows.add(r)
                    }
                }
                cs.display == "table-footer-group" || child.tagName.equals("tfoot", ignoreCase = true) -> {
                    for (r in child.children) {
                        val rs = r.resolveVisualStyle()
                        if (r.visible && rs.display != "none") tfootRows.add(r)
                    }
                }
                cs.display == "table-row-group" || child.tagName.equals("tbody", ignoreCase = true) -> {
                    for (r in child.children) {
                        val rs = r.resolveVisualStyle()
                        if (r.visible && rs.display != "none") bodyRows.add(r)
                    }
                }
                cs.display == "table-row" || child.tagName.equals("tr", ignoreCase = true) -> bodyRows.add(child)
            }
        }

        val allRows = theadRows + bodyRows + tfootRows
        val occupied = ArrayList<MutableSet<Int>>(allRows.size)
        repeat(allRows.size) { occupied.add(HashSet()) }

        val cells = mutableListOf<CellEntry>()
        var maxCols = 0

        for ((rIdx, rowNode) in allRows.withIndex()) {
            var cIdx = 0
            for (cellNode in rowNode.children) {
                val cs = cellNode.resolveVisualStyle()
                if (!cellNode.visible || cs.display == "none") continue
                while (cIdx in occupied[rIdx]) cIdx++
                val colSpan = (cellNode.attributes["colspan"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val rowSpan = (cellNode.attributes["rowspan"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                while (occupied.size < rIdx + rowSpan) occupied.add(HashSet())
                for (rr in rIdx until rIdx + rowSpan) {
                    for (cc in cIdx until cIdx + colSpan) {
                        occupied[rr].add(cc)
                    }
                }
                cells.add(CellEntry(cellNode, cs, rIdx, cIdx, colSpan, rowSpan))
                maxCols = max(maxCols, cIdx + colSpan)
                cIdx += colSpan
            }
        }

        val spacing = if (tableStyle.borderCollapse == "collapse") 0 else tableStyle.borderSpacingPx.roundToInt().coerceAtLeast(0)
        return TableModel(captions, allRows, cells, maxCols, spacing)
    }

    private fun solveColumnWidths(
        engine: AlienLayoutEngine,
        model: TableModel,
        availW: Int,
        stretchToAvail: Boolean
    ): IntArray {
        if (model.colCount == 0) return IntArray(0)
        val widths = IntArray(model.colCount) { 16 }
        for (cell in model.cells) {
            val cw = engine.resolveCssDimensionPx(cell.style.widthCss, availW, cell.style.fontSizePx)
                ?: cell.node.measuredSize.width
            if (cell.colSpan == 1) {
                widths[cell.colIdx] = max(widths[cell.colIdx], cw)
            } else {
                val per = (cw / cell.colSpan).coerceAtLeast(1)
                for (c in cell.colIdx until (cell.colIdx + cell.colSpan).coerceAtMost(model.colCount)) {
                    widths[c] = max(widths[c], per)
                }
            }
        }
        val gaps = model.spacingPx * (model.colCount + 1)
        val total = widths.sum() + gaps
        if (stretchToAvail && availW > total && total > gaps) {
            val extra = availW - total
            val currentSum = widths.sum().coerceAtLeast(1)
            for (c in widths.indices) {
                widths[c] += (extra * (widths[c].toFloat() / currentSum)).roundToInt()
            }
        } else if (total > availW && availW > gaps) {
            val targetCols = (availW - gaps).coerceAtLeast(model.colCount)
            val currentSum = widths.sum().coerceAtLeast(1)
            for (c in widths.indices) {
                widths[c] = max(1, (targetCols * (widths[c].toFloat() / currentSum)).roundToInt())
            }
        }
        return widths
    }

    private fun solveRowHeights(
        engine: AlienLayoutEngine,
        model: TableModel,
        colWidths: IntArray
    ): IntArray {
        if (model.rows.isEmpty()) return IntArray(0)
        val heights = IntArray(model.rows.size) { 20 }
        for (cell in model.cells) {
            var spanW = 0
            for (c in cell.colIdx until (cell.colIdx + cell.colSpan).coerceAtMost(colWidths.size)) {
                spanW += colWidths[c]
                if (c > cell.colIdx) spanW += model.spacingPx
            }
            if (spanW != cell.node.measuredSize.width && cell.node.customMeasure != null) {
                cell.node.measuredSize = cell.node.customMeasure!!.invoke(spanW.coerceAtLeast(1), 4096, cell.style)
            }
            val ch = engine.resolveCssDimensionPx(cell.style.heightCss, 1000, cell.style.fontSizePx)
                ?: cell.node.measuredSize.height
            if (cell.rowSpan == 1) {
                heights[cell.rowIdx] = max(heights[cell.rowIdx], ch)
            } else {
                val endR = (cell.rowIdx + cell.rowSpan).coerceAtMost(heights.size)
                val existing = (cell.rowIdx until endR).sumOf { heights[it] }
                if (ch > existing) {
                    val add = (ch - existing) / (endR - cell.rowIdx)
                    for (r in cell.rowIdx until endR) heights[r] += add
                }
            }
        }
        return heights
    }
}
