package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo

/**
 * Parsed container metadata extracted from a media stream header (such as ISO-BMFF / MP4).
 */
data class MediaContainerMetadata(
    val durationSeconds: Double,
    val naturalWidth: Int = 0,
    val naturalHeight: Int = 0,
    val frameCount: Int = 0,
    val videoCodec: String = "",
    val audioTracks: List<MediaTrackInfo> = emptyList(),
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList()
)
