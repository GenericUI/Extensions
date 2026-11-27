package com.nexustools.genericui.ext.webview.desktop.engine

/**
 * Parsed WebVTT subtitle cue associated with an HTML `<track>` element.
 */
data class WebVttCue(
    val id: String = "",
    val startTime: Double,
    val endTime: Double,
    val text: String
)
