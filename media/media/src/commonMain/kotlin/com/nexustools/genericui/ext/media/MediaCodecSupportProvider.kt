package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.VideoCodec

/**
 * Backend capability and codec support SPI queried directly by [MediaExtension] without
 * instantiating any video or audio player widgets or surfaces.
 */
interface MediaCodecSupportProvider {
    fun queryMediaCapabilities(): MediaCapabilitiesReport
    fun isVideoCodecSupported(codec: VideoCodec): Boolean
    fun isAudioCodecSupported(codec: AudioCodec): Boolean
    fun getSupportedVideoCodecs(): Set<VideoCodec>
    fun getSupportedAudioCodecs(): Set<AudioCodec>
    fun supportsAudioTrackSelection(): Boolean = queryMediaCapabilities().supportsAudioTrackSelection
    fun supportsSubtitleTrackSelection(): Boolean = queryMediaCapabilities().supportsSubtitleTrackSelection
}
