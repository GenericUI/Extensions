package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.image.ImageBuffer

/**
 * Now-playing metadata published to system media transport controls (Windows SMTC, macOS Now Playing,
 * Linux MPRIS, Web `navigator.mediaSession`).
 */
data class MediaControlMetadata(
    val title: String = "",
    val subtitle: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: ImageBuffer? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackRate: Float = 1.0f
)
