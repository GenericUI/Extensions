package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable

/**
 * Contract for a modular UI element mounted into an [OverlaySlot] of an [AbstractOverlay].
 */
interface OverlayElement : Disposable {
    val elementId: String
    val slot: OverlaySlot
    val component: AbstractComponent

    fun onStateChanged(state: OverlayState, actions: OverlayActions) {}

    override val isDisposed: Boolean
        get() = if (component === this) false else component.isDisposed

    override fun dispose() {
        if (component !== this) {
            component.dispose()
        }
    }
}
