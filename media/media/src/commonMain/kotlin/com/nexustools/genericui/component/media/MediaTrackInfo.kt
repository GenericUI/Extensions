package com.nexustools.genericui.component.media

/**
 * Metadata for an audio or video stream track inside a [VideoPlayer] media item.
 */
data class MediaTrackInfo(
    val id: String,
    val label: String,
    val language: String = "und",
    val codec: String = "",
    val channels: Int = 2,
    val sampleRateHz: Int = 48000,
    val bitrateBps: Long = 0L,
    val isDefault: Boolean = false,
    val isSelected: Boolean = false
)
