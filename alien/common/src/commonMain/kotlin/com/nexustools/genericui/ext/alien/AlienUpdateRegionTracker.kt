package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks and coalesces dirty update regions (`Rect`) inside an [AlienRenderSession].
 *
 * - Coalesces overlapping or adjacent rectangles (`gap <= coalesceThresholdPx`) to minimize clip changes.
 * - Clips all dirty rectangles to the current viewport (`0, 0, viewportWidth, viewportHeight`).
 * - Falls back to a single full-viewport dirty region when `dirtyRegions.size > maxRegionsBeforeFullRepaint`.
 */
class AlienUpdateRegionTracker(
    val coalesceThresholdPx: Int = 8,
    val maxRegionsBeforeFullRepaint: Int = 12
) {
    private val dirtyRegions = mutableListOf<Rect>()
    var fullRepaintPending: Boolean = true
        private set

    val hasDirtyRegions: Boolean
        get() = fullRepaintPending || dirtyRegions.isNotEmpty()

    val dirtyRegionCount: Int
        get() = if (fullRepaintPending) 1 else dirtyRegions.size

    /**
     * Marks the entire viewport as needing a full repaint on the next frame.
     */
    fun markFullViewportDirty() {
        fullRepaintPending = true
        dirtyRegions.clear()
    }

    /**
     * Marks a specific rectangular region (plus optional shadow/border-bleed [padPx]) as dirty.
     */
    fun markDirty(rect: Rect, padPx: Int = 4) {
        if (fullRepaintPending) return
        if (rect.width <= 0 || rect.height <= 0) return

        val padded = Rect(
            x = rect.x - padPx,
            y = rect.y - padPx,
            width = rect.width + padPx * 2,
            height = rect.height + padPx * 2
        )

        var merged = padded
        val iterator = dirtyRegions.iterator()
        while (iterator.hasNext()) {
            val existing = iterator.next()
            if (shouldCoalesce(existing, merged, coalesceThresholdPx)) {
                merged = unionRect(existing, merged)
                iterator.remove()
            }
        }
        dirtyRegions.add(merged)

        if (dirtyRegions.size > maxRegionsBeforeFullRepaint) {
            markFullViewportDirty()
        }
    }

    /**
     * Marks both [oldBounds] and [newBounds] as dirty when a widget moves, resizes, or changes visibility.
     */
    fun markBoundsTransitionDirty(oldBounds: Rect, newBounds: Rect, padPx: Int = 6) {
        markDirty(oldBounds, padPx)
        if (oldBounds != newBounds) {
            markDirty(newBounds, padPx)
        }
    }

    /**
     * Drains and returns the coalesced, viewport-clipped dirty regions for the current repaint pass,
     * clearing the pending dirty queue.
     */
    fun drainDirtyRegions(viewportWidth: Int, viewportHeight: Int): List<Rect> {
        val safeW = viewportWidth.coerceAtLeast(1)
        val safeH = viewportHeight.coerceAtLeast(1)
        val fullViewport = Rect(0, 0, safeW, safeH)

        if (fullRepaintPending) {
            fullRepaintPending = false
            dirtyRegions.clear()
            return listOf(fullViewport)
        }

        if (dirtyRegions.isEmpty()) return emptyList()

        val clipped = dirtyRegions.mapNotNull { r ->
            clipToViewport(r, safeW, safeH)
        }
        dirtyRegions.clear()
        return if (clipped.isEmpty()) emptyList() else clipped
    }

    /**
     * Inspects the currently queued dirty rectangles without draining them.
     */
    fun peekDirtyRegions(viewportWidth: Int, viewportHeight: Int): List<Rect> {
        val safeW = viewportWidth.coerceAtLeast(1)
        val safeH = viewportHeight.coerceAtLeast(1)
        if (fullRepaintPending) {
            return listOf(Rect(0, 0, safeW, safeH))
        }
        return dirtyRegions.mapNotNull { clipToViewport(it, safeW, safeH) }
    }

    private fun shouldCoalesce(a: Rect, b: Rect, threshold: Int): Boolean {
        val aLeft = a.x - threshold
        val aTop = a.y - threshold
        val aRight = a.right + threshold
        val aBottom = a.bottom + threshold
        return b.x <= aRight && b.right >= aLeft && b.y <= aBottom && b.bottom >= aTop
    }

    private fun unionRect(a: Rect, b: Rect): Rect {
        val minX = min(a.x, b.x)
        val minY = min(a.y, b.y)
        val maxX = max(a.right, b.right)
        val maxY = max(a.bottom, b.bottom)
        return Rect(minX, minY, (maxX - minX).coerceAtLeast(1), (maxY - minY).coerceAtLeast(1))
    }

    private fun clipToViewport(rect: Rect, viewportW: Int, viewportH: Int): Rect? {
        val x0 = rect.x.coerceIn(0, viewportW)
        val y0 = rect.y.coerceIn(0, viewportH)
        val x1 = rect.right.coerceIn(0, viewportW)
        val y1 = rect.bottom.coerceIn(0, viewportH)
        val w = x1 - x0
        val h = y1 - y0
        return if (w > 0 && h > 0) Rect(x0, y0, w, h) else null
    }
}
