package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.css.CssVerticalAlign
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
 *   and responsive 12-column grid (`row` + `col-*` / `col-sm-*` / `col-md-*` / `col-lg-*` / `col-xl-*` / `col-xxl-*`) layouts.
 * - Cross-axis shrink-to-siblings (`data-stack-shrink-cross="true"` / `StackConstraint.shrinkCrossAxis`).
 * - Percentage (`100%`, `80%`, `50%`), `px`, `em`, `rem`, `calc(...)`, `max-width`, `max-height`,
 *   `min-width`, `min-height`, `aspect-ratio` (`928 / 270`, `420 / 289`), and `margin: ... auto` centering.
 * - `position: relative` (`left` / `top` / `right` / `bottom` offsets) and `position: absolute` / `fixed` with
 *   `%` and `px` offsets (`left: 50%; top: 50%`) and `transform: translate(-50%, -50%)`.
 * - Automatic bounds synchronization for [AlienWidgetNode.wrappedUnderlayNativeImpl] (`UnalienContainer` and
 *   extension surfaces attached below the `Canvas2D`).
 */
class AlienLayoutEngine {

    private var currentViewportWidthPx: Int = 1280
    private var currentViewportHeightPx: Int = 720

    companion object {
        private val BOOTSTRAP_COL_BREAKPOINTS: List<Pair<String, Int>> = listOf(
            "" to 0,
            "sm-" to 576,
            "md-" to 768,
            "lg-" to 992,
            "xl-" to 1200,
            "xxl-" to 1400
        )

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
        currentViewportWidthPx = safeW
        currentViewportHeightPx = safeH
        val session = root.session
        val startedPass = session != null && session.activeStylePassId < 0L
        if (startedPass) session!!.beginStylePass()
        try {
            measureNode(root, safeW, safeH)
            arrangeNode(root, Rect(0, 0, safeW, safeH), updateTracker)
        } finally {
            if (startedPass) session!!.endStylePass()
        }
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

        val isShrinkCross = isShrinkCrossAxisChild(node)
        val parentIsHorizontal = node.parent?.let { isHorizontalContainer(it) } == true

        val effectiveWidthCss = style.widthCss?.takeUnless {
            isShrinkCross && !parentIsHorizontal && (it.trim() == "0" || it.trim() == "0px")
        }
        val effectiveHeightCss = style.heightCss?.takeUnless {
            isShrinkCross && parentIsHorizontal && (it.trim() == "0" || it.trim() == "0px")
        }
        val effectiveMinWidthCss = style.minWidthCss?.takeUnless {
            isShrinkCross && !parentIsHorizontal && it.trim() == "100%"
        }
        val effectiveMinHeightCss = style.minHeightCss?.takeUnless {
            isShrinkCross && parentIsHorizontal && it.trim() == "100%"
        }
        val effectiveMaxWidthCss = style.maxWidthCss?.takeUnless {
            isShrinkCross && !parentIsHorizontal && it.trim() == "100%"
        }
        val effectiveMaxHeightCss = style.maxHeightCss?.takeUnless {
            isShrinkCross && parentIsHorizontal && it.trim() == "100%"
        }

        val padBorderH = style.padding.left + style.padding.right +
            style.borderLeftWidthPx.roundToInt() + style.borderRightWidthPx.roundToInt()
        val padBorderV = style.padding.top + style.padding.bottom +
            style.borderTopWidthPx.roundToInt() + style.borderBottomWidthPx.roundToInt()
        val isContentBox = style.boxSizing == CssBoxSizing.CONTENT_BOX

        fun outerDimFromCss(rawCss: String?, availPx: Int, padBorderPx: Int): Int? {
            val rawPx = resolveCssDimensionPx(rawCss, availPx, style.fontSizePx) ?: return null
            return if (isContentBox) rawPx + padBorderPx else rawPx
        }

        val explicitPref = node.preferredSize
        val cssW = outerDimFromCss(effectiveWidthCss, availableWidth, padBorderH)
            ?: explicitPref?.width?.takeIf { it > 0 }
        val cssH = outerDimFromCss(effectiveHeightCss, availableHeight, padBorderV)
            ?: explicitPref?.height?.takeIf { it > 0 }
        val minW = outerDimFromCss(effectiveMinWidthCss, availableWidth, padBorderH) ?: 0
        val minH = outerDimFromCss(effectiveMinHeightCss, availableHeight, padBorderV) ?: 0
        val maxW = outerDimFromCss(effectiveMaxWidthCss, availableWidth, padBorderH)
        val maxH = outerDimFromCss(effectiveMaxHeightCss, availableHeight, padBorderV)

        // Effective available space for children
        var containerTargetW = cssW ?: maxW ?: availableWidth
        if (maxW != null) containerTargetW = min(containerTargetW, maxW)
        var containerTargetH = cssH ?: maxH ?: availableHeight
        if (maxH != null) containerTargetH = min(containerTargetH, maxH)
        if (style.aspectRatio != null && style.aspectRatio > 0f) {
            if (cssH == null && containerTargetW > 0) {
                val aspectH = (containerTargetW / style.aspectRatio).roundToInt()
                containerTargetH = if (maxH != null) min(aspectH, maxH) else aspectH
            } else if (cssW == null && containerTargetH > 0) {
                val aspectW = (containerTargetH * style.aspectRatio).roundToInt()
                containerTargetW = if (maxW != null) min(aspectW, maxW) else aspectW
            }
        }

        val innerAvailW = (containerTargetW - padBorderH).coerceAtLeast(0)
        val innerAvailH = (containerTargetH - padBorderV).coerceAtLeast(0)

        val isRow = isBootstrapGridRow(node)
        val viewportW = resolveViewportWidth(node)

        for (child in node.children) {
            val childStyle = child.resolveVisualStyle()
            if (isNodeRendered(child, childStyle)) {
                val childAvailW = if (isRow && innerAvailW > 0) {
                    val colSpan = resolveBootstrapColSpan(child, viewportW)
                    if (colSpan != null) ((innerAvailW * colSpan) / 12f).toInt().coerceAtLeast(1) else innerAvailW
                } else {
                    innerAvailW
                }
                measureNode(child, childAvailW, innerAvailH)
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
            cssH != null && cssH > 0 && style.aspectRatio != null && style.aspectRatio > 0f && customMeasureFn == null ->
                (cssH * style.aspectRatio).roundToInt().coerceAtLeast(1)
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
            (effectiveWidthCss == null || effectiveWidthCss.endsWith("%"))
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

        val effectiveBounds = when (style.position) {
            "relative" -> {
                val parentW = (node.parent?.contentBounds?.width ?: assignedBounds.width).coerceAtLeast(1)
                val parentH = (node.parent?.contentBounds?.height ?: assignedBounds.height).coerceAtLeast(1)
                val dx = resolveSignedCssDimensionPx(style.leftCss, parentW, style.fontSizePx)
                    ?: resolveSignedCssDimensionPx(style.rightCss, parentW, style.fontSizePx)?.let { -it }
                    ?: 0
                val dy = resolveSignedCssDimensionPx(style.topCss, parentH, style.fontSizePx)
                    ?: resolveSignedCssDimensionPx(style.bottomCss, parentH, style.fontSizePx)?.let { -it }
                    ?: 0
                if (dx != 0 || dy != 0) {
                    Rect(assignedBounds.x + dx, assignedBounds.y + dy, assignedBounds.width, assignedBounds.height)
                } else {
                    assignedBounds
                }
            }
            "sticky" -> resolveStickyBounds(node, style, assignedBounds)
            else -> assignedBounds
        }

        val oldBounds = node.bounds
        node.bounds = effectiveBounds
        node.layoutDirty = false

        if (oldBounds != effectiveBounds) {
            updateTracker?.markBoundsTransitionDirty(oldBounds, effectiveBounds)
        }

        // Synchronize underlay native widget bounds if this is an UnalienContainer or Extension slot
        val underlay = node.wrappedUnderlayNativeImpl
        if (underlay != null) {
            underlay.setPreferredSize(Size(effectiveBounds.width.coerceAtLeast(1), effectiveBounds.height.coerceAtLeast(1)))
        }

        if (node.children.isEmpty()) {
            node.scrollWidth = effectiveBounds.width
            node.scrollHeight = effectiveBounds.height
            return
        }

        // Assign 1-based list item index to rendered <li> children
        var liNum = 0
        for (child in node.children) {
            if (!isNodeRendered(child)) {
                clearUnrenderedSubtreeBounds(child, updateTracker)
            } else if (child.tagName.equals("li", ignoreCase = true) ||
                child.resolveVisualStyle().display == "list-item"
            ) {
                liNum++
                child.listItemIndex = liNum
            }
        }

        val borderL = style.borderLeftWidthPx.roundToInt()
        val borderT = style.borderTopWidthPx.roundToInt()
        val borderR = style.borderRightWidthPx.roundToInt()
        val borderB = style.borderBottomWidthPx.roundToInt()
        val contentX = effectiveBounds.x + style.padding.left + borderL - node.scrollOffsetX
        val contentY = effectiveBounds.y + style.padding.top + borderT - node.scrollOffsetY
        val contentW = (effectiveBounds.width - style.padding.left - style.padding.right - borderL - borderR).coerceAtLeast(0)
        val contentH = (effectiveBounds.height - style.padding.top - style.padding.bottom - borderT - borderB).coerceAtLeast(0)

        val visibleFlow = node.children.filter {
            isNodeRendered(it) && it.resolveVisualStyle().position.let { p -> p != "absolute" && p != "fixed" }
        }
        val positionedChildren = node.children.filter {
            isNodeRendered(it) && it.resolveVisualStyle().position.let { p -> p == "absolute" || p == "fixed" }
        }
        for (posChild in positionedChildren) {
            arrangePositionedChild(posChild, posChild.resolveVisualStyle(), effectiveBounds, contentX, contentY, updateTracker)
        }

        val rowGap = if (style.rowGapPx > 0f) style.rowGapPx.roundToInt() else resolveGapPx(node, false, style)
        val colGap = if (style.columnGapPx > 0f) style.columnGapPx.roundToInt() else resolveGapPx(node, true, style)

        when {
            AlienGridLayoutSolver.isGridContainer(style, node) -> {
                AlienGridLayoutSolver.arrangeGridContainer(
                    engine = this,
                    node = node,
                    style = style,
                    contentX = contentX,
                    contentY = contentY,
                    contentW = contentW,
                    contentH = contentH,
                    gapRow = rowGap,
                    gapCol = colGap,
                    flowChildren = visibleFlow,
                    updateTracker = updateTracker
                )
            }
            AlienTableLayoutSolver.isFullHtmlTable(style, node) -> {
                AlienTableLayoutSolver.arrangeTable(
                    engine = this,
                    node = node,
                    style = style,
                    contentX = contentX,
                    contentY = contentY,
                    contentW = contentW,
                    updateTracker = updateTracker
                )
            }
            isW3cFlexContainer(node, style, visibleFlow) -> {
                AlienFlexLayoutSolver.arrangeFlexContainer(
                    engine = this,
                    node = node,
                    style = style,
                    assignedBounds = effectiveBounds,
                    contentX = contentX,
                    contentY = contentY,
                    contentW = contentW,
                    contentH = contentH,
                    gapRow = rowGap,
                    gapCol = colGap,
                    flowChildren = visibleFlow,
                    updateTracker = updateTracker
                )
            }
            isHorizontalContainer(node, style) -> {
                val gapPx = resolveGapPx(node, true, style)
                arrangeHorizontal(node, style, effectiveBounds, contentX, contentY, contentW, contentH, gapPx, updateTracker)
            }
            else -> {
                val gapPx = resolveGapPx(node, false, style)
                arrangeVertical(node, style, effectiveBounds, contentX, contentY, contentW, contentH, gapPx, updateTracker)
            }
        }

        // Track scroll content extents and clamp scroll offsets within valid bounds
        updateScrollExtents(node, style, effectiveBounds)
    }

    private fun resolveStickyBounds(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        assignedBounds: Rect
    ): Rect {
        var scrollParent = node.parent
        while (scrollParent != null && scrollParent.parent != null) {
            val ps = scrollParent.resolveVisualStyle()
            if (ps.overflowY != OverflowBehavior.VISIBLE ||
                ps.overflowX != OverflowBehavior.VISIBLE ||
                scrollParent.scrollOffsetY != 0 ||
                scrollParent.scrollOffsetX != 0
            ) {
                break
            }
            scrollParent = scrollParent.parent
        }
        val anchorBounds = scrollParent?.bounds ?: node.parent?.bounds ?: return assignedBounds
        val topThreshold = resolveSignedCssDimensionPx(style.topCss, anchorBounds.height, style.fontSizePx)
        val bottomThreshold = resolveSignedCssDimensionPx(style.bottomCss, anchorBounds.height, style.fontSizePx)
        val leftThreshold = resolveSignedCssDimensionPx(style.leftCss, anchorBounds.width, style.fontSizePx)
        val rightThreshold = resolveSignedCssDimensionPx(style.rightCss, anchorBounds.width, style.fontSizePx)

        val parentBounds = node.parent?.bounds ?: anchorBounds
        var stickyX = assignedBounds.x
        var stickyY = assignedBounds.y

        if (topThreshold != null) {
            val minY = anchorBounds.y + topThreshold
            val maxY = (parentBounds.bottom - assignedBounds.height).coerceAtLeast(minY)
            stickyY = stickyY.coerceIn(minY, maxY)
        } else if (bottomThreshold != null) {
            val maxY = anchorBounds.bottom - bottomThreshold - assignedBounds.height
            val minY = parentBounds.y.coerceAtMost(maxY)
            stickyY = stickyY.coerceIn(minY, maxY)
        }

        if (leftThreshold != null) {
            val minX = anchorBounds.x + leftThreshold
            val maxX = (parentBounds.right - assignedBounds.width).coerceAtLeast(minX)
            stickyX = stickyX.coerceIn(minX, maxX)
        } else if (rightThreshold != null) {
            val maxX = anchorBounds.right - rightThreshold - assignedBounds.width
            val minX = parentBounds.x.coerceAtMost(maxX)
            stickyX = stickyX.coerceIn(minX, maxX)
        }

        return Rect(stickyX, stickyY, assignedBounds.width, assignedBounds.height)
    }

    private fun updateScrollExtents(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        effectiveBounds: Rect
    ) {
        var maxR = effectiveBounds.width
        var maxB = effectiveBounds.height
        val baseX = effectiveBounds.x - node.scrollOffsetX
        val baseY = effectiveBounds.y - node.scrollOffsetY
        for (child in node.children) {
            if (!isNodeRendered(child)) continue
            val cs = child.resolveVisualStyle()
            if (cs.position == "fixed") continue
            val r = (child.bounds.right - baseX) + cs.margin.right + style.padding.right
            val b = (child.bounds.bottom - baseY) + cs.margin.bottom + style.padding.bottom
            if (r > maxR) maxR = r
            if (b > maxB) maxB = b
        }
        node.scrollWidth = max(maxR, effectiveBounds.width)
        node.scrollHeight = max(maxB, effectiveBounds.height)
        val maxScrollX = (node.scrollWidth - effectiveBounds.width).coerceAtLeast(0)
        val maxScrollY = (node.scrollHeight - effectiveBounds.height).coerceAtLeast(0)
        if (node.scrollOffsetX > maxScrollX) node.scrollOffsetX = maxScrollX
        if (node.scrollOffsetY > maxScrollY) node.scrollOffsetY = maxScrollY
    }

    private fun isBootstrapGridRow(node: AlienWidgetNode): Boolean {
        if (node.session?.hasBootstrapStyleSheet == false) return false
        return node.componentClassPrefix == "row" || "row" in node.styleSnapshot.customClasses
    }

    private fun isW3cFlexContainer(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        flowChildren: List<AlienWidgetNode>
    ): Boolean {
        if (!AlienFlexLayoutSolver.isFlexContainer(style, node)) return false
        val prefix = node.componentClassPrefix.lowercase()
        if (isBootstrapGridRow(node) || prefix == "split-pane" ||
            prefix == "navbar" || prefix == "menubar" || prefix == "btn-group" || prefix == "input-group"
        ) {
            return false
        }
        if (flowChildren.any { isShrinkCrossAxisChild(it) }) return false
        return true
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
            val isHr = node.tagName.equals("hr", ignoreCase = true) || node.componentClassPrefix == "hr"
            val intrinsicH = max(node.intrinsicHeight.roundToInt(), if (isHr) 2 else 0)
            val padBorderH = style.padding.left + style.padding.right +
                style.borderLeftWidthPx.roundToInt() + style.borderRightWidthPx.roundToInt()
            val padBorderV = style.padding.top + style.padding.bottom +
                style.borderTopWidthPx.roundToInt() + style.borderBottomWidthPx.roundToInt()
            val defaultW = padBorderH +
                if (node.componentClassPrefix == "alien-root" || node.componentClassPrefix == "page") innerAvailW else intrinsicW
            val defaultH = padBorderV +
                if (node.componentClassPrefix == "alien-root" || node.componentClassPrefix == "page") innerAvailH else intrinsicH
            return Size(defaultW, defaultH)
        }

        val isHorizontal = isHorizontalContainer(node, style)
        val gapPx = resolveGapPx(node, isHorizontal, style)
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

        val rowGap = if (style.rowGapPx > 0f) style.rowGapPx.roundToInt() else resolveGapPx(node, false, style)
        val colGap = if (style.columnGapPx > 0f) style.columnGapPx.roundToInt() else resolveGapPx(node, true, style)
        if (AlienGridLayoutSolver.isGridContainer(style, node)) {
            return AlienGridLayoutSolver.measureGridContainer(this, node, style, flowChildren, innerAvailW, innerAvailH, rowGap, colGap)
        }
        if (AlienTableLayoutSolver.isFullHtmlTable(style, node)) {
            return AlienTableLayoutSolver.measureTable(this, node, style, innerAvailW)
        }
        if (isW3cFlexContainer(node, style, flowChildren)) {
            return AlienFlexLayoutSolver.measureFlexContainer(this, node, style, flowChildren, innerAvailW, innerAvailH, rowGap, colGap)
        }

        val hasNonShrinkingFlowChild = flowChildren.any { !isShrinkCrossAxisChild(it) }

        if (isHorizontal) {
            val isRow = isBootstrapGridRow(node)
            if (isRow) {
                val viewportW = resolveViewportWidth(node)
                val rowGap = if (style.rowGapPx > 0f) style.rowGapPx.roundToInt() else gapPx
                var currentLineSpan = 0
                var currentLineW = 0
                var currentLineH = 0
                var lineCount = 0

                for (child in flowChildren) {
                    val childStyle = child.resolveVisualStyle()
                    val colSpan = resolveBootstrapColSpan(child, viewportW) ?: 12
                    val cw = child.measuredSize.width + childStyle.margin.left + childStyle.margin.right
                    val ch = child.measuredSize.height + childStyle.margin.top + childStyle.margin.bottom
                    if (currentLineSpan > 0 && currentLineSpan + colSpan > 12) {
                        accumW = max(accumW, currentLineW)
                        accumH += currentLineH + (if (lineCount > 0) rowGap else 0)
                        lineCount++
                        currentLineSpan = 0
                        currentLineW = 0
                        currentLineH = 0
                    }
                    currentLineW += cw + (if (currentLineSpan > 0) gapPx else 0)
                    currentLineH = max(currentLineH, ch)
                    currentLineSpan += colSpan
                }
                if (currentLineSpan > 0 || flowChildren.isNotEmpty()) {
                    accumW = max(accumW, currentLineW)
                    accumH += currentLineH + (if (lineCount > 0) rowGap else 0)
                }
            } else {
                for (child in flowChildren) {
                    if (hasNonShrinkingFlowChild && isShrinkCrossAxisChild(child)) continue
                    val childStyle = child.resolveVisualStyle()
                    val ch = child.measuredSize.height + childStyle.margin.top + childStyle.margin.bottom
                    accumH = max(accumH, ch)
                }
                if (hasNonShrinkingFlowChild && accumH > 0) {
                    for (child in flowChildren) {
                        if (!isShrinkCrossAxisChild(child)) continue
                        val cs = child.resolveVisualStyle()
                        val targetCrossH = (accumH - cs.margin.top - cs.margin.bottom).coerceAtLeast(1)
                        measureNode(child, innerAvailW, targetCrossH)
                    }
                }
                val isTable = style.display == "table" || "d-table" in node.styleSnapshot.customClasses
                val effectiveGap = if (isTable) style.columnGapPx.roundToInt().coerceAtLeast(0) else gapPx
                val isFlexWrap = !isTable && (
                    style.flexWrap == FlexWrap.WRAP ||
                        style.flexWrap == FlexWrap.WRAP_REVERSE ||
                        node.styleSnapshot.flexWrap == FlexWrap.WRAP ||
                        node.styleSnapshot.flexWrap == FlexWrap.WRAP_REVERSE
                    )
                if (isFlexWrap && innerAvailW > 0) {
                    val rowGap = if (style.rowGapPx > 0f) style.rowGapPx.roundToInt() else effectiveGap
                    var currentLineW = 0
                    var currentLineH = 0
                    var lineCount = 0
                    var itemsInLine = 0
                    accumH = 0
                    for (child in flowChildren) {
                        val childStyle = child.resolveVisualStyle()
                        val cw = child.measuredSize.width + childStyle.margin.left + childStyle.margin.right
                        val ch = child.measuredSize.height + childStyle.margin.top + childStyle.margin.bottom
                        val nextW = currentLineW + (if (itemsInLine > 0) effectiveGap else 0) + cw
                        if (itemsInLine > 0 && nextW > innerAvailW) {
                            accumW = max(accumW, currentLineW)
                            accumH += currentLineH + (if (lineCount > 0) rowGap else 0)
                            lineCount++
                            currentLineW = cw
                            currentLineH = ch
                            itemsInLine = 1
                        } else {
                            currentLineW = nextW
                            currentLineH = max(currentLineH, ch)
                            itemsInLine++
                        }
                    }
                    if (itemsInLine > 0) {
                        accumW = max(accumW, currentLineW)
                        accumH += currentLineH + (if (lineCount > 0) rowGap else 0)
                    }
                } else {
                    for ((idx, child) in flowChildren.withIndex()) {
                        val childStyle = child.resolveVisualStyle()
                        val cw = child.measuredSize.width + childStyle.margin.left + childStyle.margin.right
                        val ch = child.measuredSize.height + childStyle.margin.top + childStyle.margin.bottom
                        accumW += cw + (if (idx > 0) effectiveGap else 0)
                        if (!hasNonShrinkingFlowChild || !isShrinkCrossAxisChild(child)) {
                            accumH = max(accumH, ch)
                        }
                    }
                }
            }
        } else {
            var nonShrinkW = 0
            if (hasNonShrinkingFlowChild) {
                var scanIdx = 0
                while (scanIdx < flowChildren.size) {
                    val child = flowChildren[scanIdx]
                    val childStyle = child.resolveVisualStyle()
                    if (isInlineFlowChild(child, childStyle)) {
                        var rowW = 0
                        while (scanIdx < flowChildren.size) {
                            val c = flowChildren[scanIdx]
                            val cs = c.resolveVisualStyle()
                            if (!isInlineFlowChild(c, cs)) break
                            if (!isShrinkCrossAxisChild(c)) {
                                val cw = c.measuredSize.width + cs.margin.left + cs.margin.right
                                if (innerAvailW > 0 && rowW > 0 && rowW + cw > innerAvailW) {
                                    nonShrinkW = max(nonShrinkW, rowW)
                                    rowW = 0
                                }
                                rowW += cw + (if (rowW > 0) gapPx else 0)
                            }
                            scanIdx++
                        }
                        nonShrinkW = max(nonShrinkW, rowW)
                    } else {
                        if (!isShrinkCrossAxisChild(child)) {
                            val isHr = child.tagName == "hr" || child.componentClassPrefix == "hr"
                            val cw = (if (isHr && style.widthCss == null && !shouldStretchHorizontally(node, style)) 0 else child.measuredSize.width) +
                                childStyle.margin.left + childStyle.margin.right
                            nonShrinkW = max(nonShrinkW, cw)
                        }
                        scanIdx++
                    }
                }
                if (nonShrinkW > 0) {
                    for (child in flowChildren) {
                        if (!isShrinkCrossAxisChild(child)) continue
                        val cs = child.resolveVisualStyle()
                        val targetCrossW = (nonShrinkW - cs.margin.left - cs.margin.right).coerceAtLeast(1)
                        measureNode(child, targetCrossW, innerAvailH)
                    }
                }
            }

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
                            if (!hasNonShrinkingFlowChild) accumW = max(accumW, rowW)
                            accumH += rowH + gapPx
                            rowW = 0
                            rowH = 0
                        }
                        if (!hasNonShrinkingFlowChild || !isShrinkCrossAxisChild(c)) {
                            rowW += cw + (if (rowW > 0) gapPx else 0)
                        }
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
                    if (!hasNonShrinkingFlowChild || !isShrinkCrossAxisChild(child)) {
                        accumW = max(accumW, cw)
                    }
                    accumH += ch + (if (idx > 0) gapPx else 0)
                    idx++
                }
            }
            if (hasNonShrinkingFlowChild && nonShrinkW > 0) {
                accumW = nonShrinkW
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
                    contentH = contentH,
                    gapPx = gapPx,
                    updateTracker = updateTracker
                )
                continue
            }

            val m = childStyle.margin
            val isShrinkCross = isShrinkCrossAxisChild(child)
            val isChildTable = childStyle.display == "table" || "d-table" in child.styleSnapshot.customClasses
            val stretchWidth = shouldStretchHorizontally(child, childStyle)
            val effectiveWidthCss = childStyle.widthCss?.takeUnless {
                isShrinkCross && (it.trim() == "0" || it.trim() == "0px")
            }
            val cPadBorderH = childStyle.padding.left + childStyle.padding.right +
                childStyle.borderLeftWidthPx.roundToInt() + childStyle.borderRightWidthPx.roundToInt()
            val cPadBorderV = childStyle.padding.top + childStyle.padding.bottom +
                childStyle.borderTopWidthPx.roundToInt() + childStyle.borderBottomWidthPx.roundToInt()
            val cIsContentBox = childStyle.boxSizing == CssBoxSizing.CONTENT_BOX
            var childW = when {
                isShrinkCross ->
                    (contentW - m.left - m.right).coerceAtLeast(1)
                effectiveWidthCss != null -> {
                    val raw = resolveCssDimensionPx(effectiveWidthCss, contentW, childStyle.fontSizePx)
                    if (raw != null) (if (cIsContentBox) raw + cPadBorderH else raw) else child.measuredSize.width
                }
                isChildTable ->
                    child.measuredSize.width.coerceAtLeast(1)
                stretchWidth ->
                    (contentW - m.left - m.right).coerceAtLeast(child.measuredSize.width.coerceAtMost(contentW))
                else ->
                    child.measuredSize.width.coerceAtLeast(1)
            }
            val effectiveMaxWidthCss = childStyle.maxWidthCss?.takeUnless {
                isShrinkCross && it.trim() == "100%"
            }
            if (!isChildTable) {
                resolveCssDimensionPx(effectiveMaxWidthCss, contentW, childStyle.fontSizePx)?.let { maxW ->
                    val outerMaxW = if (cIsContentBox) maxW + cPadBorderH else maxW
                    childW = childW.coerceAtMost(outerMaxW)
                }
            }
            resolveCssDimensionPx(childStyle.minWidthCss, contentW, childStyle.fontSizePx)?.let { minW ->
                val outerMinW = if (cIsContentBox) minW + cPadBorderH else minW
                childW = childW.coerceAtLeast(outerMinW)
            }

            if (childW != child.measuredSize.width && child.customMeasure != null &&
                (childStyle.heightCss == null || childStyle.heightCss.trim().equals("auto", ignoreCase = true))
            ) {
                child.measuredSize = child.customMeasure!!.invoke(childW, contentH, childStyle)
            }

            var childH = if (totalFlexGrow > 0f && childStyle.flexGrow > 0f) {
                ((remainingFlexH * (childStyle.flexGrow / totalFlexGrow)).roundToInt()).coerceAtLeast(child.measuredSize.height)
            } else if (childStyle.aspectRatio != null && childStyle.aspectRatio > 0f && childStyle.heightCss == null) {
                (childW / childStyle.aspectRatio).roundToInt().coerceAtLeast(1)
            } else {
                child.measuredSize.height.coerceAtLeast(1)
            }
            resolveCssDimensionPx(childStyle.maxHeightCss, contentH, childStyle.fontSizePx)?.let { maxH ->
                val outerMaxH = if (cIsContentBox) maxH + cPadBorderV else maxH
                childH = childH.coerceAtMost(outerMaxH)
            }
            resolveCssDimensionPx(childStyle.minHeightCss, contentH, childStyle.fontSizePx)?.let { minH ->
                val outerMinH = if (cIsContentBox) minH + cPadBorderV else minH
                childH = childH.coerceAtLeast(outerMinH)
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
        contentH: Int = 0,
        gapPx: Int,
        updateTracker: AlienUpdateRegionTracker?
    ): Int {
        if (group.isEmpty()) return startY

        // Split into wrapped lines based on contentW
        val lines = mutableListOf<MutableList<Triple<AlienWidgetNode, AlienResolvedVisualStyle, Size>>>()
        var currentLine = mutableListOf<Triple<AlienWidgetNode, AlienResolvedVisualStyle, Size>>()
        var currentLineW = 0

        for ((c, cs) in group) {
            if (c.tagName.equals("br", ignoreCase = true)) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = mutableListOf()
                    currentLineW = 0
                }
                arrangeNode(c, Rect(contentX, startY, 0, 0), updateTracker)
                continue
            }
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
            val effectiveLineH = if (lines.size == 1 &&
                (parentStyle.verticalAlign == CssVerticalAlign.BOTTOM || parentStyle.verticalAlign == CssVerticalAlign.MIDDLE) &&
                contentH > lineH
            ) {
                contentH
            } else {
                lineH
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
                val alignMode = if (cs.verticalAlign != CssVerticalAlign.BASELINE) cs.verticalAlign else parentStyle.verticalAlign
                val cy = when (alignMode) {
                    CssVerticalAlign.BOTTOM -> cursorY + (effectiveLineH - sz.height - m.bottom).coerceAtLeast(m.top)
                    CssVerticalAlign.MIDDLE -> cursorY + ((effectiveLineH - sz.height - m.top - m.bottom) / 2).coerceAtLeast(0) + m.top
                    else -> cursorY + m.top
                }
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
            cursorY += effectiveLineH + gapPx
        }
        return cursorY
    }

    private fun resolvePositionedContainingBlockBounds(
        child: AlienWidgetNode,
        childStyle: AlienResolvedVisualStyle,
        fallbackBounds: Rect
    ): Rect {
        var root: AlienWidgetNode? = child.parent
        var firstPositioned: AlienWidgetNode? = null
        while (root != null) {
            if (childStyle.position != "fixed" && firstPositioned == null) {
                val ancPos = root.resolveVisualStyle().position
                if (ancPos != "static") {
                    firstPositioned = root
                }
            }
            val nextParent = root.parent
            if (nextParent == null) {
                return firstPositioned?.let(::paddingBoxOf) ?: root.bounds
            }
            root = nextParent
        }
        return firstPositioned?.let(::paddingBoxOf) ?: fallbackBounds
    }

    private fun paddingBoxOf(node: AlienWidgetNode): Rect {
        val st = node.resolveVisualStyle()
        val bl = st.borderLeftWidthPx.roundToInt()
        val bt = st.borderTopWidthPx.roundToInt()
        val br = st.borderRightWidthPx.roundToInt()
        val bb = st.borderBottomWidthPx.roundToInt()
        return Rect(
            x = node.bounds.x + bl,
            y = node.bounds.y + bt,
            width = (node.bounds.width - bl - br).coerceAtLeast(0),
            height = (node.bounds.height - bt - bb).coerceAtLeast(0)
        )
    }

    private fun arrangePositionedChild(
        child: AlienWidgetNode,
        childStyle: AlienResolvedVisualStyle,
        parentBounds: Rect,
        contentX: Int,
        contentY: Int,
        updateTracker: AlienUpdateRegionTracker?
    ) {
        val cbBounds = resolvePositionedContainingBlockBounds(child, childStyle, parentBounds)
        val availW = cbBounds.width.coerceAtLeast(1)
        val availH = cbBounds.height.coerceAtLeast(1)

        val leftPx = resolveSignedCssDimensionPx(childStyle.leftCss, availW, childStyle.fontSizePx)
        val rightPx = resolveSignedCssDimensionPx(childStyle.rightCss, availW, childStyle.fontSizePx)
        val topPx = resolveSignedCssDimensionPx(childStyle.topCss, availH, childStyle.fontSizePx)
        val bottomPx = resolveSignedCssDimensionPx(childStyle.bottomCss, availH, childStyle.fontSizePx)

        var absW = resolveCssDimensionPx(childStyle.widthCss, availW, childStyle.fontSizePx)
            ?: if (leftPx != null && rightPx != null) (availW - leftPx - rightPx).coerceAtLeast(1) else null
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
            ?: if (topPx != null && bottomPx != null) (availH - topPx - bottomPx).coerceAtLeast(1) else null
            ?: child.preferredSize?.height?.takeIf { it > 0 }
            ?: child.measuredSize.height.coerceAtLeast(1)
        resolveCssDimensionPx(childStyle.maxHeightCss, availH, childStyle.fontSizePx)?.let { maxH ->
            absH = absH.coerceAtMost(maxH)
        }
        resolveCssDimensionPx(childStyle.minHeightCss, availH, childStyle.fontSizePx)?.let { minH ->
            absH = absH.coerceAtLeast(minH)
        }

        var absX = when {
            leftPx != null -> cbBounds.x + leftPx
            rightPx != null -> cbBounds.right - rightPx - absW
            else -> contentX + childStyle.margin.left
        }
        var absY = when {
            topPx != null -> cbBounds.y + topPx
            bottomPx != null -> cbBounds.bottom - bottomPx - absH
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

        // Handle CSS `display: table` (e.g. `.a4 .steps`) with `display: table-cell` children
        val isTable = style.display == "table" || "d-table" in node.styleSnapshot.customClasses
        if (isTable) {
            var cursorX = contentX
            val effectiveGap = style.columnGapPx.roundToInt().coerceAtLeast(0)
            for (child in flowChildren) {
                val cs = child.resolveVisualStyle()
                val m = cs.margin
                val childW = (resolveCssDimensionPx(cs.widthCss, contentW, cs.fontSizePx) ?: child.measuredSize.width).coerceAtLeast(1)
                val childH = max(child.measuredSize.height, (contentH - m.top - m.bottom).coerceAtLeast(1))
                val childX = cursorX + m.left
                val childY = contentY + m.top
                arrangeNode(child, Rect(childX, childY, childW, childH), updateTracker)
                cursorX = childX + childW + m.right + effectiveGap
            }
            return
        }

        // Handle Bootstrap 12-column `.row` with responsive `col-*`..`col-xxl-*` wrapping
        val isRow = isBootstrapGridRow(node)
        if (isRow) {
            val viewportW = resolveViewportWidth(node)
            val lines = mutableListOf<MutableList<Pair<AlienWidgetNode, Int?>>>()
            var currentLine = mutableListOf<Pair<AlienWidgetNode, Int?>>()
            var currentSpan = 0

            for (child in flowChildren) {
                val span = resolveBootstrapColSpan(child, viewportW)
                val effectiveSpan = span ?: 12
                if (currentLine.isNotEmpty() && currentSpan + effectiveSpan > 12) {
                    lines.add(currentLine)
                    currentLine = mutableListOf()
                    currentSpan = 0
                }
                currentLine.add(child to span)
                currentSpan += effectiveSpan
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }

            val rowGap = if (style.rowGapPx > 0f) style.rowGapPx.roundToInt() else gapPx
            var cursorY = contentY
            for (line in lines) {
                val count = line.size.coerceAtLeast(1)
                val totalGap = gapPx * (count - 1).coerceAtLeast(0)
                val availColsW = (contentW - totalGap).coerceAtLeast(0)

                val itemWidths = IntArray(line.size)
                var maxMeasuredH = 0
                for ((idx, pair) in line.withIndex()) {
                    val (child, colSpan) = pair
                    val cs = child.resolveVisualStyle()
                    val m = cs.margin
                    val cellW = if (colSpan != null) {
                        ((availColsW * colSpan) / 12f).toInt().coerceAtLeast(1)
                    } else {
                        (availColsW / count).coerceAtLeast(1)
                    }
                    val childW = (cellW - m.left - m.right).coerceAtLeast(1)
                    itemWidths[idx] = childW
                    if (childW != child.measuredSize.width) {
                        measureNode(child, childW, contentH)
                    }
                    maxMeasuredH = max(maxMeasuredH, child.measuredSize.height + m.top + m.bottom)
                }

                val lineH = if (lines.size == 1) {
                    max(maxMeasuredH, contentH.coerceAtMost(max(maxMeasuredH, 32)))
                } else {
                    maxMeasuredH.coerceAtLeast(1)
                }

                var cursorX = contentX
                for ((idx, pair) in line.withIndex()) {
                    val (child, _) = pair
                    val cs = child.resolveVisualStyle()
                    val m = cs.margin
                    val childW = itemWidths[idx]
                    val childH = max(child.measuredSize.height, (lineH - m.top - m.bottom).coerceAtLeast(1))
                    val childX = cursorX + m.left
                    val childY = cursorY + m.top
                    arrangeNode(child, Rect(childX, childY, childW, childH), updateTracker)
                    cursorX = childX + childW + m.right + gapPx
                }
                cursorY += lineH + rowGap
            }
            return
        }

        // Standard HStack / Flex-Row (with FlexWrap.WRAP / FlexWrap.WRAP_REVERSE multi-line support)
        val isFlexWrap = style.flexWrap == FlexWrap.WRAP ||
            style.flexWrap == FlexWrap.WRAP_REVERSE ||
            node.styleSnapshot.flexWrap == FlexWrap.WRAP ||
            node.styleSnapshot.flexWrap == FlexWrap.WRAP_REVERSE

        if (isFlexWrap && contentW > 0) {
            val lines = mutableListOf<MutableList<AlienWidgetNode>>()
            var currentLine = mutableListOf<AlienWidgetNode>()
            var currentLineW = 0
            for (child in flowChildren) {
                val cs = child.resolveVisualStyle()
                val cw = child.measuredSize.width.coerceAtLeast(1) + cs.margin.left + cs.margin.right
                val nextW = currentLineW + (if (currentLine.isNotEmpty()) gapPx else 0) + cw
                if (currentLine.isNotEmpty() && nextW > contentW) {
                    lines.add(currentLine)
                    currentLine = mutableListOf(child)
                    currentLineW = cw
                } else {
                    currentLine.add(child)
                    currentLineW = nextW
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
            if (lines.size > 1) {
                val rowGap = if (style.rowGapPx > 0f) style.rowGapPx.roundToInt() else gapPx
                var cursorY = contentY
                for (line in lines) {
                    val lineFlexGrow = line.sumOf { it.resolveVisualStyle().flexGrow.toDouble() }.toFloat()
                    val lineFixedW = line.sumOf { c ->
                        val cs = c.resolveVisualStyle()
                        val w = if (lineFlexGrow > 0f && cs.flexGrow > 0f) 0 else c.measuredSize.width.coerceAtLeast(1)
                        w + cs.margin.left + cs.margin.right
                    } + gapPx * (line.size - 1).coerceAtLeast(0)
                    val lineRemW = (contentW - lineFixedW).coerceAtLeast(0)
                    val lineTotalW = if (lineFlexGrow > 0f) contentW else lineFixedW
                    val lineH = line.maxOf { c ->
                        val cs = c.resolveVisualStyle()
                        max(c.measuredSize.height, 1) + cs.margin.top + cs.margin.bottom
                    }

                    var lineCursorX = when (style.justifyContent) {
                        JustifyContent.CENTER -> contentX + ((contentW - lineTotalW) / 2).coerceAtLeast(0)
                        JustifyContent.END -> contentX + (contentW - lineTotalW).coerceAtLeast(0)
                        else -> contentX
                    }

                    for (child in line) {
                        val cs = child.resolveVisualStyle()
                        val m = cs.margin
                        val childX = lineCursorX + m.left
                        val childH = max(child.measuredSize.height, 1)
                        val childY = when {
                            cs.alignSelf == AlignSelf.CENTER -> cursorY + ((lineH - childH - m.top - m.bottom) / 2).coerceAtLeast(0) + m.top
                            cs.alignSelf == AlignSelf.END -> cursorY + (lineH - childH - m.bottom).coerceAtLeast(m.top)
                            style.alignItems == AlignItems.CENTER && cs.alignSelf == AlignSelf.AUTO ->
                                cursorY + ((lineH - childH - m.top - m.bottom) / 2).coerceAtLeast(0) + m.top
                            style.alignItems == AlignItems.END && cs.alignSelf == AlignSelf.AUTO ->
                                cursorY + (lineH - childH - m.bottom).coerceAtLeast(m.top)
                            else -> cursorY + m.top
                        }
                        val childW = if (lineFlexGrow > 0f && cs.flexGrow > 0f) {
                            ((lineRemW * (cs.flexGrow / lineFlexGrow)).roundToInt()).coerceAtLeast(child.measuredSize.width.coerceAtLeast(1))
                        } else {
                            child.measuredSize.width.coerceAtLeast(1)
                        }
                        arrangeNode(child, Rect(childX, childY, childW, childH), updateTracker)
                        lineCursorX = childX + childW + m.right + gapPx
                    }
                    cursorY += lineH + rowGap
                }
                return
            }
        }

        for (child in flowChildren) {
            if (!isShrinkCrossAxisChild(child)) continue
            val cs = child.resolveVisualStyle()
            val m = cs.margin
            val targetH = (contentH - m.top - m.bottom).coerceAtLeast(1)
            if (targetH != child.measuredSize.height && child.customMeasure != null &&
                (cs.widthCss == null || cs.widthCss.trim().equals("auto", ignoreCase = true))
            ) {
                child.measuredSize = child.customMeasure!!.invoke(contentW, targetH, cs)
            }
        }

        val totalFlexGrow = flowChildren.sumOf { it.resolveVisualStyle().flexGrow.toDouble() }.toFloat()
        val fixedChildrenW = flowChildren.sumOf { c ->
            val cs = c.resolveVisualStyle()
            val w = if (totalFlexGrow > 0f && cs.flexGrow > 0f) 0 else c.measuredSize.width
            w + cs.margin.left + cs.margin.right
        } + gapPx * (flowChildren.size - 1).coerceAtLeast(0)
        val remainingFlexW = (contentW - fixedChildrenW).coerceAtLeast(0)

        val totalChildrenW = if (totalFlexGrow > 0f) contentW else fixedChildrenW

        var cursorX = when (style.justifyContent) {
            JustifyContent.CENTER -> contentX + ((contentW - totalChildrenW) / 2).coerceAtLeast(0)
            JustifyContent.END -> contentX + (contentW - totalChildrenW).coerceAtLeast(0)
            else -> contentX
        }

        for (child in flowChildren) {
            val cs = child.resolveVisualStyle()
            val m = cs.margin
            val isShrinkCross = isShrinkCrossAxisChild(child)
            val childX = cursorX + m.left
            val childY = contentY + m.top
            var childH = if (isShrinkCross) {
                (contentH - m.top - m.bottom).coerceAtLeast(1)
            } else {
                max(child.measuredSize.height, 1)
            }
            val effectiveMaxHeightCss = cs.maxHeightCss?.takeUnless { isShrinkCross && it.trim() == "100%" }
            resolveCssDimensionPx(effectiveMaxHeightCss, contentH, cs.fontSizePx)?.let { maxH ->
                childH = childH.coerceAtMost(maxH)
            }
            if (childH != child.measuredSize.height && child.customMeasure != null &&
                (cs.widthCss == null || cs.widthCss.trim().equals("auto", ignoreCase = true))
            ) {
                child.measuredSize = child.customMeasure!!.invoke(contentW, childH, cs)
            }
            val childW = if (totalFlexGrow > 0f && cs.flexGrow > 0f) {
                ((remainingFlexW * (cs.flexGrow / totalFlexGrow)).roundToInt()).coerceAtLeast(child.measuredSize.width.coerceAtLeast(1))
            } else {
                child.measuredSize.width.coerceAtLeast(1)
            }
            arrangeNode(child, Rect(childX, childY, childW, childH), updateTracker)
            cursorX = childX + childW + m.right + gapPx
        }
    }

    private fun isNodeRendered(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle = node.resolveVisualStyle()
    ): Boolean = node.visible && style.display != "none"

    private fun clearUnrenderedSubtreeBounds(
        node: AlienWidgetNode,
        updateTracker: AlienUpdateRegionTracker?
    ) {
        val oldBounds = node.bounds
        if (oldBounds.width > 0 || oldBounds.height > 0) {
            node.bounds = Rect(0, 0, 0, 0)
            updateTracker?.markBoundsTransitionDirty(oldBounds, node.bounds)
        }
        node.layoutDirty = false
        for (child in node.children) {
            clearUnrenderedSubtreeBounds(child, updateTracker)
        }
    }

    private fun isShrinkCrossAxisChild(node: AlienWidgetNode): Boolean =
        node.attributes["data-stack-shrink-cross"] == "true"

    private fun isInlineFlowChild(child: AlienWidgetNode, style: AlienResolvedVisualStyle): Boolean {
        if (style.floatMode == "left" || style.floatMode == "right") return true
        return style.display == "inline-block" || style.display == "inline"
    }

    private fun isHorizontalContainer(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle = node.resolveVisualStyle()
    ): Boolean {
        if (style.display == "table" || "d-table" in node.styleSnapshot.customClasses) return true
        val prefix = node.componentClassPrefix.lowercase()
        val classes = node.styleSnapshot.customClasses
        val computedFlexDir = node.computedStyle?.flexDirection
        val hasBootstrap = node.session?.hasBootstrapStyleSheet != false
        if (computedFlexDir == FlexDirection.COLUMN || computedFlexDir == FlexDirection.COLUMN_REVERSE ||
            (hasBootstrap && "flex-column" in classes) ||
            node.styleSnapshot.flexDirection == FlexDirection.COLUMN ||
            node.styleSnapshot.flexDirection == FlexDirection.COLUMN_REVERSE ||
            node.inlineComponentStyle?.getFlexDirection() == FlexDirection.COLUMN ||
            node.inlineComponentStyle?.getFlexDirection() == FlexDirection.COLUMN_REVERSE
        ) {
            return false
        }
        if (prefix == "split-pane") {
            return node.attributes["data-orientation"] != "VERTICAL"
        }
        if (hasBootstrap && (prefix == "hstack" || prefix == "row" || prefix == "navbar" || prefix == "menubar" || prefix == "btn-group" || prefix == "input-group")) {
            return true
        }
        if ((hasBootstrap && ("hstack" in classes || "row" in classes || "flex-row" in classes || "btn-group" in classes)) ||
            computedFlexDir == FlexDirection.ROW || computedFlexDir == FlexDirection.ROW_REVERSE
        ) {
            return true
        }
        if (node.styleSnapshot.flexDirection == FlexDirection.ROW ||
            node.styleSnapshot.flexDirection == FlexDirection.ROW_REVERSE ||
            node.inlineComponentStyle?.getFlexDirection() == FlexDirection.ROW ||
            node.inlineComponentStyle?.getFlexDirection() == FlexDirection.ROW_REVERSE
        ) {
            return true
        }
        if ((node.styleSnapshot.display == DisplayMode.FLEX || style.display == "flex" || style.display == "inline-flex") &&
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
        if (isShrinkCrossAxisChild(child)) return true
        if (style.display == "table" || "d-table" in child.styleSnapshot.customClasses) return false
        if (style.position == "absolute" || style.position == "fixed") {
            return !style.leftCss.isNullOrBlank() && !style.rightCss.isNullOrBlank()
        }
        if (child.preferredSize != null && child.preferredSize!!.width > 0) return false
        if (style.widthCss != null) return style.widthCss.trim() == "100%"
        if ("w-100" in child.styleSnapshot.customClasses) return true
        if (style.marginLeftAuto || style.marginRightAuto) return false
        if (style.display == "inline-block" || style.display == "inline" ||
            style.display == "inline-flex" || style.display == "inline-grid" ||
            style.floatMode != "none"
        ) {
            return false
        }
        if (child.children.any { isShrinkCrossAxisChild(it) } && child.styleSnapshot.width == null) {
            return false
        }
        val prefix = child.componentClassPrefix.lowercase()
        if (child.tagName.equals("img", ignoreCase = true) || prefix == "img-fluid") {
            return "w-100" in child.styleSnapshot.customClasses || child.styleSnapshot.width != null
        }
        if (prefix == "btn" || prefix == "badge" || prefix == "spinner-border" || prefix == "form-check") {
            return style.display == "block" || "w-100" in child.styleSnapshot.customClasses || child.styleSnapshot.width != null
        }
        return true
    }

    private fun resolveGapPx(
        node: AlienWidgetNode,
        isHorizontal: Boolean = false,
        style: AlienResolvedVisualStyle = node.resolveVisualStyle()
    ): Int {
        val directionalGap = if (isHorizontal) style.columnGapPx else style.rowGapPx
        if (directionalGap > 0f) return directionalGap.roundToInt()
        if (style.gapPx > 0f) return style.gapPx.roundToInt()
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

    private fun resolveViewportWidth(node: AlienWidgetNode): Int =
        (node.session?.viewportWidth?.takeIf { it > 0 } ?: currentViewportWidthPx).coerceAtLeast(1)

    internal fun resolveBootstrapColSpan(child: AlienWidgetNode, viewportWidthPx: Int): Int? {
        val classes = child.styleSnapshot.customClasses
        if (classes.isEmpty()) return null
        var resolvedSpan: Int? = null
        var hasEqualWidthCol = false
        for ((bpPrefix, minWidthPx) in BOOTSTRAP_COL_BREAKPOINTS) {
            if (viewportWidthPx < minWidthPx) continue
            val bareClass = if (bpPrefix.isEmpty()) "col" else "col-${bpPrefix.removeSuffix("-")}"
            if (bareClass in classes) {
                resolvedSpan = null
                hasEqualWidthCol = true
            }
            val spanPrefix = "col-$bpPrefix"
            for (n in 1..12) {
                if ("$spanPrefix$n" in classes) {
                    resolvedSpan = n
                }
            }
            if ("${spanPrefix}auto" in classes) {
                resolvedSpan = null
                hasEqualWidthCol = true
            }
        }
        if (resolvedSpan != null) return resolvedSpan
        if (hasEqualWidthCol) return null
        return null
    }

    internal fun resolveCssDimensionPx(raw: String?, availablePx: Int, fontSizePx: Float): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("!important", "").trim().lowercase()
        if (cleaned == "auto" || cleaned == "none") return null
        return AlienCssStyleResolver.parseLengthPx(
            raw = cleaned,
            fontSizePx = fontSizePx,
            percentBasePx = availablePx.toFloat(),
            viewportWidthPx = currentViewportWidthPx.toFloat(),
            viewportHeightPx = currentViewportHeightPx.toFloat()
        )?.roundToInt()?.coerceAtLeast(0)
    }

    internal fun resolveSignedCssDimensionPx(raw: String?, availablePx: Int, fontSizePx: Float): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("!important", "").trim().lowercase()
        if (cleaned == "auto" || cleaned == "none") return null
        return AlienCssStyleResolver.parseLengthPx(
            raw = cleaned,
            fontSizePx = fontSizePx,
            percentBasePx = availablePx.toFloat(),
            viewportWidthPx = currentViewportWidthPx.toFloat(),
            viewportHeightPx = currentViewportHeightPx.toFloat()
        )?.roundToInt()
    }
}
