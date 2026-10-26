package com.nexustools.genericui.ext.overlay

/**
 * Domain-agnostic state snapshot rendered by an [AbstractOverlay] and its [OverlayElement]s.
 */
data class OverlayState(
    val title: String = "",
    val subtitle: String = "",
    val statusText: String = "",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val bufferedRanges: List<OverlayBufferedRange> = emptyList(),
    val volumePercent: Int = 100,
    val isMuted: Boolean = false,
    val playbackSpeed: Double = 1.0,
    val availableSpeeds: List<Double> = DEFAULT_PLAYBACK_SPEEDS,
    val isFullscreen: Boolean = false,
    val isInPictureInPictureMode: Boolean = false,
    val subtitleCueText: String = "",
    val audioTracks: List<OverlayTrackItem> = emptyList(),
    val subtitleTracks: List<OverlayTrackItem> = emptyList(),
    val topBadges: List<OverlayBadgeSpec> = emptyList(),
    val belowHostBadges: List<OverlayBadgeSpec> = emptyList(),
    val customProperties: Map<String, Any?> = emptyMap()
) {
    /** Normalized playback progress in `[0.0, 1.0]`. */
    val progressFraction: Double
        get() = if (durationSec > 0.0 && positionSec.isFinite()) {
            (positionSec / durationSec).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

    /** End of the furthest buffered range in seconds, or `0.0` if none. */
    val bufferedEndSec: Double
        get() = bufferedRanges.maxOfOrNull { it.endSec }?.coerceAtLeast(0.0) ?: 0.0

    /** Normalized buffered progress in `[0.0, 1.0]`. */
    val bufferedFraction: Double
        get() = if (durationSec > 0.0) {
            (bufferedEndSec / durationSec).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

    val formattedPosition: String
        get() = OverlayTimeFormatter.formatPosition(positionSec, durationSec)

    val formattedDuration: String
        get() = OverlayTimeFormatter.formatDuration(positionSec, durationSec)

    val formattedTimePair: String
        get() = OverlayTimeFormatter.formatPositionAndDuration(positionSec, durationSec)

    companion object {
        val DEFAULT_PLAYBACK_SPEEDS: List<Double> = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
    }
}
