package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.core.AbstractComponent

/**
 * Pure host contract that binds a target component (`VideoPlayer`, `Canvas2D`, `GLCanvas`,
 * `VKCanvas`, or any [AbstractComponent]) to an optional [AbstractOverlay] without
 * auto-attaching any default preset overlay or background scrim.
 */
interface OverlayHost {
    val targetComponent: AbstractComponent
    var overlay: AbstractOverlay?
    var activeOverlay: AbstractOverlay?
        get() = overlay
        set(value) {
            overlay = value
        }
    val overlayRoot: LayeredContainer
    var state: OverlayState
    var actions: OverlayActions
    var isInPictureInPictureMode: Boolean

    var themeColorHex: String?
        get() = OverlayExtension.defaultThemeColorHex
        set(_) {}

    fun attachOverlay(overlay: AbstractOverlay)
    fun detachOverlay()

    fun syncWithTargetRender(frameTimestampMs: Long = 0L)
    fun syncWithTargetResize(widthPx: Int, heightPx: Int)

    fun updateState(transform: (OverlayState) -> OverlayState)
    fun setPictureInPictureMode(enabled: Boolean)

    fun updateActions(transform: (OverlayActions) -> OverlayActions) {
        actions = transform(actions)
    }
}
