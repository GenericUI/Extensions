package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.transition.CssTransform2D
import kotlin.math.max
import kotlin.math.min

/**
 * Coordinates dirty-region tracking ([AlienUpdateRegionTracker]), CSS transition/animation stepping
 * ([AlienTransitionAnimator]), layout passes ([AlienLayoutEngine]), transparent hole cutting for
 * underlay native/extension widgets, and hierarchical dirty-region repaints onto [AlienRenderSession.canvas2D].
 */
class AlienRepaintManager(
    val updateTracker: AlienUpdateRegionTracker = AlienUpdateRegionTracker()
) {
    var repaintFrameCount: Long = 0L
        private set

    var lastRepaintedRegions: List<Rect> = emptyList()
        private set

    fun markDirty(rect: Rect, padPx: Int = 4) {
        updateTracker.markDirty(rect, padPx)
    }

    fun markFullViewportDirty() {
        updateTracker.markFullViewportDirty()
    }

    /**
     * Steps active CSS transitions/animations by [deltaMs], performs layout if needed, and repaints
     * only the coalesced dirty regions on [session]'s `Canvas2D`.
     */
    fun stepAnimationsAndRepaint(session: AlienRenderSession, deltaMs: Float = 16.67f): List<Rect> {
        val ctx = session.canvas2D.getContext()
        if (ctx.width > 0 && ctx.height > 0 &&
            (ctx.width != session.viewportWidth || ctx.height != session.viewportHeight)
        ) {
            session.resizeViewport(ctx.width, ctx.height)
        }

        // 1. Step transitions and continuous animations across the AlienWidgetNode tree
        stepNodeAnimationsRecursive(session.rootNode, deltaMs.coerceAtLeast(0f))

        // 2. Run layout pass if tree structure or preferred sizes changed
        if (session.isLayoutDirty) {
            session.layoutEngine.performLayout(
                root = session.rootNode,
                viewportWidth = session.viewportWidth,
                viewportHeight = session.viewportHeight,
                updateTracker = updateTracker
            )
            session.isLayoutDirty = false
        }

        // 3. Drain coalesced dirty rectangles
        val regions = updateTracker.drainDirtyRegions(session.viewportWidth, session.viewportHeight)
        if (regions.isEmpty()) {
            lastRepaintedRegions = emptyList()
            return emptyList()
        }

        val startedPass = session.activeStylePassId < 0L
        if (startedPass) session.beginStylePass()
        try {
            val rootStyle = session.rootNode.resolveVisualStyle()

            val transparentUnstyledRoot = session.offscreenOnly && !session.hasBootstrapStyleSheet

            for (region in regions) {
                paintDirtyRegion(ctx, region, rootStyle, session.rootNode, transparentUnstyledRoot)
            }
        } finally {
            if (startedPass) session.endStylePass()
        }

        session.canvas2D.swapBuffers()
        repaintFrameCount++
        lastRepaintedRegions = regions
        return regions
    }

    private fun stepNodeAnimationsRecursive(node: AlienWidgetNode, deltaMs: Float) {
        if (!node.visible) return
        node.resolveVisualStyle()
        val changed = node.transitionAnimator.step(
            deltaMs = deltaMs,
            hasContinuousAnimation = node.hasContinuousAnimation
        )
        if (changed) {
            updateTracker.markDirty(node.bounds, padPx = 6)
        }
        for (child in node.children) {
            stepNodeAnimationsRecursive(child, deltaMs)
        }
    }

    private fun paintDirtyRegion(
        ctx: ImageBuffer2DContext,
        region: Rect,
        rootStyle: AlienResolvedVisualStyle,
        rootNode: AlienWidgetNode,
        transparentUnstyledRoot: Boolean = false
    ) {
        ctx.save()
        ctx.beginPath()
        ctx.moveTo(region.x.toFloat(), region.y.toFloat())
        ctx.lineTo(region.right.toFloat(), region.y.toFloat())
        ctx.lineTo(region.right.toFloat(), region.bottom.toFloat())
        ctx.lineTo(region.x.toFloat(), region.bottom.toFloat())
        ctx.closePath()
        ctx.clip()

        // Clear region and paint default body background unless root specifies a custom background
        ctx.clearRect(region.x.toFloat(), region.y.toFloat(), region.width.toFloat(), region.height.toFloat())
        val skipUnstyledRootFill =
            transparentUnstyledRoot &&
                rootStyle.backgroundColor.a == 0 &&
                rootStyle.backgroundImageUrl.isNullOrBlank()
        if (!skipUnstyledRootFill) {
            val baseBg = when {
                rootStyle.backgroundColor.a > 0 -> rootStyle.backgroundColor
                rootNode.componentClassPrefix == "alien-root" ||
                    rootNode.tagName.equals("genericui:app", ignoreCase = true) ||
                    rootNode.tagName.equals("genericui:fragment", ignoreCase = true) ||
                    !rootStyle.backgroundImageUrl.isNullOrBlank() -> RgbaColor.BLACK
                rootStyle.darkTheme -> AlienBootstrapThemeTokens.BODY_BG_DARK
                else -> AlienBootstrapThemeTokens.BODY_BG_LIGHT
            }
            ctx.setFillColor(baseBg)
            ctx.fillRect(region.x.toFloat(), region.y.toFloat(), region.width.toFloat(), region.height.toFloat())
        }

        paintNodeHierarchy(ctx, rootNode, region, inheritedOpacity = 1f)

        ctx.restore()
    }

    private fun paintNodeHierarchy(
        ctx: ImageBuffer2DContext,
        node: AlienWidgetNode,
        clipRegion: Rect,
        inheritedOpacity: Float
    ) {
        if (!node.visible) return
        val style = node.resolveVisualStyle()
        if (style.display == "none") return

        val filterOpacity = style.filter?.opacity?.coerceIn(0f, 1f) ?: 1f
        val effectiveOpacity = (style.opacity * filterOpacity * inheritedOpacity).coerceIn(0f, 1f)
        if (effectiveOpacity <= 0.001f) return

        val transform = style.transform2D
        if (transform.scaleX == 0f || transform.scaleY == 0f) return

        // If this node is an underlay slot (`UnalienContainer` or extension widget below the Canvas2D):
        if (node.isUnderlayExtensionSlot) {
            if (node.underlayLayerMode == AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE) {
                val holeIntersection = intersectRect(node.bounds, clipRegion)
                if (holeIntersection != null) {
                    AlienBootstrapPainter.cutTransparentHole(ctx, holeIntersection, style.borderRadiusPx)
                }
            }
            if (style.borderWidthPx > 0f && style.borderColor.a > 0) {
                AlienBootstrapPainter.strokeRoundedRect(
                    ctx,
                    node.bounds.x.toFloat(),
                    node.bounds.y.toFloat(),
                    node.bounds.width.toFloat(),
                    node.bounds.height.toFloat(),
                    style.borderRadiusPx
                )
            }
            return
        }

        ctx.save()
        if (transform != CssTransform2D.IDENTITY) {
            val isPositioned = style.position == "absolute" || style.position == "fixed"
            val m = transform.resolveAffineMatrix(node.bounds.width, node.bounds.height)
            // Positioned nodes already have `translate(dx, dy)` applied to `node.bounds` in AlienLayoutEngine
            val tx = if (isPositioned && !transform.hasMatrix) 0f else m[4]
            val ty = if (isPositioned && !transform.hasMatrix) 0f else m[5]
            val ox = node.bounds.x + (style.transformOriginXPxOffset
                ?: ((style.transformOriginXPercent / 100f) * node.bounds.width))
            val oy = node.bounds.y + (style.transformOriginYPxOffset
                ?: ((style.transformOriginYPercent / 100f) * node.bounds.height))
            val hasLinearTransform = m[0] != 1f || m[1] != 0f || m[2] != 0f || m[3] != 1f
            if (tx != 0f || ty != 0f || hasLinearTransform) {
                ctx.translate(ox + tx, oy + ty)
                if (hasLinearTransform) {
                    ctx.transform(m[0], m[1], m[2], m[3], 0f, 0f)
                }
                ctx.translate(-ox, -oy)
            }
        }

        val effectiveStyle = if (effectiveOpacity != style.opacity) {
            style.copy(opacity = effectiveOpacity)
        } else {
            style
        }

        val hasTransformNonIdentity = transform != CssTransform2D.IDENTITY
        if (style.visible && (rectsIntersect(node.bounds, clipRegion) || hasTransformNonIdentity)) {
            // Paint standard Bootstrap 5.3 box surface (shadow, focus ring, background/gradient, border, outline)
            AlienBootstrapPainter.paintBoxSurface(ctx, node.bounds, effectiveStyle)

            // Paint list marker for <li> / display: list-item nodes
            if ((node.tagName.equals("li", ignoreCase = true) || effectiveStyle.display == "list-item") &&
                effectiveStyle.listStyleType != "none"
            ) {
                AlienBootstrapPainter.paintListMarker(ctx, node.bounds, effectiveStyle, node.listItemIndex)
            }

            // Paint ::before pseudo-element
            effectiveStyle.beforeStyle?.let { bStyle ->
                AlienBootstrapPainter.paintPseudoElementBox(ctx, node.bounds, bStyle, isAfter = false)
            }

            // Paint component-specific content (or extension widget surface) with opacity + rounded/overflow clipping
            node.customPaint?.let { painter ->
                ctx.save()
                ctx.setGlobalAlpha(effectiveOpacity)
                val clipsSelf = effectiveStyle.overflow != OverflowBehavior.VISIBLE ||
                    effectiveStyle.overflowX != OverflowBehavior.VISIBLE ||
                    effectiveStyle.overflowY != OverflowBehavior.VISIBLE ||
                    effectiveStyle.borderRadiusPx > 0f ||
                    effectiveStyle.cornerRadii != null
                if (clipsSelf && node.bounds.width > 0 && node.bounds.height > 0) {
                    val radii = AlienBootstrapPainter.resolveCornerRadii(
                        node.bounds.width.toFloat(),
                        node.bounds.height.toFloat(),
                        effectiveStyle
                    )
                    ctx.beginPath()
                    if (radii[0] == radii[1] && radii[1] == radii[2] && radii[2] == radii[3]) {
                        if (radii[0] <= 0.5f) {
                            ctx.rect(
                                node.bounds.x.toFloat(),
                                node.bounds.y.toFloat(),
                                node.bounds.width.toFloat(),
                                node.bounds.height.toFloat()
                            )
                        } else {
                            ctx.roundRect(
                                node.bounds.x.toFloat(),
                                node.bounds.y.toFloat(),
                                node.bounds.width.toFloat(),
                                node.bounds.height.toFloat(),
                                radii[0]
                            )
                        }
                    } else {
                        ctx.roundRect(
                            node.bounds.x.toFloat(),
                            node.bounds.y.toFloat(),
                            node.bounds.width.toFloat(),
                            node.bounds.height.toFloat(),
                            radii[0], radii[1], radii[2], radii[3]
                        )
                    }
                    ctx.clip()
                }
                painter(ctx, node.bounds, effectiveStyle)
                ctx.restore()
            }
        }

        if (node.children.isNotEmpty()) {
            val clipsX = style.overflow != OverflowBehavior.VISIBLE || style.overflowX != OverflowBehavior.VISIBLE
            val clipsY = style.overflow != OverflowBehavior.VISIBLE || style.overflowY != OverflowBehavior.VISIBLE
            val hasOverflowClip = clipsX || clipsY

            var childClipRegion: Rect? = clipRegion
            if (hasOverflowClip) {
                val clipLeft = if (clipsX) node.bounds.x.toFloat() else 0f
                val clipTop = if (clipsY) node.bounds.y.toFloat() else 0f
                val clipRight = if (clipsX) node.bounds.right.toFloat() else ctx.width.toFloat().coerceAtLeast(clipRegion.right.toFloat())
                val clipBottom = if (clipsY) node.bounds.bottom.toFloat() else ctx.height.toFloat().coerceAtLeast(clipRegion.bottom.toFloat())

                if (clipRight <= clipLeft || clipBottom <= clipTop) {
                    childClipRegion = null
                } else {
                    ctx.save()
                    val cw = clipRight - clipLeft
                    val ch = clipBottom - clipTop
                    val radii = if (clipsX && clipsY) AlienBootstrapPainter.resolveCornerRadii(cw, ch, effectiveStyle) else FloatArray(4)
                    ctx.beginPath()
                    if (radii.any { it > 0.5f }) {
                        ctx.roundRect(clipLeft, clipTop, cw, ch, radii[0], radii[1], radii[2], radii[3])
                    } else {
                        ctx.rect(clipLeft, clipTop, cw, ch)
                    }
                    ctx.clip()

                    if (!hasTransformNonIdentity) {
                        val x0 = if (clipsX) max(clipRegion.x, node.bounds.x) else clipRegion.x
                        val y0 = if (clipsY) max(clipRegion.y, node.bounds.y) else clipRegion.y
                        val x1 = if (clipsX) min(clipRegion.right, node.bounds.right) else clipRegion.right
                        val y1 = if (clipsY) min(clipRegion.bottom, node.bounds.bottom) else clipRegion.bottom
                        childClipRegion = if (x1 > x0 && y1 > y0) Rect(x0, y0, x1 - x0, y1 - y0) else null
                    }
                }
            }

            if (childClipRegion != null) {
                val sortedChildren = node.children
                    .filter { it.visible }
                    .sortedBy { it.resolveVisualStyle().zIndex }
                for (child in sortedChildren) {
                    paintNodeHierarchy(ctx, child, childClipRegion, effectiveOpacity)
                }
            }

            if (hasOverflowClip && childClipRegion != null) {
                ctx.restore()
            }
        }

        if (style.visible && (rectsIntersect(node.bounds, clipRegion) || hasTransformNonIdentity)) {
            effectiveStyle.afterStyle?.let { aStyle ->
                AlienBootstrapPainter.paintPseudoElementBox(ctx, node.bounds, aStyle, isAfter = true)
            }
        }

        ctx.restore()
    }

    private fun rectsIntersect(a: Rect, b: Rect): Boolean =
        a.x < b.right && a.right > b.x && a.y < b.bottom && a.bottom > b.y

    private fun intersectRect(a: Rect, b: Rect): Rect? {
        val x0 = max(a.x, b.x)
        val y0 = max(a.y, b.y)
        val x1 = min(a.right, b.right)
        val y1 = min(a.bottom, b.bottom)
        return if (x1 > x0 && y1 > y0) Rect(x0, y0, x1 - x0, y1 - y0) else null
    }
}
