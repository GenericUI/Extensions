package com.nexustools.genericui.ext.overlay

/**
 * Represents a contiguous buffered time range `[startSec, endSec]` in seconds.
 */
data class OverlayBufferedRange(
    val startSec: Double,
    val endSec: Double
) {
    val durationSec: Double
        get() = (endSec - startSec).coerceAtLeast(0.0)
}
