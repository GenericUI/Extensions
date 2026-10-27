package com.nexustools.genericui.ext.alien

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

        val rootStyle = session.rootNode.resolveVisualStyle()

        for (region in regions) {
            paintDirtyRegion(ctx, region, rootStyle, session.rootNode)
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
        rootNode: AlienWidgetNode
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
        val baseBg = when {
            rootStyle.backgroundColor.a > 0 -> rootStyle.backgroundColor
            rootNode.componentClassPrefix == "alien-root" ||
                rootNode.tagName.equals("genericui:root", ignoreCase = true) ||
                !rootStyle.backgroundImageUrl.isNullOrBlank() -> RgbaColor.BLACK
            rootStyle.darkTheme -> AlienBootstrapThemeTokens.BODY_BG_DARK
            else -> AlienBootstrapThemeTokens.BODY_BG_LIGHT
        }
        ctx.setFillColor(baseBg)
        ctx.fillRect(region.x.toFloat(), region.y.toFloat(), region.width.toFloat(), region.height.toFloat())

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
            // Positioned nodes already have `translate(dx, dy)` applied to `node.bounds` in AlienLayoutEngine
            val (dx, dy) = if (isPositioned) {
                0f to 0f
            } else {
                transform.resolveTranslationPx(node.bounds.width, node.bounds.height)
            }
            val cx = node.bounds.x + node.bounds.width * 0.5f
            val cy = node.bounds.y + node.bounds.height * 0.5f
            if (dx != 0f || dy != 0f || transform.rotateDeg != 0f || transform.scaleX != 1f || transform.scaleY != 1f) {
                ctx.translate(cx + dx, cy + dy)
                if (transform.rotateDeg != 0f) {
                    ctx.rotate(transform.rotateDeg * (3.1415927f / 180f))
                }
                if (transform.scaleX != 1f || transform.scaleY != 1f) {
                    ctx.scale(transform.scaleX, transform.scaleY)
                }
                ctx.translate(-cx, -cy)
            }
        }

        val effectiveStyle = if (effectiveOpacity != style.opacity) {
            style.copy(opacity = effectiveOpacity)
        } else {
            style
        }

        if (style.visible && (rectsIntersect(node.bounds, clipRegion) || transform.scaleX != 1f || transform.scaleY != 1f)) {
            // Paint standard Bootstrap 5.3 box surface (shadow, focus ring, background/gradient, border)
            AlienBootstrapPainter.paintBoxSurface(ctx, node.bounds, effectiveStyle)

            // Paint component-specific content (button label, input text/caret, progress bar, checkbox, image, etc.)
            node.customPaint?.invoke(ctx, node.bounds, effectiveStyle)
        }

        if (node.children.isNotEmpty()) {
            val sortedChildren = node.children
                .filter { it.visible }
                .sortedBy { it.resolveVisualStyle().zIndex }
            for (child in sortedChildren) {
                paintNodeHierarchy(ctx, child, clipRegion, effectiveOpacity)
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
