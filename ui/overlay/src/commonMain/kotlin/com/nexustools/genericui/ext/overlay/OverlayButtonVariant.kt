package com.nexustools.genericui.ext.overlay

/**
 * Sizing and border chrome variants for [OverlayControlButton].
 */
enum class OverlayButtonVariant(
    val sizePx: Int,
    val cornerRadiusPx: Float,
    val isBorderless: Boolean = false
) {
    /** Standalone `40x40` translucent circular HUD button. */
    CIRCLE_PILL(sizePx = 40, cornerRadiusPx = 20f, isBorderless = false),

    /** Primary center-stage `64x64` circular action button (`data-overlay-role="primary-center-action"`). */
    CENTER_PRIMARY(sizePx = 64, cornerRadiusPx = 32f, isBorderless = false),

    /** Secondary center-stage `56x56` circular transport button (e.g., Rewind / Fast-Forward). */
    CENTER_SECONDARY(sizePx = 56, cornerRadiusPx = 28f, isBorderless = false),

    /** Borderless `32x32` action button mounted inside a `40px` pill container. */
    INNER_PILL(sizePx = 32, cornerRadiusPx = 16f, isBorderless = true)
}
