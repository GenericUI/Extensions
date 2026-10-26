package com.nexustools.genericui.ext.overlay

/**
 * Formats media/overlay time positions and durations across all 8 backends.
 *
 * Uses `H:MM:SS` for both position and duration whenever either value is `>= 3600` seconds,
 * `M:SS` when both values are `< 3600` seconds, and `--:--` when duration is unknown or `<= 0`.
 */
object OverlayTimeFormatter {

    const val UNKNOWN_DURATION_TEXT: String = "--:--"

    /**
     * Returns `true` when either [positionSec] or [durationSec] is at least 1 hour (`3600` seconds).
     */
    fun shouldUseHourFormat(positionSec: Double, durationSec: Double): Boolean =
        (positionSec.isFinite() && positionSec >= 3600.0) ||
            (durationSec.isFinite() && durationSec >= 3600.0)

    /**
     * Formats [seconds] as `H:MM:SS` when [forceHours] is `true` or `seconds >= 3600`, otherwise `M:SS`.
     */
    fun formatSeconds(seconds: Double, forceHours: Boolean = false): String {
        if (!seconds.isFinite() || seconds < 0.0) {
            return if (forceHours) "0:00:00" else "0:00"
        }
        val totalSeconds = seconds.toLong()
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val secs = totalSeconds % 60L
        val secStr = secs.toString().padStart(2, '0')
        return if (forceHours || hours > 0L) {
            val minStr = minutes.toString().padStart(2, '0')
            "$hours:$minStr:$secStr"
        } else {
            "$minutes:$secStr"
        }
    }

    /**
     * Formats [positionSec] in the context of [durationSec] (`H:MM:SS` if either is `>= 3600`, else `M:SS`).
     */
    fun formatPosition(positionSec: Double, durationSec: Double = 0.0): String =
        formatSeconds(
            seconds = if (positionSec.isFinite()) positionSec.coerceAtLeast(0.0) else 0.0,
            forceHours = shouldUseHourFormat(positionSec, durationSec)
        )

    /**
     * Formats [durationSec] in the context of [positionSec] (`--:--` if `durationSec <= 0`,
     * `H:MM:SS` if either is `>= 3600`, else `M:SS`).
     */
    fun formatDuration(positionSec: Double = 0.0, durationSec: Double): String {
        if (!durationSec.isFinite() || durationSec <= 0.0) {
            return UNKNOWN_DURATION_TEXT
        }
        return formatSeconds(
            seconds = durationSec,
            forceHours = shouldUseHourFormat(positionSec, durationSec)
        )
    }

    /**
     * Formats [positionSec] and [durationSec] as a combined `"position / duration"` label.
     */
    fun formatPositionAndDuration(
        positionSec: Double,
        durationSec: Double,
        separator: String = " / "
    ): String = "${formatPosition(positionSec, durationSec)}$separator${formatDuration(positionSec, durationSec)}"
}
