package com.nexustools.genericui.ext.overlay

/**
 * Responsive overlay chrome layout modes reconciled by [HybridOverlayDifferenceController].
 */
enum class HybridOverlayLayoutMode {
    /** Compact mobile/portrait layout with `BELOW_CONTROLS` timeline and frosted center transport buttons. */
    SMALL_MOBILE_PORTRAIT,

    /** Standard medium/wide desktop, tablet, or web layout with `ABOVE_CONTROLS` timeline and bottom transport bar. */
    MEDIUM_LARGE
}
