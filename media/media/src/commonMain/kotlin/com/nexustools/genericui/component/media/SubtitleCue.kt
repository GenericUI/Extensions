package com.nexustools.genericui.component.media

/**
 * Timed subtitle cue parsed from a WebVTT or SubRip (`.srt`) stream by [WebVttSrtParser].
 */
data class SubtitleCue(
    val id: String,
    val startTimeSeconds: Double,
    val endTimeSeconds: Double,
    val text: String,
    val linePositionPercent: Float? = null
) {
    fun isActiveAt(timeSeconds: Double): Boolean =
        timeSeconds >= startTimeSeconds && timeSeconds <= endTimeSeconds
}
