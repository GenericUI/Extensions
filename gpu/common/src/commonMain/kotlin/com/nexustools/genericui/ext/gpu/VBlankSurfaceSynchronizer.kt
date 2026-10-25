package com.nexustools.genericui.ext.gpu

/**
 * Platform-specific strategy that synchronizes dirty double-buffered surfaces with VBlank.
 *
 * - On System targets (`:extensions:gpu:sharedSystem`), `SystemVBlankSurfaceSynchronizer` schedules
 *   the surface swap/presentation immediately at the end of the current VBlank callback when dirtied
 *   inside a VBlank callback (`isInVBlankCallback == true`), or coalesces and schedules presentation
 *   on the next VBlank tick when dirtied outside a VBlank callback (`isInVBlankCallback == false`).
 * - On Web targets (`:extensions:gpu:web`), `WebVBlankSurfaceSynchronizer` is a no-op because the
 *   browser's HTML5 `<canvas>` element natively handles double buffering and screen compositing.
 */
fun interface VBlankSurfaceSynchronizer {
    fun onSurfaceDirty(
        isInVBlankCallback: Boolean,
        presentAction: () -> Unit
    )
}
