package com.nexustools.genericui.ext.gpu.system

import kotlin.math.max
import kotlin.math.min

/**
 * Shared system-target (`:extensions:gpu:sharedSystem`) bounding dirty-region tracker used by
 * GPU-accelerated 2D/3D surfaces and image processors to avoid full-framebuffer copies and
 * RGBA-to-BGRA swizzles when only a sub-rectangle changed.
 */
class SystemGpuDirtyRegion(
    var surfaceWidth: Int,
    var surfaceHeight: Int
) {
    var minX: Int = 0
        private set
    var minY: Int = 0
        private set
    var maxX: Int = max(1, surfaceWidth)
        private set
    var maxY: Int = max(1, surfaceHeight)
        private set
    var isDirty: Boolean = true
        private set

    fun markAllDirty() {
        minX = 0
        minY = 0
        maxX = max(1, surfaceWidth)
        maxY = max(1, surfaceHeight)
        isDirty = true
    }

    fun markRectDirty(x0: Int, y0: Int, x1: Int, y1: Int) {
        val sx = x0.coerceIn(0, surfaceWidth)
        val sy = y0.coerceIn(0, surfaceHeight)
        val ex = x1.coerceIn(sx, surfaceWidth)
        val ey = y1.coerceIn(sy, surfaceHeight)
        if (ex <= sx || ey <= sy) return
        if (!isDirty) {
            minX = sx
            minY = sy
            maxX = ex
            maxY = ey
            isDirty = true
        } else {
            minX = min(minX, sx)
            minY = min(minY, sy)
            maxX = max(maxX, ex)
            maxY = max(maxY, ey)
        }
    }

    fun clearDirty() {
        isDirty = false
        minX = surfaceWidth
        minY = surfaceHeight
        maxX = 0
        maxY = 0
    }

    fun resize(newWidth: Int, newHeight: Int) {
        surfaceWidth = max(1, newWidth)
        surfaceHeight = max(1, newHeight)
        markAllDirty()
    }
}
