package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.css.CssMathEvaluator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full W3C CSS Grid (`display: grid | inline-grid`) track sizing and placement solver
 * supporting `fr`, `px`, `%`, `auto`, `minmax(min, max)`, `repeat(N | auto-fill | auto-fit, ...)`,
 * `grid-template-areas` / `grid-area`, explicit `grid-column`/`grid-row` spans and negative lines,
 * `grid-auto-flow: row | column | dense`, `grid-auto-rows` / `grid-auto-columns`, and item alignment.
 */
internal object AlienGridLayoutSolver {

    private sealed class TrackSizeSpec {
        data class FixedPx(val px: Float) : TrackSizeSpec()
        data class Percent(val pct: Float) : TrackSizeSpec()
        data class FlexFr(val fr: Float) : TrackSizeSpec()
        data class MinMax(val minSpec: TrackSizeSpec, val maxSpec: TrackSizeSpec) : TrackSizeSpec()
        data object Auto : TrackSizeSpec()
    }

    private data class NamedArea(
        val colStart: Int, // 0-based inclusive
        val colEnd: Int,   // 0-based exclusive
        val rowStart: Int,
        val rowEnd: Int
    )

    private data class PlacedGridItem(
        val node: AlienWidgetNode,
        val style: AlienResolvedVisualStyle,
        var colStart: Int,
        var colEnd: Int,
        var rowStart: Int,
        var rowEnd: Int
    )

    fun isGridContainer(style: AlienResolvedVisualStyle, node: AlienWidgetNode): Boolean =
        style.display == "grid" ||
            style.display == "inline-grid" ||
            "d-grid" in node.styleSnapshot.customClasses ||
            "d-inline-grid" in node.styleSnapshot.customClasses

    fun measureGridContainer(
        engine: AlienLayoutEngine,
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        flowChildren: List<AlienWidgetNode>,
        innerAvailW: Int,
        innerAvailH: Int,
        gapRow: Int,
        gapCol: Int
    ): Size {
        val solution = solveGrid(engine, style, flowChildren, innerAvailW, innerAvailH, gapRow, gapCol)
        val padH = style.padding.left + style.padding.right +
            style.borderLeftWidthPx.roundToInt() + style.borderRightWidthPx.roundToInt()
        val padV = style.padding.top + style.padding.bottom +
            style.borderTopWidthPx.roundToInt() + style.borderBottomWidthPx.roundToInt()
        return Size(solution.totalWidth + padH, solution.totalHeight + padV)
    }

    fun arrangeGridContainer(
        engine: AlienLayoutEngine,
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        contentX: Int,
        contentY: Int,
        contentW: Int,
        contentH: Int,
        gapRow: Int,
        gapCol: Int,
        flowChildren: List<AlienWidgetNode>,
        updateTracker: AlienUpdateRegionTracker?
    ) {
        if (flowChildren.isEmpty()) return
        val solution = solveGrid(engine, style, flowChildren, contentW, contentH, gapRow, gapCol)

        val colOffsets = IntArray(solution.colWidths.size)
        var cx = 0
        for (c in solution.colWidths.indices) {
            colOffsets[c] = cx
            cx += solution.colWidths[c] + gapCol
        }

        val rowOffsets = IntArray(solution.rowHeights.size)
        var cy = 0
        for (r in solution.rowHeights.indices) {
            rowOffsets[r] = cy
            cy += solution.rowHeights[r] + gapRow
        }

        for (item in solution.items) {
            val c0 = item.colStart.coerceIn(0, solution.colWidths.lastIndex.coerceAtLeast(0))
            val c1 = item.colEnd.coerceIn(c0 + 1, solution.colWidths.size)
            val r0 = item.rowStart.coerceIn(0, solution.rowHeights.lastIndex.coerceAtLeast(0))
            val r1 = item.rowEnd.coerceIn(r0 + 1, solution.rowHeights.size)

            var areaW = 0
            for (c in c0 until c1) {
                areaW += solution.colWidths[c]
                if (c > c0) areaW += gapCol
            }
            var areaH = 0
            for (r in r0 until r1) {
                areaH += solution.rowHeights[r]
                if (r > r0) areaH += gapRow
            }

            val cs = item.style
            val m = cs.margin
            val areaX = contentX + colOffsets[c0]
            val areaY = contentY + rowOffsets[r0]

            val availItemW = (areaW - m.left - m.right).coerceAtLeast(1)
            val availItemH = (areaH - m.top - m.bottom).coerceAtLeast(1)

            val padBorderH = cs.padding.left + cs.padding.right + cs.borderLeftWidthPx.roundToInt() + cs.borderRightWidthPx.roundToInt()
            val padBorderV = cs.padding.top + cs.padding.bottom + cs.borderTopWidthPx.roundToInt() + cs.borderBottomWidthPx.roundToInt()

            val explicitW = cs.widthCss?.let { w ->
                val p = engine.resolveCssDimensionPx(w, availItemW, cs.fontSizePx)
                if (p != null && cs.boxSizing == CssBoxSizing.CONTENT_BOX) p + padBorderH else p
            }
            val jMode = if (cs.justifySelf != "auto") cs.justifySelf else style.justifyItems
            val finalW = when {
                explicitW != null -> explicitW.coerceAtLeast(1)
                jMode == "stretch" -> availItemW
                else -> min(availItemW, item.node.measuredSize.width.coerceAtLeast(1))
            }

            if (finalW != item.node.measuredSize.width && cs.heightCss == null && item.node.customMeasure != null) {
                item.node.measuredSize = item.node.customMeasure!!.invoke(finalW, availItemH, cs)
            }

            val explicitH = cs.heightCss?.let { h ->
                val p = engine.resolveCssDimensionPx(h, availItemH, cs.fontSizePx)
                if (p != null && cs.boxSizing == CssBoxSizing.CONTENT_BOX) p + padBorderV else p
            }
            val aMode = when (cs.alignSelf) {
                com.nexustools.genericui.enums.bootstrap.AlignSelf.START -> "start"
                com.nexustools.genericui.enums.bootstrap.AlignSelf.END -> "end"
                com.nexustools.genericui.enums.bootstrap.AlignSelf.CENTER -> "center"
                com.nexustools.genericui.enums.bootstrap.AlignSelf.STRETCH -> "stretch"
                else -> when (style.alignItems) {
                    com.nexustools.genericui.enums.bootstrap.AlignItems.START -> "start"
                    com.nexustools.genericui.enums.bootstrap.AlignItems.END -> "end"
                    com.nexustools.genericui.enums.bootstrap.AlignItems.CENTER -> "center"
                    else -> "stretch"
                }
            }
            val finalH = when {
                explicitH != null -> explicitH.coerceAtLeast(1)
                aMode == "stretch" -> availItemH
                else -> min(availItemH, item.node.measuredSize.height.coerceAtLeast(1))
            }

            val itemX = when {
                cs.marginLeftAuto && cs.marginRightAuto -> areaX + ((areaW - finalW) / 2).coerceAtLeast(0)
                jMode == "center" -> areaX + m.left + ((availItemW - finalW) / 2).coerceAtLeast(0)
                jMode == "end" || jMode == "flex-end" || jMode == "right" ->
                    areaX + (areaW - m.right - finalW).coerceAtLeast(m.left)
                else -> areaX + m.left
            }
            val itemY = when (aMode) {
                "center" -> areaY + m.top + ((availItemH - finalH) / 2).coerceAtLeast(0)
                "end" -> areaY + (areaH - m.bottom - finalH).coerceAtLeast(m.top)
                else -> areaY + m.top
            }

            engine.arrangeNode(item.node, Rect(itemX, itemY, finalW, finalH), updateTracker)
        }
    }

    private data class GridSolution(
        val colWidths: IntArray,
        val rowHeights: IntArray,
        val totalWidth: Int,
        val totalHeight: Int,
        val items: List<PlacedGridItem>
    )

    private fun solveGrid(
        engine: AlienLayoutEngine,
        style: AlienResolvedVisualStyle,
        flowChildren: List<AlienWidgetNode>,
        availW: Int,
        availH: Int,
        gapRow: Int,
        gapCol: Int
    ): GridSolution {
        val areas = parseTemplateAreas(style.gridTemplateAreas)
        val areaMaxCols = areas.values.maxOfOrNull { it.colEnd } ?: 0
        val areaMaxRows = areas.values.maxOfOrNull { it.rowEnd } ?: 0

        val explicitCols = parseTrackList(
            raw = style.gridTemplateColumns,
            availPx = availW,
            gapPx = gapCol,
            fontSizePx = style.fontSizePx,
            vw = style.viewportWidthPx,
            vh = style.viewportHeightPx
        )
        val explicitRows = parseTrackList(
            raw = style.gridTemplateRows,
            availPx = availH,
            gapPx = gapRow,
            fontSizePx = style.fontSizePx,
            vw = style.viewportWidthPx,
            vh = style.viewportHeightPx
        )
        val autoColSpecs = parseTrackList(
            raw = style.gridAutoColumns,
            availPx = availW,
            gapPx = gapCol,
            fontSizePx = style.fontSizePx,
            vw = style.viewportWidthPx,
            vh = style.viewportHeightPx
        )
        val autoRowSpecs = parseTrackList(
            raw = style.gridAutoRows,
            availPx = availH,
            gapPx = gapRow,
            fontSizePx = style.fontSizePx,
            vw = style.viewportWidthPx,
            vh = style.viewportHeightPx
        )

        val colCountBase = max(explicitCols.size, areaMaxCols).coerceAtLeast(1)
        val rowCountBase = max(explicitRows.size, areaMaxRows)
        val flowParts = style.gridAutoFlow.lowercase().split(Regex("\\s+"))
        val columnFlow = "column" in flowParts
        val denseFlow = "dense" in flowParts

        val items = placeGridItems(flowChildren, areas, colCountBase, rowCountBase, columnFlow, denseFlow)
        val totalCols = max(colCountBase, items.maxOfOrNull { it.colEnd } ?: 1)
        val totalRows = max(rowCountBase, items.maxOfOrNull { it.rowEnd } ?: 1).coerceAtLeast(1)

        val finalColSpecs = List(totalCols) { c ->
            when {
                c < explicitCols.size -> explicitCols[c]
                autoColSpecs.isNotEmpty() -> autoColSpecs[c % autoColSpecs.size]
                else -> TrackSizeSpec.Auto
            }
        }
        val finalRowSpecs = List(totalRows) { r ->
            when {
                r < explicitRows.size -> explicitRows[r]
                autoRowSpecs.isNotEmpty() -> autoRowSpecs[r % autoRowSpecs.size]
                else -> TrackSizeSpec.Auto
            }
        }

        // Measure intrinsic contributions per track (single-span items first, then span items)
        val colIntrinsic = IntArray(totalCols)
        for (item in items) {
            val outerW = item.node.measuredSize.width + item.style.margin.left + item.style.margin.right
            val span = (item.colEnd - item.colStart).coerceAtLeast(1)
            if (span == 1) {
                colIntrinsic[item.colStart] = max(colIntrinsic[item.colStart], outerW)
            }
        }

        val colWidths = resolveTracks(finalColSpecs, availW, gapCol, colIntrinsic, itemSpans = items, isCol = true)

        // Second pass on items whose height changes with resolved column width
        val rowIntrinsic = IntArray(totalRows)
        for (item in items) {
            var itemTrackW = 0
            for (c in item.colStart until item.colEnd) {
                itemTrackW += colWidths[c]
                if (c > item.colStart) itemTrackW += gapCol
            }
            val netW = (itemTrackW - item.style.margin.left - item.style.margin.right).coerceAtLeast(1)
            if (netW != item.node.measuredSize.width && item.style.heightCss == null && item.node.customMeasure != null) {
                item.node.measuredSize = item.node.customMeasure!!.invoke(netW, availH, item.style)
            }
            val outerH = item.node.measuredSize.height + item.style.margin.top + item.style.margin.bottom
            if (item.rowEnd - item.rowStart == 1) {
                rowIntrinsic[item.rowStart] = max(rowIntrinsic[item.rowStart], outerH)
            } else {
                val perRow = (outerH / (item.rowEnd - item.rowStart)).coerceAtLeast(1)
                for (r in item.rowStart until item.rowEnd) {
                    rowIntrinsic[r] = max(rowIntrinsic[r], perRow)
                }
            }
        }

        val rowHeights = resolveTracks(finalRowSpecs, availH, gapRow, rowIntrinsic, itemSpans = items, isCol = false)

        val totalW = colWidths.sum() + gapCol * (totalCols - 1).coerceAtLeast(0)
        val totalH = rowHeights.sum() + gapRow * (totalRows - 1).coerceAtLeast(0)
        return GridSolution(colWidths, rowHeights, totalW, totalH, items)
    }

    private fun resolveTracks(
        specs: List<TrackSizeSpec>,
        availPx: Int,
        gapPx: Int,
        intrinsicSizes: IntArray,
        itemSpans: List<PlacedGridItem>,
        isCol: Boolean
    ): IntArray {
        val count = specs.size
        val sizes = IntArray(count)
        var totalFr = 0f

        for (i in 0 until count) {
            when (val spec = specs[i]) {
                is TrackSizeSpec.FixedPx -> sizes[i] = spec.px.roundToInt().coerceAtLeast(0)
                is TrackSizeSpec.Percent -> sizes[i] = ((availPx * spec.pct / 100f).roundToInt()).coerceAtLeast(0)
                is TrackSizeSpec.Auto -> sizes[i] = intrinsicSizes[i]
                is TrackSizeSpec.FlexFr -> {
                    sizes[i] = intrinsicSizes[i]
                    totalFr += spec.fr
                }
                is TrackSizeSpec.MinMax -> {
                    val minPx = when (val m = spec.minSpec) {
                        is TrackSizeSpec.FixedPx -> m.px.roundToInt()
                        is TrackSizeSpec.Percent -> (availPx * m.pct / 100f).roundToInt()
                        else -> intrinsicSizes[i]
                    }
                    sizes[i] = max(minPx, 0)
                    if (spec.maxSpec is TrackSizeSpec.FlexFr) {
                        totalFr += spec.maxSpec.fr
                    } else if (spec.maxSpec is TrackSizeSpec.Auto) {
                        sizes[i] = max(sizes[i], intrinsicSizes[i])
                    } else if (spec.maxSpec is TrackSizeSpec.FixedPx) {
                        sizes[i] = max(sizes[i], min(spec.maxSpec.px.roundToInt(), max(minPx, intrinsicSizes[i])))
                    }
                }
            }
        }

        // Expand tracks if multi-span items exceed current spanned width
        for (item in itemSpans) {
            val start = if (isCol) item.colStart else item.rowStart
            val end = if (isCol) item.colEnd else item.rowEnd
            if (end - start <= 1) continue
            val itemOuter = if (isCol) {
                item.node.measuredSize.width + item.style.margin.left + item.style.margin.right
            } else {
                item.node.measuredSize.height + item.style.margin.top + item.style.margin.bottom
            }
            var spanned = gapPx * (end - start - 1)
            for (k in start until end) spanned += sizes[k]
            if (itemOuter > spanned) {
                val extra = itemOuter - spanned
                val per = extra / (end - start)
                for (k in start until end) sizes[k] += per
            }
        }

        if (totalFr > 0f && availPx > 0) {
            val totalGap = gapPx * (count - 1).coerceAtLeast(0)
            var fixedSum = 0
            for (i in 0 until count) {
                val spec = specs[i]
                val isFr = spec is TrackSizeSpec.FlexFr || (spec is TrackSizeSpec.MinMax && spec.maxSpec is TrackSizeSpec.FlexFr)
                if (!isFr) fixedSum += sizes[i]
            }
            val freeForFr = (availPx - fixedSum - totalGap).coerceAtLeast(0)
            for (i in 0 until count) {
                val spec = specs[i]
                val frVal = when {
                    spec is TrackSizeSpec.FlexFr -> spec.fr
                    spec is TrackSizeSpec.MinMax && spec.maxSpec is TrackSizeSpec.FlexFr -> spec.maxSpec.fr
                    else -> null
                }
                if (frVal != null) {
                    val share = ((freeForFr * (frVal / totalFr)).roundToInt())
                    val minFloor = if (spec is TrackSizeSpec.MinMax && spec.minSpec is TrackSizeSpec.FixedPx) {
                        spec.minSpec.px.roundToInt()
                    } else 0
                    sizes[i] = max(share, minFloor)
                }
            }
        }
        return sizes
    }

    private fun placeGridItems(
        children: List<AlienWidgetNode>,
        areas: Map<String, NamedArea>,
        explicitCols: Int,
        explicitRows: Int,
        columnFlow: Boolean,
        denseFlow: Boolean
    ): List<PlacedGridItem> {
        val occupancy = ArrayList<BooleanArray>()
        fun ensureRows(rowsNeeded: Int, colsNeeded: Int) {
            while (occupancy.size < rowsNeeded) {
                occupancy.add(BooleanArray(colsNeeded.coerceAtLeast(explicitCols)))
            }
            for (r in occupancy.indices) {
                if (occupancy[r].size < colsNeeded) {
                    val bigger = BooleanArray(colsNeeded)
                    occupancy[r].copyInto(bigger)
                    occupancy[r] = bigger
                }
            }
        }
        fun isFree(r0: Int, c0: Int, rSpan: Int, cSpan: Int): Boolean {
            for (r in r0 until r0 + rSpan) {
                if (r >= occupancy.size) continue
                val rowArr = occupancy[r]
                for (c in c0 until c0 + cSpan) {
                    if (c < rowArr.size && rowArr[c]) return false
                }
            }
            return true
        }
        fun markOccupied(r0: Int, r1: Int, c0: Int, c1: Int) {
            ensureRows(r1, max(explicitCols, c1))
            for (r in r0 until r1) {
                for (c in c0 until c1) {
                    occupancy[r][c] = true
                }
            }
        }

        val styledChildren = children.sortedBy { it.resolveVisualStyle().order }
        val result = ArrayList<PlacedGridItem>(styledChildren.size)
        val unplaced = mutableListOf<Triple<AlienWidgetNode, AlienResolvedVisualStyle, Pair<Int, Int>>>()

        // Pass 1: Explicitly placed items (`grid-area`, `grid-column`+`grid-row`)
        for (child in styledChildren) {
            val cs = child.resolveVisualStyle()
            val named = cs.gridArea?.trim()?.let { areas[it] }
            if (named != null) {
                markOccupied(named.rowStart, named.rowEnd, named.colStart, named.colEnd)
                result.add(PlacedGridItem(child, cs, named.colStart, named.colEnd, named.rowStart, named.rowEnd))
                continue
            }

            val colLine = parseLinePlacement(cs.gridColumn, cs.gridColumnStart, cs.gridColumnEnd, explicitCols)
            val rowLine = parseLinePlacement(cs.gridRow, cs.gridRowStart, cs.gridRowEnd, max(explicitRows, 1))
            if (colLine.start != null && rowLine.start != null) {
                val c0 = colLine.start
                val c1 = c0 + colLine.span
                val r0 = rowLine.start
                val r1 = r0 + rowLine.span
                markOccupied(r0, r1, c0, c1)
                result.add(PlacedGridItem(child, cs, c0, c1, r0, r1))
            } else {
                unplaced.add(Triple(child, cs, colLine.span to rowLine.span))
            }
        }

        // Pass 2: Auto-placement cursor for remaining items
        var curRow = 0
        var curCol = 0
        val gridColsLimit = max(explicitCols, result.maxOfOrNull { it.colEnd } ?: 1).coerceAtLeast(1)
        val gridRowsLimit = max(explicitRows, result.maxOfOrNull { it.rowEnd } ?: 1).coerceAtLeast(1)

        for ((child, cs, spans) in unplaced) {
            val colLine = parseLinePlacement(cs.gridColumn, cs.gridColumnStart, cs.gridColumnEnd, gridColsLimit)
            val rowLine = parseLinePlacement(cs.gridRow, cs.gridRowStart, cs.gridRowEnd, gridRowsLimit)
            val cSpan = colLine.span.coerceIn(1, max(gridColsLimit, colLine.span))
            val rSpan = rowLine.span.coerceAtLeast(1)

            if (colLine.start != null) {
                val fixedC = colLine.start
                var rSearch = if (denseFlow) 0 else curRow
                while (!isFree(rSearch, fixedC, rSpan, cSpan)) rSearch++
                markOccupied(rSearch, rSearch + rSpan, fixedC, fixedC + cSpan)
                result.add(PlacedGridItem(child, cs, fixedC, fixedC + cSpan, rSearch, rSearch + rSpan))
                if (!denseFlow) {
                    curRow = rSearch
                    curCol = fixedC + cSpan
                }
            } else if (!columnFlow) {
                var r = if (denseFlow) 0 else curRow
                var c = if (denseFlow) 0 else curCol
                while (true) {
                    if (c + cSpan > gridColsLimit) {
                        r++
                        c = 0
                        continue
                    }
                    if (isFree(r, c, rSpan, cSpan)) break
                    c++
                }
                markOccupied(r, r + rSpan, c, c + cSpan)
                result.add(PlacedGridItem(child, cs, c, c + cSpan, r, r + rSpan))
                if (!denseFlow) {
                    curRow = r
                    curCol = c + cSpan
                }
            } else {
                var c = if (denseFlow) 0 else curCol
                var r = if (denseFlow) 0 else curRow
                while (true) {
                    if (r + rSpan > gridRowsLimit) {
                        c++
                        r = 0
                        continue
                    }
                    if (isFree(r, c, rSpan, cSpan)) break
                    r++
                }
                markOccupied(r, r + rSpan, c, c + cSpan)
                result.add(PlacedGridItem(child, cs, c, c + cSpan, r, r + rSpan))
                if (!denseFlow) {
                    curCol = c
                    curRow = r + rSpan
                }
            }
        }
        return result
    }

    private data class LinePlacement(val start: Int?, val span: Int)

    private fun parseLinePlacement(
        shorthand: String?,
        explicitStart: String?,
        explicitEnd: String?,
        explicitLineCount: Int
    ): LinePlacement {
        val parts = shorthand?.split('/')?.map { it.trim() }.orEmpty()
        val sTok = explicitStart ?: parts.getOrNull(0)
        val eTok = explicitEnd ?: parts.getOrNull(1)
        if (sTok.isNullOrBlank() && eTok.isNullOrBlank()) return LinePlacement(null, 1)

        fun resolveLineNum(raw: String): Int? {
            val n = raw.toIntOrNull() ?: return null
            return if (n > 0) n - 1 else if (n < 0) (explicitLineCount + 1 + n).coerceAtLeast(0) else null
        }

        if (sTok != null && sTok.startsWith("span ", ignoreCase = true)) {
            val span = sTok.substring(5).trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
            return LinePlacement(null, span)
        }

        val start0 = sTok?.let(::resolveLineNum)
        val span = when {
            eTok == null -> 1
            eTok.startsWith("span ", ignoreCase = true) -> eTok.substring(5).trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
            else -> {
                val end0 = resolveLineNum(eTok)
                if (start0 != null && end0 != null) (end0 - start0).coerceAtLeast(1) else 1
            }
        }
        return LinePlacement(start0, span)
    }

    private fun parseTemplateAreas(raw: String?): Map<String, NamedArea> {
        if (raw.isNullOrBlank()) return emptyMap()
        val rowRegex = Regex("""["']([^"']+)["']""")
        val out = linkedMapOf<String, NamedArea>()
        var rowIdx = 0
        for (match in rowRegex.findAll(raw)) {
            val cells = match.groupValues[1].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            for ((colIdx, name) in cells.withIndex()) {
                if (name == "." || name.all { it == '.' }) continue
                val existing = out[name]
                out[name] = if (existing == null) {
                    NamedArea(colIdx, colIdx + 1, rowIdx, rowIdx + 1)
                } else {
                    NamedArea(
                        min(existing.colStart, colIdx),
                        max(existing.colEnd, colIdx + 1),
                        min(existing.rowStart, rowIdx),
                        max(existing.rowEnd, rowIdx + 1)
                    )
                }
            }
            rowIdx++
        }
        return out
    }

    private fun parseTrackList(
        raw: String?,
        availPx: Int,
        gapPx: Int,
        fontSizePx: Float,
        vw: Float,
        vh: Float
    ): List<TrackSizeSpec> {
        if (raw.isNullOrBlank() || raw.trim().equals("none", ignoreCase = true)) return emptyList()
        val tokens = splitGridTokens(raw.trim())
        val out = mutableListOf<TrackSizeSpec>()
        for (tok in tokens) {
            val lower = tok.lowercase()
            if (lower.startsWith("repeat(") && lower.endsWith(")")) {
                val inner = tok.substring(tok.indexOf('(') + 1, tok.length - 1).trim()
                val commaIdx = inner.indexOf(',')
                if (commaIdx > 0) {
                    val countTok = inner.substring(0, commaIdx).trim().lowercase()
                    val patternRaw = inner.substring(commaIdx + 1).trim()
                    val patternSpecs = parseTrackList(patternRaw, availPx, gapPx, fontSizePx, vw, vh)
                    if (patternSpecs.isEmpty()) continue
                    val repCount = when (countTok) {
                        "auto-fill", "auto-fit" -> {
                            val minTrackW = patternSpecs.sumOf { spec ->
                                when (spec) {
                                    is TrackSizeSpec.FixedPx -> spec.px.roundToInt()
                                    is TrackSizeSpec.MinMax -> if (spec.minSpec is TrackSizeSpec.FixedPx) spec.minSpec.px.roundToInt() else 60
                                    else -> 60
                                }
                            }.coerceAtLeast(1)
                            if (availPx > 0) {
                                ((availPx + gapPx) / (minTrackW + gapPx)).coerceAtLeast(1)
                            } else 1
                        }
                        else -> countTok.toIntOrNull()?.coerceIn(1, 64) ?: 1
                    }
                    repeat(repCount) { out.addAll(patternSpecs) }
                }
            } else {
                parseSingleTrackSpec(tok, availPx, fontSizePx, vw, vh)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun parseSingleTrackSpec(
        raw: String,
        availPx: Int,
        fontSizePx: Float,
        vw: Float,
        vh: Float
    ): TrackSizeSpec? {
        val lower = raw.trim().lowercase()
        if (lower.isEmpty()) return null
        if (lower == "auto" || lower == "min-content" || lower == "max-content") return TrackSizeSpec.Auto
        if (lower.endsWith("fr")) {
            val fr = lower.removeSuffix("fr").trim().toFloatOrNull() ?: return null
            return TrackSizeSpec.FlexFr(fr.coerceAtLeast(0f))
        }
        if (lower.startsWith("minmax(") && lower.endsWith(")")) {
            val inner = raw.substring(raw.indexOf('(') + 1, raw.length - 1).trim()
            val commaIdx = findTopLevelComma(inner)
            if (commaIdx > 0) {
                val minSpec = parseSingleTrackSpec(inner.substring(0, commaIdx), availPx, fontSizePx, vw, vh)
                    ?: TrackSizeSpec.Auto
                val maxSpec = parseSingleTrackSpec(inner.substring(commaIdx + 1), availPx, fontSizePx, vw, vh)
                    ?: TrackSizeSpec.FlexFr(1f)
                return TrackSizeSpec.MinMax(minSpec, maxSpec)
            }
        }
        if (lower.endsWith("%") && !CssMathEvaluator.isMathFunction(lower)) {
            val pct = lower.removeSuffix("%").trim().toFloatOrNull() ?: return null
            return TrackSizeSpec.Percent(pct)
        }
        val px = AlienCssStyleResolver.parseLengthPx(lower, fontSizePx, availPx.toFloat(), vw, vh)
        return px?.let { TrackSizeSpec.FixedPx(it) } ?: TrackSizeSpec.Auto
    }

    private fun splitGridTokens(raw: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (ch in raw) {
            when {
                ch == '(' || ch == '[' -> {
                    depth++
                    sb.append(ch)
                }
                ch == ')' || ch == ']' -> {
                    if (depth > 0) depth--
                    sb.append(ch)
                }
                ch.isWhitespace() && depth == 0 -> {
                    if (sb.isNotEmpty()) {
                        val s = sb.toString()
                        if (!s.startsWith("[")) out.add(s)
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            val s = sb.toString()
            if (!s.startsWith("[")) out.add(s)
        }
        return out
    }

    private fun findTopLevelComma(input: String): Int {
        var depth = 0
        for (i in input.indices) {
            when (input[i]) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) return i
            }
        }
        return -1
    }
}
