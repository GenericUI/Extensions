package com.nexustools.genericui.ext.overlay

/**
 * Lightweight minimal [FadingOverlay] providing an auto-hiding windowed [OverlayWindowTitleBar]
 * without any custom player HUD controls.
 *
 * Automatically mounts when an [OverlayHost] / [OverlayHostBox] enables `windowTitleBarEnabled = true`
 * or `windowDragEnabled = true` with no custom overlay attached. Inherits `.overlay-fading-root`
 * so the embedded [windowTitleBar] fades atomically with `"hidden"`, while the hosting [OverlayHostBox]
 * wakes the controller on pointer movement/clicks.
 */
open class DefaultWindowTitleBarOverlay(
    titleBarConfig: OverlayWindowTitleBarConfig = OverlayWindowTitleBarConfig(enabled = true),
    autoHideSpec: AutoHideSpec = AutoHideSpec()
) : FadingOverlay(
    tagName = "genericui:default-window-titlebar-overlay",
    classPrefix = "gui-overlay overlay-fading-root default-window-titlebar-overlay",
    autoHideSpec = autoHideSpec
) {

    init {
        windowTitleBarConfig = titleBarConfig
    }
}
