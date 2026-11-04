package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable

/**
 * Lightweight wrapper around any [AbstractComponent] mounted inside an [AbstractOverlay]
 * with a [LayerConstraint] ([com.nexustools.genericui.component.layout.LayerSizingMode.FILL]
 * or [com.nexustools.genericui.component.layout.LayerSizingMode.FLOAT]).
 */
open class OverlayElement(
    open val component: AbstractComponent = Container("genericui-overlay-element"),
    var constraint: LayerConstraint = LayerConstraint.fill(),
    val elementId: String = component.id ?: "overlay-element"
) : Disposable {

    private var disposedState: Boolean = false

    override val isDisposed: Boolean
        get() = disposedState

    var isVisible: Boolean
        get() = component.isVisible
        set(value) {
            component.isVisible = value
        }

    open fun onStateChanged(state: OverlayState, actions: OverlayActions) {}

    open fun onTargetRenderFrame(frameTimestampMs: Long, targetWidthPx: Int, targetHeightPx: Int) {}

    open fun onTargetResize(targetWidthPx: Int, targetHeightPx: Int) {}

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        if (component !== this) {
            component.dispose()
        }
    }
}
