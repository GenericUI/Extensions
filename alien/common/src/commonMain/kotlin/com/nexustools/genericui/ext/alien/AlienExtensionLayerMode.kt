package com.nexustools.genericui.ext.alien

/**
 * Controls how [AlienContainer] and [AlienFragment] layer the self-rendered `Canvas2D`
 * above native underlay slots (such as [UnalienContainer] or extension components like
 * `GLCanvas`, `VKCanvas`, `VideoPlayer`, `AudioAnalyzer`, and `WebView`).
 */
enum class AlienExtensionLayerMode {
    /**
     * Cuts a transparent hole (`rgba(0, 0, 0, 0)`) into the [AlienContainer] / [AlienFragment]
     * `Canvas2D` at the exact bounding rectangle (and border radius) of the underlay component
     * so the native widget positioned below the `Canvas2D` shines through cleanly, and higher
     * Z-index Alien components (popovers, dropdown menus, floating dock panels, modals, overlays)
     * can still draw on top of the hole with mouse/pointer passthrough for uncovered areas.
     */
    CUT_TRANSPARENT_HOLE,

    /**
     * Draws the Alien background and Alien sibling components over the underlay component without
     * punching a transparent hole first.
     */
    DRAW_OVER
}
