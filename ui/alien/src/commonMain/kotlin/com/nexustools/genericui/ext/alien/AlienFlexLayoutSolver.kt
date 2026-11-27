package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.css.CssAlignContent
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full W3C CSS Flexbox (`display: flex | inline-flex`) measurement and arrangement solver
 * supporting `order`, all four `flex-direction`s (`row`, `row-reverse`, `column`, `column-reverse`),
 * `wrap` / `wrap-reverse`, `flex-grow`, `flex-shrink`, `flex-basis`, `justify-content`
 * (`flex-start`, `flex-end`, `center`, `space-between`, `space-around`, `space-evenly`),
 * `align-items`, `align-self`, `align-content`, and directional gaps.
 */
internal object AlienFlexLayoutSolver {

    private data class FlexItem(
        val node: AlienWidgetNode,
        val style: AlienResolvedVisualStyle,
        val marginMainStart: Int,
        val marginMainEnd: Int,
        val marginCrossStart: Int,
        val marginCrossEnd: Int,
        val baseMain: Int,
        val minMain: Int,
        val maxMain: Int?,
        val baseCross: Int,
        val minCross: Int,
        val maxCross: Int?,
        var resolvedMain: Int = baseMain,
        var resolvedCross: Int = baseCross,
        val marginMainStartAuto: Boolean = false,
        val marginMainEndAuto: Boolean = false
    )

    private data class FlexLine(
        val items: MutableList<FlexItem> = mutableListOf(),
        var mainUsedPx: Int = 0,
        var crossSizePx: Int = 0
    )

    fun isFlexContainer(style: AlienResolvedVisualStyle, node: AlienWidgetNode): Boolean {
        val d = style.display
        return d == "flex" || d == "inline-flex" ||
            node.styleSnapshot.display == com.nexustools.genericui.enums.bootstrap.DisplayMode.FLEX ||
            node.styleSnapshot.display == com.nexustools.genericui.enums.bootstrap.DisplayMode.INLINE_FLEX
    }

    fun measureFlexContainer(
        engine: AlienLayoutEngine,
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        flowChildren: List<AlienWidgetNode>,
        innerAvailW: Int,
        innerAvailH: Int,
        gapRow: Int,
        gapCol: Int
    ): Size {
        val dir = style.flexDirection
        val isRow = dir == FlexDirection.ROW || dir == FlexDirection.ROW_REVERSE
        val mainGap = if (isRow) gapCol else gapRow
        val crossGap = if (isRow) gapRow else gapCol
        val mainAvail = if (isRow) innerAvailW else innerAvailH
        val crossAvail = if (isRow) innerAvailH else innerAvailW
        val isWrap = style.flexWrap != FlexWrap.NOWRAP && mainAvail > 0

        val items = buildItems(engine, flowChildren, isRow, mainAvail, crossAvail)
        val lines = breakIntoLines(items, isWrap, mainAvail, mainGap)

        var maxLineMain = 0
        var totalCross = 0
        for ((idx, line) in lines.withIndex()) {
            var lineMain = 0
            var lineCross = 0
            for ((iIdx, item) in line.items.withIndex()) {
                lineMain += item.baseMain + item.marginMainStart + item.marginMainEnd + (if (iIdx > 0) mainGap else 0)
                lineCross = max(lineCross, item.baseCross + item.marginCrossStart + item.marginCrossEnd)
            }
            maxLineMain = max(maxLineMain, lineMain)
            totalCross += lineCross + (if (idx > 0) crossGap else 0)
        }

        val padH = style.padding.left + style.padding.right +
            style.borderLeftWidthPx.roundToInt() + style.borderRightWidthPx.roundToInt()
        val padV = style.padding.top + style.padding.bottom +
            style.borderTopWidthPx.roundToInt() + style.borderBottomWidthPx.roundToInt()

        return if (isRow) {
            Size(maxLineMain + padH, totalCross + padV)
        } else {
            Size(totalCross + padH, maxLineMain + padV)
        }
    }

    fun arrangeFlexContainer(
        engine: AlienLayoutEngine,
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        assignedBounds: Rect,
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
        val dir = style.flexDirection
        val isRow = dir == FlexDirection.ROW || dir == FlexDirection.ROW_REVERSE
        val isMainReversed = dir == FlexDirection.ROW_REVERSE || dir == FlexDirection.COLUMN_REVERSE
        val mainGap = if (isRow) gapCol else gapRow
        val crossGap = if (isRow) gapRow else gapCol
        val mainAvail = if (isRow) contentW else contentH
        val crossAvail = if (isRow) contentH else contentW
        val isWrap = style.flexWrap != FlexWrap.NOWRAP && mainAvail > 0
        val isWrapReversed = style.flexWrap == FlexWrap.WRAP_REVERSE

        val items = buildItems(engine, flowChildren, isRow, mainAvail, crossAvail)
        val lines = breakIntoLines(items, isWrap, mainAvail, mainGap)

        // 1. Resolve flexible main-axis lengths (flex-grow / flex-shrink) per line
        for (line in lines) {
            resolveLineMainSizes(line, mainAvail, mainGap)
            // Re-measure items if row-flex changed main width and cross height is auto
            for (item in line.items) {
                if (isRow && item.resolvedMain != item.node.measuredSize.width &&
                    item.style.heightCss == null && item.node.customMeasure != null
                ) {
                    val mSize = item.node.customMeasure!!.invoke(item.resolvedMain, crossAvail, item.style)
                    item.resolvedCross = clampAxis(mSize.height, item.minCross, item.maxCross)
                }
            }
            var maxCross = 0
            for (item in line.items) {
                maxCross = max(maxCross, item.resolvedCross + item.marginCrossStart + item.marginCrossEnd)
            }
            line.crossSizePx = maxCross
        }

        // 2. Compute line cross sizes & align-content positions
        val sumLinesCross = lines.sumOf { it.crossSizePx } + crossGap * (lines.size - 1).coerceAtLeast(0)
        if (lines.size == 1 && crossAvail > lines[0].crossSizePx) {
            lines[0].crossSizePx = crossAvail
        }

        val freeCrossSpace = (crossAvail - sumLinesCross).coerceAtLeast(0)
        val linePositionsCross = IntArray(lines.size)
        val lineCrossHeights = IntArray(lines.size) { lines[it].crossSizePx }

        if (lines.size > 1 && freeCrossSpace > 0) {
            when (style.alignContent) {
                CssAlignContent.STRETCH -> {
                    val extraPerLine = freeCrossSpace / lines.size
                    var c = 0
                    for (idx in lines.indices) {
                        lineCrossHeights[idx] += extraPerLine
                        linePositionsCross[idx] = c
                        c += lineCrossHeights[idx] + crossGap
                    }
                }
                CssAlignContent.FLEX_END -> {
                    var c = freeCrossSpace
                    for (idx in lines.indices) {
                        linePositionsCross[idx] = c
                        c += lineCrossHeights[idx] + crossGap
                    }
                }
                CssAlignContent.CENTER -> {
                    var c = freeCrossSpace / 2
                    for (idx in lines.indices) {
                        linePositionsCross[idx] = c
                        c += lineCrossHeights[idx] + crossGap
                    }
                }
                CssAlignContent.SPACE_BETWEEN -> {
                    val space = if (lines.size > 1) freeCrossSpace.toFloat() / (lines.size - 1) else 0f
                    var c = 0f
                    for (idx in lines.indices) {
                        linePositionsCross[idx] = c.roundToInt()
                        c += lineCrossHeights[idx] + crossGap + space
                    }
                }
                CssAlignContent.SPACE_AROUND -> {
                    val step = freeCrossSpace.toFloat() / lines.size
                    var c = step * 0.5f
                    for (idx in lines.indices) {
                        linePositionsCross[idx] = c.roundToInt()
                        c += lineCrossHeights[idx] + crossGap + step
                    }
                }
                CssAlignContent.SPACE_EVENLY -> {
                    val step = freeCrossSpace.toFloat() / (lines.size + 1)
                    var c = step
                    for (idx in lines.indices) {
                        linePositionsCross[idx] = c.roundToInt()
                        c += lineCrossHeights[idx] + crossGap + step
                    }
                }
                else -> {
                    var c = 0
                    for (idx in lines.indices) {
                        linePositionsCross[idx] = c
                        c += lineCrossHeights[idx] + crossGap
                    }
                }
            }
        } else {
            var c = 0
            for (idx in lines.indices) {
                linePositionsCross[idx] = c
                c += lineCrossHeights[idx] + crossGap
            }
        }

        // 3. Place items inside each line
        val orderedLineIndices = if (isWrapReversed) lines.indices.reversed() else lines.indices
        for (lineIdx in orderedLineIndices) {
            val line = lines[lineIdx]
            val lineCrossH = lineCrossHeights[lineIdx]
            val baseCrossPos = if (isWrapReversed) {
                (crossAvail - linePositionsCross[lineIdx] - lineCrossH).coerceAtLeast(0)
            } else {
                linePositionsCross[lineIdx]
            }

            val orderedItems = if (isMainReversed) line.items.asReversed() else line.items
            val usedMain = orderedItems.sumOf { it.resolvedMain + it.marginMainStart + it.marginMainEnd } +
                mainGap * (orderedItems.size - 1).coerceAtLeast(0)
            val freeMain = (mainAvail - usedMain).coerceAtLeast(0)

            var mainAutoCount = 0
            for (item in orderedItems) {
                if (item.marginMainStartAuto) mainAutoCount++
                if (item.marginMainEndAuto) mainAutoCount++
            }

            val autoMainShare = if (mainAutoCount > 0 && freeMain > 0) freeMain / mainAutoCount else 0
            var initialOffset = 0
            var betweenExtra = 0f

            if (mainAutoCount == 0 && freeMain > 0) {
                val effectiveJustify = if (isMainReversed) {
                    when (style.justifyContent) {
                        JustifyContent.START -> JustifyContent.END
                        JustifyContent.END -> JustifyContent.START
                        else -> style.justifyContent
                    }
                } else {
                    style.justifyContent
                }
                when (effectiveJustify) {
                    JustifyContent.CENTER -> initialOffset = freeMain / 2
                    JustifyContent.END -> initialOffset = freeMain
                    JustifyContent.BETWEEN -> {
                        if (orderedItems.size > 1) {
                            betweenExtra = freeMain.toFloat() / (orderedItems.size - 1)
                        }
                    }
                    JustifyContent.AROUND -> {
                        val step = freeMain.toFloat() / orderedItems.size
                        initialOffset = (step * 0.5f).roundToInt()
                        betweenExtra = step
                    }
                    JustifyContent.EVENLY -> {
                        val step = freeMain.toFloat() / (orderedItems.size + 1)
                        initialOffset = step.roundToInt()
                        betweenExtra = step
                    }
                    else -> {}
                }
            }

            var cursorMain = initialOffset.toFloat()
            for (item in orderedItems) {
                val msStart = if (isMainReversed) item.marginMainEnd else item.marginMainStart
                val msEnd = if (isMainReversed) item.marginMainStart else item.marginMainEnd
                val autoStart = if (isMainReversed) item.marginMainEndAuto else item.marginMainStartAuto
                val autoEnd = if (isMainReversed) item.marginMainStartAuto else item.marginMainEndAuto

                cursorMain += msStart + (if (autoStart) autoMainShare else 0)
                val itemMainPos = cursorMain.roundToInt()
                cursorMain += item.resolvedMain + msEnd + (if (autoEnd) autoMainShare else 0) + mainGap + betweenExtra

                val alignMode = resolveEffectiveItemAlign(style.alignItems, item.style.alignSelf)
                val hasFixedCrossSize = if (isRow) item.style.heightCss != null else item.style.widthCss != null
                val availItemCross = (lineCrossH - item.marginCrossStart - item.marginCrossEnd).coerceAtLeast(1)
                val finalCross = when {
                    alignMode == AlignItems.STRETCH && !hasFixedCrossSize ->
                        clampAxis(availItemCross, item.minCross, item.maxCross)
                    else -> item.resolvedCross.coerceAtLeast(1)
                }

                val itemCrossOffset = when (alignMode) {
                    AlignItems.CENTER -> baseCrossPos + item.marginCrossStart + ((availItemCross - finalCross) / 2).coerceAtLeast(0)
                    AlignItems.END -> baseCrossPos + (lineCrossH - item.marginCrossEnd - finalCross).coerceAtLeast(item.marginCrossStart)
                    else -> baseCrossPos + item.marginCrossStart
                }

                val rect = if (isRow) {
                    Rect(
                        x = contentX + itemMainPos,
                        y = contentY + itemCrossOffset,
                        width = item.resolvedMain.coerceAtLeast(1),
                        height = finalCross.coerceAtLeast(1)
                    )
                } else {
                    Rect(
                        x = contentX + itemCrossOffset,
                        y = contentY + itemMainPos,
                        width = finalCross.coerceAtLeast(1),
                        height = item.resolvedMain.coerceAtLeast(1)
                    )
                }
                engine.arrangeNode(item.node, rect, updateTracker)
            }
        }
    }

    private fun buildItems(
        engine: AlienLayoutEngine,
        flowChildren: List<AlienWidgetNode>,
        isRow: Boolean,
        mainAvail: Int,
        crossAvail: Int
    ): List<FlexItem> {
        val sorted = flowChildren.sortedBy { it.resolveVisualStyle().order }
        return sorted.map { child ->
            val cs = child.resolveVisualStyle()
            val m = cs.margin
            val padBorderMain = if (isRow) {
                cs.padding.left + cs.padding.right + cs.borderLeftWidthPx.roundToInt() + cs.borderRightWidthPx.roundToInt()
            } else {
                cs.padding.top + cs.padding.bottom + cs.borderTopWidthPx.roundToInt() + cs.borderBottomWidthPx.roundToInt()
            }
            val padBorderCross = if (isRow) {
                cs.padding.top + cs.padding.bottom + cs.borderTopWidthPx.roundToInt() + cs.borderBottomWidthPx.roundToInt()
            } else {
                cs.padding.left + cs.padding.right + cs.borderLeftWidthPx.roundToInt() + cs.borderRightWidthPx.roundToInt()
            }

            val rawBasis = cs.flexBasisCss?.trim()?.lowercase()
            val basisPx = when {
                rawBasis != null && rawBasis != "auto" && rawBasis != "content" ->
                    resolveOuterAxisDim(engine, rawBasis, mainAvail, cs, padBorderMain)
                else -> {
                    val mainCss = if (isRow) cs.widthCss else cs.heightCss
                    if (mainCss != null && !mainCss.equals("auto", ignoreCase = true)) {
                        resolveOuterAxisDim(engine, mainCss, mainAvail, cs, padBorderMain)
                    } else {
                        if (isRow) child.measuredSize.width else child.measuredSize.height
                    }
                }
            } ?: if (isRow) child.measuredSize.width else child.measuredSize.height

            val minMain = resolveOuterAxisDim(engine, if (isRow) cs.minWidthCss else cs.minHeightCss, mainAvail, cs, padBorderMain) ?: 0
            val maxMain = resolveOuterAxisDim(engine, if (isRow) cs.maxWidthCss else cs.maxHeightCss, mainAvail, cs, padBorderMain)
            val clampedMain = clampAxis(basisPx, minMain, maxMain).coerceAtLeast(0)

            val crossCss = if (isRow) cs.heightCss else cs.widthCss
            val rawCross = resolveOuterAxisDim(engine, crossCss, crossAvail, cs, padBorderCross)
                ?: if (isRow) child.measuredSize.height else child.measuredSize.width
            val minCross = resolveOuterAxisDim(engine, if (isRow) cs.minHeightCss else cs.minWidthCss, crossAvail, cs, padBorderCross) ?: 0
            val maxCross = resolveOuterAxisDim(engine, if (isRow) cs.maxHeightCss else cs.maxWidthCss, crossAvail, cs, padBorderCross)
            val clampedCross = clampAxis(rawCross, minCross, maxCross).coerceAtLeast(1)

            FlexItem(
                node = child,
                style = cs,
                marginMainStart = if (isRow) m.left else m.top,
                marginMainEnd = if (isRow) m.right else m.bottom,
                marginCrossStart = if (isRow) m.top else m.left,
                marginCrossEnd = if (isRow) m.bottom else m.right,
                baseMain = clampedMain,
                minMain = minMain,
                maxMain = maxMain,
                baseCross = clampedCross,
                minCross = minCross,
                maxCross = maxCross,
                resolvedMain = clampedMain,
                resolvedCross = clampedCross,
                marginMainStartAuto = if (isRow) cs.marginLeftAuto else false,
                marginMainEndAuto = if (isRow) cs.marginRightAuto else false
            )
        }
    }

    private fun resolveOuterAxisDim(
        engine: AlienLayoutEngine,
        css: String?,
        availPx: Int,
        style: AlienResolvedVisualStyle,
        padBorderPx: Int
    ): Int? {
        if (css.isNullOrBlank() || css.trim().equals("auto", ignoreCase = true)) return null
        val px = engine.resolveCssDimensionPx(css, availPx, style.fontSizePx) ?: return null
        return if (style.boxSizing == CssBoxSizing.CONTENT_BOX) px + padBorderPx else px
    }

    private fun breakIntoLines(
        items: List<FlexItem>,
        isWrap: Boolean,
        mainAvail: Int,
        mainGap: Int
    ): List<FlexLine> {
        if (!isWrap || items.isEmpty()) {
            return listOf(FlexLine(items.toMutableList()))
        }
        val lines = mutableListOf<FlexLine>()
        var current = FlexLine()
        for (item in items) {
            val outerMain = item.baseMain + item.marginMainStart + item.marginMainEnd
            val nextUsed = current.mainUsedPx + (if (current.items.isNotEmpty()) mainGap else 0) + outerMain
            if (current.items.isNotEmpty() && nextUsed > mainAvail) {
                lines.add(current)
                current = FlexLine()
            }
            current.mainUsedPx += (if (current.items.isNotEmpty()) mainGap else 0) + outerMain
            current.items.add(item)
        }
        if (current.items.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun resolveLineMainSizes(line: FlexLine, mainAvail: Int, mainGap: Int) {
        val items = line.items
        if (items.isEmpty()) return
        val totalBaseOuter = items.sumOf { it.baseMain + it.marginMainStart + it.marginMainEnd } +
            mainGap * (items.size - 1).coerceAtLeast(0)
        val delta = mainAvail - totalBaseOuter

        if (delta > 0) {
            val totalGrow = items.sumOf { it.style.flexGrow.toDouble() }.toFloat()
            if (totalGrow > 0f) {
                for (item in items) {
                    val g = item.style.flexGrow
                    if (g > 0f) {
                        val grown = item.baseMain + ((delta * (g / totalGrow)).roundToInt())
                        item.resolvedMain = clampAxis(grown, item.minMain, item.maxMain)
                    }
                }
            }
        } else if (delta < 0) {
            val overflow = -delta
            val totalWeightedShrink = items.sumOf { (it.style.flexShrink * it.baseMain.coerceAtLeast(1)).toDouble() }.toFloat()
            if (totalWeightedShrink > 0f) {
                for (item in items) {
                    val w = item.style.flexShrink * item.baseMain.coerceAtLeast(1)
                    if (w > 0f) {
                        val shrinkPx = (overflow * (w / totalWeightedShrink)).roundToInt()
                        item.resolvedMain = clampAxis((item.baseMain - shrinkPx).coerceAtLeast(0), item.minMain, item.maxMain)
                    }
                }
            }
        }
    }

    private fun resolveEffectiveItemAlign(containerAlign: AlignItems, itemAlign: AlignSelf): AlignItems = when (itemAlign) {
        AlignSelf.START -> AlignItems.START
        AlignSelf.END -> AlignItems.END
        AlignSelf.CENTER -> AlignItems.CENTER
        AlignSelf.BASELINE -> AlignItems.BASELINE
        AlignSelf.STRETCH -> AlignItems.STRETCH
        AlignSelf.AUTO -> containerAlign
    }

    private fun clampAxis(value: Int, minV: Int, maxV: Int?): Int {
        var r = max(value, minV)
        if (maxV != null) r = min(r, maxV)
        return r
    }
}
