package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.Disposable

/**
 * Handle returned by [AbstractCanvas.registerVBlankRender] or `VBlankWatcher.watch` allowing
 * the caller to pause, resume, or cancel the VBlank render loop subscription.
 */
interface VBlankSubscription : Disposable {
    val isActive: Boolean
    fun pause()
    fun resume()
}
