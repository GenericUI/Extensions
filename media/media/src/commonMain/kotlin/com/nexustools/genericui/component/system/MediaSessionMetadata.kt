package com.nexustools.genericui.component.system

/**
 * Metadata for the active media item displayed in OS media transport controls
 * (Windows SMTC, macOS `MPNowPlayingInfoCenter`, Linux MPRIS2, Android `MediaSession`, iOS `MPNowPlayingInfoCenter`, Web `navigator.mediaSession`).
 */
data class MediaSessionMetadata(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null
)
