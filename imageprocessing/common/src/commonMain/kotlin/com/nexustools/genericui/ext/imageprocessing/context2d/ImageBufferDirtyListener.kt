package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Listener notified whenever an [ImageBuffer] surface is modified by 2D drawing commands
 * or direct pixel writes on System targets.
 *
 * Used by `:extensions:canvas2d`'s `Canvas2D` to schedule double-buffer presentation via
 * `VBlankWatcher.notifySurfaceDirty` (immediate end-of-callback presentation when dirtied
 * inside a VBlank callback, or next-VBlank presentation when dirtied outside a VBlank callback).
 * On `:web`, dirty tracking is disabled (`enableDirtyTracking = false`) because HTML5 `<canvas>`
 * natively handles double buffering and screen compositing.
 */
fun interface ImageBufferDirtyListener {
    fun onImageBufferDirty(
        buffer: ImageBuffer,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int
    )
}
