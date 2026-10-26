package com.nexustools.genericui.ext.gpu.system

import com.nexustools.genericui.ext.gpu.VBlankSurfaceSynchronizer
import com.nexustools.genericui.ext.gpu.VBlankWatcher

/**
 * System-target (`sharedSystem`: Desktop JVM/Native, Android, iOS) implementation of
 * [VBlankSurfaceSynchronizer] for double-buffered surfaces such as `Canvas2D`.
 *
 * - When a surface's `ImageBuffer` is dirtied **inside** a VBlank callback
 *   (`isInVBlankCallback == true`), presentation/buffer-swap is queued via
 *   [VBlankWatcher.doAtEndOfCurrentVBlank] so the updated frame is presented immediately
 *   when that VBlank callback completes.
 * - When a surface's `ImageBuffer` is dirtied **outside** a VBlank callback
 *   (`isInVBlankCallback == false`), presentation/buffer-swap is scheduled for the next
 *   VBlank tick via [VBlankWatcher.requestNextVBlank].
 */
object SystemVBlankSurfaceSynchronizer : VBlankSurfaceSynchronizer {
    override fun onSurfaceDirty(
        isInVBlankCallback: Boolean,
        presentAction: () -> Unit
    ) {
        if (isInVBlankCallback) {
            VBlankWatcher.doAtEndOfCurrentVBlank(presentAction)
        } else {
            VBlankWatcher.requestNextVBlank { _, _, _ ->
                presentAction()
            }
        }
    }

    /**
     * Installs [SystemVBlankSurfaceSynchronizer] as the active synchronizer in [VBlankWatcher].
     */
    fun install() {
        VBlankWatcher.surfaceSynchronizer = this
    }
}
