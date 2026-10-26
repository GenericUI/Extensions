package com.nexustools.genericui.ext.overlay

/**
 * Generic specification for a status/metadata badge rendered in an [AbstractOverlay]
 * (for example, in [OverlaySlot.TOP_BAR] or [OverlaySlot.BELOW_HOST]).
 */
data class OverlayBadgeSpec(
    val id: String,
    val text: String = id,
    val variant: OverlayBadgeVariant = OverlayBadgeVariant.NEUTRAL,
    val tooltip: String? = null,
    val visible: Boolean = true,
    val clickable: Boolean = false,
    val backgroundColorHex: String? = null,
    val textColorHex: String? = null,
    val borderColorHex: String? = null,
    val onClick: (() -> Unit)? = null
) {
    val isClickable: Boolean
        get() = clickable || onClick != null
}
