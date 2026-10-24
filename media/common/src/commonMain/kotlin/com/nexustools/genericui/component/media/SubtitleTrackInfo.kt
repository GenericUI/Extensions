package com.nexustools.genericui.component.media

/**
 * Metadata for an embedded or external subtitle/closed-caption track inside a [VideoPlayer].
 */
data class SubtitleTrackInfo(
    val id: String,
    val label: String,
    val language: String = "en",
    val format: String = "vtt",
    val externalUrl: String? = null,
    val isForced: Boolean = false,
    val isSelected: Boolean = false
)
