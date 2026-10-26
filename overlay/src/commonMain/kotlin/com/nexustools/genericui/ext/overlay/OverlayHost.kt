package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container

/**
 * Contract implemented by [OverlayHostBox] and media/canvas components that host an [AbstractOverlay].
 */
interface OverlayHost {
    val overlayRoot: Container
    val belowHostContainer: Container
    var activeOverlay: AbstractOverlay?
    var state: OverlayState
    var actions: OverlayActions
    var isInPictureInPictureMode: Boolean

    var themeColorHex: String?
        get() = OverlayExtension.defaultThemeColorHex
        set(_) {}

    fun attachOverlay(overlay: AbstractOverlay)
    fun detachOverlay()
    fun updateState(transform: (OverlayState) -> OverlayState)
    fun setPictureInPictureMode(enabled: Boolean)

    fun updateActions(transform: (OverlayActions) -> OverlayActions) {
        actions = transform(actions)
    }
}
