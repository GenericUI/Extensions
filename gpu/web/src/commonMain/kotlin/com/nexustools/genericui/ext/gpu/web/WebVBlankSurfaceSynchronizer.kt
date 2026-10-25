package com.nexustools.genericui.ext.gpu.web

import com.nexustools.genericui.ext.gpu.VBlankSurfaceSynchronizer
import com.nexustools.genericui.ext.gpu.VBlankWatcher

/**
 * Web-target (`:extensions:gpu:web`) no-op implementation of [VBlankSurfaceSynchronizer].
 *
 * On Web (`js` and `wasmJs`), the browser's native HTML5 `<canvas>` element already performs
 * double-buffered compositing synchronized with the browser compositor, so manual dirty-buffer
 * swapping and VBlank surface synchronization are not required.
 */
object WebVBlankSurfaceSynchronizer : VBlankSurfaceSynchronizer {
    override fun onSurfaceDirty(
        isInVBlankCallback: Boolean,
        presentAction: () -> Unit
    ) {
        // No-op on Web: HTML5 <canvas> natively handles double buffering and screen compositing.
    }

    /**
     * Installs [WebVBlankSurfaceSynchronizer] as the active synchronizer in [VBlankWatcher].
     */
    fun install() {
        VBlankWatcher.surfaceSynchronizer = this
    }
}
