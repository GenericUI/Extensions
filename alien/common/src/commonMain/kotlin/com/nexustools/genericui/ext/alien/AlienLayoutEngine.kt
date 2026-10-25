package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Two-pass layout engine (`measure` and `arrange`) for [AlienWidgetNode] trees inside an [AlienRenderSession].
 *
 * Supports:
 * - Standard Bootstrap 5.3 block, inline-block, flex (`d-flex`, `flex-row`, `flex-column`, `flex: 1`,
 *   `justify-content-*`, `align-items-*`, `gap-*`), `display: table` / `table-cell`, `float: left` / `right`,
 *   and 12-column grid (`row` + `col-*`) layouts.
 * - Percentage (`100%`, `80%`, `50%`), `px`, `em`, `rem`, `calc(...)`, `max-width`, `max-height`,
 *   `min-width`, `min-height`, `aspect-ratio` (`928 / 270`, `420 / 289`), and `margin: ... auto` centering.
 * - `position: absolute` / `fixed` with `%` and `px` offsets (`left: 50%; top: 50%`) and `transform: translate(-50%, -50%)`.
 * - Automatic bounds synchronization for [AlienWidgetNode.wrappedUnderlayNativeImpl] (`UnalienContainer` and
 *   extension surfaces attached below the `Canvas2D`).
 */
class AlienLayoutEngine {

    companion object {
        fun layoutTree(
            root: AlienWidgetNode,
            viewportWidth: Float,
            viewportHeight: Float,
            updateTracker: AlienUpdateRegionTracker? = null
        ) {
            AlienLayoutEngine().performLayout(
                root = root,
                viewportWidth = viewportWidth.roundToInt(),
                viewportHeight = viewportHeight.roundToInt(),
                updateTracker = updateTracker
            )
        }
    }

    /**
     * Performs a complete measure + arrange pass rooted at [root] for `(viewportWidth, viewportHeight)`.
     */
    fun performLayout(
        root: AlienWidgetNode,
        viewportWidth: Int,
        viewportHeight: Int,
        updateTracker: AlienUpdateRegionTracker? = null
    ) {
        val safeW = viewportWidth.coerceAtLeast(1)
        val safeH = viewportHeight.coerceAtLeast(1)
        measureNode(root, safeW, safeH)
        arrangeNode(root, Rect(0, 0, safeW, safeH), updateTracker)
    }

    /**
     * Pass 1: Measures the intrinsic/preferred size of [node] given `(availableWidth, availableHeight)`.
     */
    fun measureNode(node: AlienWidgetNode, availableWidth: Int, availableHeight: Int): Size {
        val style = node.resolveVisualStyle()
        if (!isNodeRendered(node, style)) {
            node.measuredSize = Size(0, 0)
            return node.measuredSize
        }

        val explicitPref = node.preferredSize
        val cssW = resolveCssDimensionPx(style.widthCss, availableWidth, style.fontSizePx)
            ?: explicitPref?.width?.takeIf { it > 0 }
        val cssH = resolveCssDimensionPx(style.heightCss, availableHeight, style.fontSizePx)
            ?: explicitPref?.height?.takeIf { it > 0 }
        val minW = resolveCssDimensionPx(style.minWidthCss, availableWidth, style.fontSizePx) ?: 0
        val minH = resolveCssDimensionPx(style.minHeightCss, availableHeight, style.fontSizePx) ?: 0
        val maxW = resolveCssDimensionPx(style.maxWidthCss, availableWidth, style.fontSizePx)
        val maxH = resolveCssDimensionPx(style.maxHeightCss, availableHeight, style.fontSizePx)

        // Effective available space for children
        var containerTargetW = cssW ?: maxW ?: availableWidth
        if (maxW != null) containerTargetW = min(containerTargetW, maxW)
        var containerTargetH = cssH ?: maxH ?: availableHeight
        if (maxH != null) containerTargetH = min(containerTargetH, maxH)
        if (style.aspectRatio != null && style.aspectRatio > 0f) {
            if (cssH == null && containerTargetW > 0) {
                val aspectH = (containerTargetW / style.aspectRatio).roundToInt()
                containerTargetH = if (maxH != null) min(aspectH, maxH) else aspectH
            }
        }

        val innerAvailW = (containerTargetW - style.padding.left - style.padding.right).coerceAtLeast(0)
        val innerAvailH = (containerTargetH - style.padding.top - style.padding.bottom).coerceAtLeast(0)

        for (child in node.children) {
            val childStyle = child.resolveVisualStyle()
            if (isNodeRendered(child, childStyle)) {
                measureNode(child, innerAvailW, innerAvailH)
            } else {
                child.measuredSize = Size(0, 0)
            }
        }

        val customMeasureFn = node.customMeasure
        val rawMeasured = if (customMeasureFn != null) {
            customMeasureFn(innerAvailW, innerAvailH, style)
        } else {
            defaultMeasureContainerOrLeaf(node, innerAvailW, innerAvailH, style)
        }

        var finalW = when {
            cssW != null && cssW > 0 -> cssW
            node.isUnderlayExtensionSlot -> {
                val nativeBounds = node.wrappedUnderlayNativeImpl?.getBounds()
                if (nativeBounds != null && nativeBounds.width > 0) nativeBounds.width else max(rawMeasured.width, 160)
            }
            else -> rawMeasured.width
        }
        finalW = finalW.coerceAtLeast(minW)
        if (maxW != null) finalW = finalW.coerceAtMost(maxW)

        var finalH = when {
            cssH != null && cssH > 0 -> cssH
            style.aspectRatio != null && style.aspectRatio > 0f && finalW > 0 ->
                (finalW / style.aspectRatio).roundToInt()
            node.isUnderlayExtensionSlot -> {
                val nativeBounds = node.wrappedUnderlayNativeImpl?.getBounds()
                if (nativeBounds != null && nativeBounds.height > 0) nativeBounds.height else max(rawMeasured.height, 120)
            }
            else -> rawMeasured.height
        }
        finalH = finalH.coerceAtLeast(minH)
        if (maxH != null) finalH = finalH.coerceAtMost(maxH)

        // If aspect-ratio was constrained by maxH, adjust finalW to preserve aspect ratio when width wasn't fixed in px
        if (style.aspectRatio != null && style.aspectRatio > 0f && maxH != null && finalH == maxH &&
            (style.widthCss == null || style.widthCss.endsWith("%"))
        ) {
            val ratioW = (finalH * style.aspectRatio).roundToInt()
            finalW = if (maxW != null) min(ratioW, maxW) else ratioW
        }

        node.measuredSize = Size(finalW.coerceAtLeast(0), finalH.coerceAtLeast(0))
        return node.measuredSize
    }

    /**
     * Pass 2: Assigns final [Rect] bounds to [node] and recursively arranges its visible children.
     */
    fun arrangeNode(
        node: AlienWidgetNode,
        assignedBounds: Rect,
        updateTracker: AlienUpdateRegionTracker? = null
    ) {
        val style = node.resolveVisualStyle()
        if (!isNodeRendered(node, style)) {
            val oldBounds = node.bounds
            if (oldBounds.width > 0 || oldBounds.height > 0) {
                node.bounds = Rect(0, 0, 0, 0)
                updateTracker?.markBoundsTransitionDirty(oldBounds, node.bounds)
            }
            node.layoutDirty = false
            return
        }

        val oldBounds = node.bounds
        node.bounds = assignedBounds
        node.layoutDirty = false

        if (oldBounds != assignedBounds) {
            updateTracker?.markBoundsTransitionDirty(oldBounds, assignedBounds)
        }

        // Synchronize underlay native widget bounds if this is an UnalienContainer or Extension slot
        val underlay = node.wrappedUnderlayNativeImpl
        if (underlay != null) {
            underlay.setPreferredSize(Size(assignedBounds.width.coerceAtLeast(1), assignedBounds.height.coerceAtLeast(1)))
        }

        if (node.children.isEmpty()) return

        val contentX = assignedBounds.x + style.padding.left - node.scrollOffsetX
        val contentY = assignedBounds.y + style.padding.top - node.scrollOffsetY
        val contentW = (assignedBounds.width - style.padding.left - style.padding.right).coerceAtLeast(0)
        val contentH = (assignedBounds.height - style.padding.top - style.padding.bottom).coerceAtLeast(0)

        val isHorizontal = isHorizontalContainer(node, style)
        val gapPx = resolveGapPx(node)

        if (isHorizontal) {
            arrangeHorizontal(node, style, assignedBounds, contentX, contentY, contentW, contentH, gapPx, updateTracker)
        } else {
            arrangeVertical(node, style, assignedBounds, contentX, contentY, contentW, contentH, gapPx, updateTracker)
        }
    }

    private fun defaultMeasureContainerOrLeaf(
        node: AlienWidgetNode,
        innerAvailW: Int,
        innerAvailH: Int,
        style: AlienResolvedVisualStyle
    ): Size {
        val visibleChildren = node.children.filter { isNodeRendered(it) }
        if (visibleChildren.isEmpty()) {
            val intrinsicW = node.intrinsicWidth.roundToInt().coerceAtLeast(0)
            val intrinsicH = node.intrinsicHeight.roundToInt().coerceAtLeast(0)
            val defaultW = style.padding.left + style.padding.right +
                if (node.componentClassPrefix == "alien-root" || node.componentClassPrefix == "page") innerAvailW else intrinsicW
            val defaultH = style.padding.top + style.padding.bottom +
                if (node.componentClassPrefix == "alien-root" || node.componentClassPrefix == "page") innerAvailH else intrinsicH
            return Size(defaultW, defaultH)
        }

        val isHorizontal = isHorizontalContainer(node, style)
        val gapPx = resolveGapPx(node)
        var accumW = 0
        var accumH = 0

        // Separate positioned children from flow children during intrinsic measurement
        val flowChildren = visibleChildren.filter {
            val p = it.resolveVisualStyle().position
            p != "absolute" && p != "fixed"
        }

        if (flowChildren.isEmpty()) {
            // Container only has positioned children (e.g. `.page` containing `.menu`)
            return Size(
                innerAvailW + style.padding.left + style.padding.right,
                innerAvailH + style.padding.top + style.padding.bottom
            )
        }

        if (isHorizontal) {
            for ((idx, child) in flowChildren.withIndex()) {
                val childStyle = child.resolveVisualStyle()
                val cw = child.measuredSize.width + childStyle.margin.left + childStyle.margin.right
                val ch = child.measuredSize.height + childStyle.margin.top + childStyle.margin.bottom
                accumW += cw + (if (idx > 0) gapPx else 0)
                accumH = max(accumH, ch)
            }
        } else {
            var idx = 0
            while (idx < flowChildren.size) {
                val child = flowChildren[idx]
                val childStyle = child.resolveVisualStyle()
                if (isInlineFlowChild(child, childStyle)) {
                    var rowW = 0
                    var rowH = 0
                    while (idx < flowChildren.size) {
                        val c = flowChildren[idx]
                        val cs = c.resolveVisualStyle()
                        if (!isInlineFlowChild(c, cs)) break
                        val cw = c.measuredSize.width + cs.margin.left + cs.margin.right
                        val ch = c.measuredSize.height + cs.margin.top + cs.margin.bottom
                        if (innerAvailW > 0 && rowW > 0 && rowW + cw > innerAvailW) {
                            accumW = max(accumW, rowW)
                            accumH += rowH + gapPx
                            rowW = 0
                            rowH = 0
                        }
                        rowW += cw + (if (rowW > 0) gapPx else 0)
                        rowH = max(rowH, ch)
                        idx++
                    }
                    accumW = max(accumW, rowW)
                    accumH += rowH + (if (idx < flowChildren.size) gapPx else 0)
                } else {
                    val isHr = child.tagName == "hr" || child.componentClassPrefix == "hr"
                    val cw = (if (isHr && style.widthCss == null && !shouldStretchHorizontally(node, style)) 0 else child.measuredSize.width) +
                        childStyle.margin.left + childStyle.margin.right
                    val ch = child.measuredSize.height + childStyle.margin.top + childStyle.margin.bottom
                    accumW = max(accumW, cw)
                    accumH += ch + (if (idx > 0) gapPx else 0)
                    idx++
                }
            }
        }

        val totalW = accumW + style.padding.left + style.padding.right
        val totalH = accumH + style.padding.top + style.padding.bottom
        return Size(totalW, totalH)
    }

    private fun arrangeVertical(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        assignedBounds: Rect,
        contentX: Int,
        contentY: Int,
        contentW: Int,
        contentH: Int,
        gapPx: Int,
        updateTracker: AlienUpdateRegionTracker?
    ) {
        val visibleChildren = node.children.filter { isNodeRendered(it) }
        if (visibleChildren.isEmpty()) return

        // Handle LazyGrid / LazyGridTree multi-column layout
        if (node.componentClassPrefix == "lazy-grid") {
            val cols = (node.attributes["data-columns"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
            if (cols > 1) {
                val totalGapW = gapPx * (cols - 1).coerceAtLeast(0)
                val colW = ((contentW - totalGapW) / cols).coerceAtLeast(1)
                var rowY = contentY
                var idx = 0
                while (idx < visibleChildren.size) {
                    val rowEnd = (idx + cols).coerceAtMost(visibleChildren.size)
                    var rowH = 34
                    for (c in idx until rowEnd) {
                        rowH = max(rowH, visibleChildren[c].measuredSize.height)
                    }
                    for (c in idx until rowEnd) {
                        val colIdx = c - idx
                        val cellX = contentX + colIdx * (colW + gapPx)
                        arrangeNode(visibleChildren[c], Rect(cellX, rowY, colW, rowH), updateTracker)
                    }
                    rowY += rowH + gapPx
                    idx = rowEnd
                }
                return
            }
        }

        // Handle SplitPane vertical split
        if (node.componentClassPrefix == "split-pane" && visibleChildren.size == 2) {
            val ratio = (node.attributes["data-divider-ratio"]?.toFloatOrNull() ?: 0.5f).coerceIn(0.05f, 0.95f)
            val dividerH = 6
            val firstH = ((contentH - dividerH) * ratio).toInt().coerceAtLeast(0)
            val secondH = (contentH - dividerH - firstH).coerceAtLeast(0)
            arrangeNode(visibleChildren[0], Rect(contentX, contentY, contentW, firstH), updateTracker)
            arrangeNode(visibleChildren[1], Rect(contentX, contentY + firstH + dividerH, contentW, secondH), updateTracker)
            return
        }

        // Compute flex-grow vertical height share for `display: flex; flex-direction: column` (e.g. `.a4` + `.page-holder`)
        val flowChildren = visibleChildren.filter {
            val p = it.resolveVisualStyle().position
            p != "absolute" && p != "fixed"
        }
        val totalFlexGrow = flowChildren.sumOf { it.resolveVisualStyle().flexGrow.toDouble() }.toFloat()
        val fixedFlowHeight = if (totalFlexGrow > 0f) {
            flowChildren.sumOf { c ->
                val cs = c.resolveVisualStyle()
                val h = if (cs.flexGrow > 0f) 0 else c.measuredSize.height
                h + cs.margin.top + cs.margin.bottom
            } + gapPx * (flowChildren.size - 1).coerceAtLeast(0)
        } else {
            0
        }
        val remainingFlexH = (contentH - fixedFlowHeight).coerceAtLeast(0)

        var cursorY = contentY
        var i = 0
        while (i < visibleChildren.size) {
            val child = visibleChildren[i]
            val childStyle = child.resolveVisualStyle()

            if (childStyle.position == "absolute" || childStyle.position == "fixed") {
                arrangePositionedChild(child, childStyle, assignedBounds, contentX, contentY, updateTracker)
                i++
                continue
            }

            // Group consecutive inline / inline-block / floated children into horizontal rows
            if (isInlineFlowChild(child, childStyle)) {
                val inlineGroup = mutableListOf<Pair<AlienWidgetNode, AlienResolvedVisualStyle>>()
                while (i < visibleChildren.size) {
                    val c = visibleChildren[i]
                    val cs = c.resolveVisualStyle()
                    if (cs.position == "absolute" || cs.position == "fixed" || !isInlineFlowChild(c, cs)) break
                    inlineGroup.add(c to cs)
                    i++
                }
                cursorY = arrangeInlineFlowGroup(
                    parentStyle = style,
                    group = inlineGroup,
                    contentX = contentX,
                    startY = cursorY,
                    contentW = contentW,
                    gapPx = gapPx,
                    updateTracker = updateTracker
                )
                continue
            }

            val m = childStyle.margin
            val stretchWidth = shouldStretchHorizontally(child, childStyle)
            var childW = when {
                childStyle.widthCss != null ->
                    resolveCssDimensionPx(childStyle.widthCss, contentW, childStyle.fontSizePx) ?: child.measuredSize.width
                stretchWidth ->
                    (contentW - m.left - m.right).coerceAtLeast(child.measuredSize.width.coerceAtMost(contentW))
                else ->
                    child.measuredSize.width.coerceAtLeast(1)
            }
            resolveCssDimensionPx(childStyle.maxWidthCss, contentW, childStyle.fontSizePx)?.let { maxW ->
                childW = childW.coerceAtMost(maxW)
            }

            var childH = if (totalFlexGrow > 0f && childStyle.flexGrow > 0f) {
                ((remainingFlexH * (childStyle.flexGrow / totalFlexGrow)).roundToInt()).coerceAtLeast(child.measuredSize.height)
            } else if (childStyle.aspectRatio != null && childStyle.aspectRatio > 0f && childStyle.heightCss == null) {
                (childW / childStyle.aspectRatio).roundToInt().coerceAtLeast(1)
            } else {
                child.measuredSize.height.coerceAtLeast(1)
            }
            resolveCssDimensionPx(childStyle.maxHeightCss, contentH, childStyle.fontSizePx)?.let { maxH ->
                childH = childH.coerceAtMost(maxH)
            }

            val childX = when {
                childStyle.marginLeftAuto && childStyle.marginRightAuto ->
                    contentX + ((contentW - childW) / 2).coerceAtLeast(0)
                childStyle.marginLeftAuto ->
                    contentX + (contentW - childW - m.right).coerceAtLeast(0)
                style.textAlign == "center" && childW < contentW && !stretchWidth ->
                    contentX + ((contentW - childW) / 2).coerceAtLeast(0)
                style.textAlign == "right" && childW < contentW && !stretchWidth ->
                    contentX + (contentW - childW - m.right).coerceAtLeast(0)
                else ->
                    contentX + m.left
            }
            val childY = cursorY + m.top

            arrangeNode(child, Rect(childX, childY, childW, childH), updateTracker)
            cursorY = childY + childH + m.bottom + gapPx
            i++
        }
    }

    private fun arrangeInlineFlowGroup(
        parentStyle: AlienResolvedVisualStyle,
        group: List<Pair<AlienWidgetNode, AlienResolvedVisualStyle>>,
        contentX: Int,
        startY: Int,
        contentW: Int,
        gapPx: Int,
        updateTracker: AlienUpdateRegionTracker?
    ): Int {
        if (group.isEmpty()) return startY

        // Split into wrapped lines based on contentW
        val lines = mutableListOf<MutableList<Triple<AlienWidgetNode, AlienResolvedVisualStyle, Size>>>()
        var currentLine = mutableListOf<Triple<AlienWidgetNode, AlienResolvedVisualStyle, Size>>()
        var currentLineW = 0

        for ((c, cs) in group) {
            val resolvedW = resolveCssDimensionPx(cs.widthCss, contentW, cs.fontSizePx) ?: c.measuredSize.width
            val resolvedH = if (cs.aspectRatio != null && cs.aspectRatio > 0f && cs.heightCss == null) {
                (resolvedW / cs.aspectRatio).roundToInt()
            } else {
                resolveCssDimensionPx(cs.heightCss, contentW, cs.fontSizePx) ?: c.measuredSize.height
            }
            val itemOuterW = resolvedW + cs.margin.left + cs.margin.right
            if (contentW > 0 && currentLine.isNotEmpty() && currentLineW + itemOuterW > contentW) {
                lines.add(currentLine)
                currentLine = mutableListOf()
                currentLineW = 0
            }
            currentLine.add(Triple(c, cs, Size(resolvedW, resolvedH)))
            currentLineW += itemOuterW
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        var cursorY = startY
        for (line in lines) {
            var lineH = 0
            var normalTotalW = 0
            for ((_, cs, sz) in line) {
                lineH = max(lineH, sz.height + cs.margin.top + cs.margin.bottom)
                if (cs.floatMode == "none") {
                    normalTotalW += sz.width + cs.margin.left + cs.margin.right
                }
            }

            var leftFloatCursor = contentX
            var rightFloatCursor = contentX + contentW
            var normalCursor = when (parentStyle.textAlign) {
                "center" -> contentX + ((contentW - normalTotalW) / 2).coerceAtLeast(0)
                "right", "end" -> contentX + (contentW - normalTotalW).coerceAtLeast(0)
                else -> contentX
            }

            for ((c, cs, sz) in line) {
                val m = cs.margin
                val cy = cursorY + m.top
                val cx = when (cs.floatMode) {
                    "left" -> {
                        val x = leftFloatCursor + m.left
                        leftFloatCursor = x + sz.width + m.right
                        x
                    }
                    "right" -> {
                        val x = rightFloatCursor - m.right - sz.width
                        rightFloatCursor = x - m.left
                        x
                    }
                    else -> {
                        val x = normalCursor + m.left
                        normalCursor = x + sz.width + m.right
                        x
                    }
                }
                arrangeNode(c, Rect(cx, cy, sz.width, sz.height), updateTracker)
            }
            cursorY += lineH + gapPx
        }
        return cursorY
    }

    private fun arrangePositionedChild(
        child: AlienWidgetNode,
        childStyle: AlienResolvedVisualStyle,
        parentBounds: Rect,
        contentX: Int,
        contentY: Int,
        updateTracker: AlienUpdateRegionTracker?
    ) {
        val availW = parentBounds.width.coerceAtLeast(1)
        val availH = parentBounds.height.coerceAtLeast(1)

        var absW = resolveCssDimensionPx(childStyle.widthCss, availW, childStyle.fontSizePx)
            ?: child.preferredSize?.width?.takeIf { it > 0 }
            ?: child.measuredSize.width.coerceAtLeast(1)
        resolveCssDimensionPx(childStyle.maxWidthCss, availW, childStyle.fontSizePx)?.let { maxW ->
            absW = absW.coerceAtMost(maxW)
        }
        resolveCssDimensionPx(childStyle.minWidthCss, availW, childStyle.fontSizePx)?.let { minW ->
            absW = absW.coerceAtLeast(minW)
        }

        // Re-measure child against its constrained `absW` if its measured width changed
        if (absW != child.measuredSize.width) {
            measureNode(child, absW, availH)
        }

        var absH = resolveCssDimensionPx(childStyle.heightCss, availH, childStyle.fontSizePx)
            ?: child.preferredSize?.height?.takeIf { it > 0 }
            ?: child.measuredSize.height.coerceAtLeast(1)
        resolveCssDimensionPx(childStyle.maxHeightCss, availH, childStyle.fontSizePx)?.let { maxH ->
            absH = absH.coerceAtMost(maxH)
        }
        resolveCssDimensionPx(childStyle.minHeightCss, availH, childStyle.fontSizePx)?.let { minH ->
            absH = absH.coerceAtLeast(minH)
        }

        val leftPx = resolveCssDimensionPx(childStyle.leftCss, availW, childStyle.fontSizePx)
        val rightPx = resolveCssDimensionPx(childStyle.rightCss, availW, childStyle.fontSizePx)
        val topPx = resolveCssDimensionPx(childStyle.topCss, availH, childStyle.fontSizePx)
        val bottomPx = resolveCssDimensionPx(childStyle.bottomCss, availH, childStyle.fontSizePx)

        var absX = when {
            leftPx != null -> parentBounds.x + leftPx
            rightPx != null -> parentBounds.right - rightPx - absW
            else -> contentX + childStyle.margin.left
        }
        var absY = when {
            topPx != null -> parentBounds.y + topPx
            bottomPx != null -> parentBounds.bottom - bottomPx - absH
            else -> contentY + childStyle.margin.top
        }

        // Apply CSS `transform: translate(dx, dy)` (e.g. `translate(-50%, -50%)`) directly to positioned bounds
        val (dx, dy) = childStyle.transform2D.resolveTranslationPx(absW, absH)
        absX += dx.roundToInt()
        absY += dy.roundToInt()

        arrangeNode(child, Rect(absX, absY, absW, absH), updateTracker)
    }

    private fun arrangeHorizontal(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        assignedBounds: Rect,
        contentX: Int,
        contentY: Int,
        contentW: Int,
        contentH: Int,
        gapPx: Int,
        updateTracker: AlienUpdateRegionTracker?
    ) {
        val visibleChildren = node.children.filter { isNodeRendered(it) }
        if (visibleChildren.isEmpty()) return

        // Arrange any positioned children first
        val flowChildren = mutableListOf<AlienWidgetNode>()
        for (child in visibleChildren) {
            val cs = child.resolveVisualStyle()
            if (cs.position == "absolute" || cs.position == "fixed") {
                arrangePositionedChild(child, cs, assignedBounds, contentX, contentY, updateTracker)
            } else {
                flowChildren.add(child)
            }
        }
        if (flowChildren.isEmpty()) return

        // Handle SplitPane horizontal split
        if (node.componentClassPrefix == "split-pane" && flowChildren.size == 2) {
            val ratio = (node.attributes["data-divider-ratio"]?.toFloatOrNull() ?: 0.5f).coerceIn(0.05f, 0.95f)
            val dividerW = 6
            val firstW = ((contentW - dividerW) * ratio).toInt().coerceAtLeast(0)
            val secondW = (contentW - dividerW - firstW).coerceAtLeast(0)
            arrangeNode(flowChildren[0], Rect(contentX, contentY, firstW, contentH), updateTracker)
            arrangeNode(flowChildren[1], Rect(contentX + firstW + dividerW, contentY, secondW, contentH), updateTracker)
            return
        }

        // Check if this is a `display: table` (e.g. `.a4 .steps`) or Bootstrap 12-column `.row`
        val isTable = style.display == "table"
        val isRow = node.componentClassPrefix == "row" || "row" in node.styleSnapshot.customClasses
        if (isTable || isRow) {
            var cursorX = contentX
            val count = flowChildren.size.coerceAtLeast(1)
            val effectiveGap = if (isTable) 0 else gapPx
            val totalGap = effectiveGap * (count - 1).coerceAtLeast(0)
            val availColsW = (contentW - totalGap).coerceAtLeast(0)
            for (child in flowChildren) {
                val colSpan = if (isRow) parseBootstrapColSpan(child) else null
                val childW = if (colSpan != null) {
                    ((availColsW * colSpan) / 12f).toInt().coerceAtLeast(1)
                } else {
                    (availColsW / count).coerceAtLeast(1)
                }
                val childH = max(child.measuredSize.height, contentH.coerceAtMost(max(child.measuredSize.height, 32)))
                arrangeNode(child, Rect(cursorX, contentY, childW, childH), updateTracker)
                cursorX += childW + effectiveGap
            }
            return
        }

        // Standard HStack / Flex-Row
        val totalChildrenW = flowChildren.sumOf {
            val m = it.resolveVisualStyle().margin
            it.measuredSize.width + m.left + m.right
        } + gapPx * (flowChildren.size - 1).coerceAtLeast(0)

        var cursorX = when (node.styleSnapshot.justifyContent) {
            JustifyContent.CENTER -> contentX + ((contentW - totalChildrenW) / 2).coerceAtLeast(0)
            JustifyContent.END -> contentX + (contentW - totalChildrenW).coerceAtLeast(0)
            else -> contentX
        }

        for (child in flowChildren) {
            val m = child.resolveVisualStyle().margin
            val childX = cursorX + m.left
            val childY = contentY + m.top
            val childW = child.measuredSize.width.coerceAtLeast(1)
            val childH = max(child.measuredSize.height, 1)
            arrangeNode(child, Rect(childX, childY, childW, childH), updateTracker)
            cursorX = childX + childW + m.right + gapPx
        }
    }

    private fun isNodeRendered(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle = node.resolveVisualStyle()
    ): Boolean = node.visible && style.display != "none"

    private fun isInlineFlowChild(child: AlienWidgetNode, style: AlienResolvedVisualStyle): Boolean {
        if (style.floatMode == "left" || style.floatMode == "right") return true
        return style.display == "inline-block" || style.display == "inline"
    }

    private fun isHorizontalContainer(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle = node.resolveVisualStyle()
    ): Boolean {
        if (style.display == "table") return true
        val prefix = node.componentClassPrefix.lowercase()
        val classes = node.styleSnapshot.customClasses
        val rawFlexDir = node.computedStyle?.rawDeclarations?.get("flex-direction")?.lowercase()
        if (rawFlexDir == "column" || rawFlexDir == "column-reverse" || "flex-column" in classes) {
            return false
        }
        if (prefix == "split-pane") {
            return node.attributes["data-orientation"] != "VERTICAL"
        }
        if (prefix == "hstack" || prefix == "row" || prefix == "navbar" || prefix == "menubar" || prefix == "btn-group" || prefix == "input-group") {
            return true
        }
        if ("hstack" in classes || "row" in classes || "flex-row" in classes || "btn-group" in classes || rawFlexDir == "row") {
            return true
        }
        if (node.styleSnapshot.flexDirection == FlexDirection.ROW || node.styleSnapshot.flexDirection == FlexDirection.ROW_REVERSE) {
            return true
        }
        if ((node.styleSnapshot.display == DisplayMode.FLEX || style.display == "flex") &&
            node.styleSnapshot.flexDirection == null && prefix != "vstack"
        ) {
            return true
        }
        return false
    }

    private fun shouldStretchHorizontally(
        child: AlienWidgetNode,
        style: AlienResolvedVisualStyle
    ): Boolean {
        if (style.position == "absolute" || style.position == "fixed") {
            return !style.leftCss.isNullOrBlank() && !style.rightCss.isNullOrBlank()
        }
        if (child.preferredSize != null && child.preferredSize!!.width > 0) return false
        if (style.widthCss != null) return style.widthCss.trim() == "100%"
        if (style.marginLeftAuto || style.marginRightAuto) return false
        if (style.display == "inline-block" || style.display == "inline" || style.floatMode != "none") return false
        val prefix = child.componentClassPrefix.lowercase()
        if (prefix == "btn" || prefix == "badge" || prefix == "spinner-border" || prefix == "form-check" || child.tagName == "img") {
            return style.display == "block" || "w-100" in child.styleSnapshot.customClasses
        }
        return true
    }

    private fun resolveGapPx(node: AlienWidgetNode): Int {
        for (cls in node.styleSnapshot.customClasses) {
            if (cls.startsWith("gap-")) {
                val token = cls.removePrefix("gap-")
                return when (token) {
                    "0" -> 0
                    "1" -> 4
                    "2" -> 8
                    "3" -> 16
                    "4" -> 24
                    "5" -> 48
                    else -> 8
                }
            }
        }
        val prefix = node.componentClassPrefix.lowercase()
        return if (prefix == "vstack" || prefix == "hstack") 8 else 0
    }

    private fun parseBootstrapColSpan(child: AlienWidgetNode): Int? {
        for (cls in child.styleSnapshot.customClasses) {
            if (cls.startsWith("col-")) {
                val tail = cls.substringAfterLast('-')
                tail.toIntOrNull()?.let { if (it in 1..12) return it }
            }
        }
        return null
    }

    internal fun resolveCssDimensionPx(raw: String?, availablePx: Int, fontSizePx: Float): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("!important", "").trim().lowercase()
        if (cleaned == "auto" || cleaned == "none") return null
        if (cleaned.startsWith("calc(") && cleaned.endsWith(")")) {
            val expr = cleaned.removePrefix("calc(").removeSuffix(")").trim()
            val minusIdx = expr.indexOf(" - ")
            if (minusIdx > 0) {
                val left = resolveCssDimensionPx(expr.substring(0, minusIdx), availablePx, fontSizePx) ?: 0
                val right = resolveCssDimensionPx(expr.substring(minusIdx + 3), availablePx, fontSizePx) ?: 0
                return (left - right).coerceAtLeast(0)
            }
            val plusIdx = expr.indexOf(" + ")
            if (plusIdx > 0) {
                val left = resolveCssDimensionPx(expr.substring(0, plusIdx), availablePx, fontSizePx) ?: 0
                val right = resolveCssDimensionPx(expr.substring(plusIdx + 3), availablePx, fontSizePx) ?: 0
                return (left + right).coerceAtLeast(0)
            }
        }
        return AlienCssStyleResolver.parseLengthPx(cleaned, fontSizePx, availablePx.toFloat())?.roundToInt()
    }
}
