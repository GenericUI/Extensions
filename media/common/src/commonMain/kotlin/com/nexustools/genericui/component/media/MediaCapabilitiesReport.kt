package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec

/**
 * Uniform cross-platform media codec, container, resolution, HDR, and surround-audio capability report
 * returned by [VideoPlayer.getMediaCapabilities].
 *
 * Allows applications to build transcoding/direct-play profiles (`clientCapabilities`)
 * without platform-specific codec probing logic.
 */
data class MediaCapabilitiesReport(
    val videoCodecs: Map<VideoCodec, CodecSupportLevel>,
    val audioCodecs: Map<AudioCodec, CodecSupportLevel>,
    val containers: Set<MediaContainer>,
    val maxWidth: Int = 3840,
    val maxHeight: Int = 2160,
    val maxFrameRate: Int = 60,
    val supportsHdr10: Boolean = false,
    val supportsHlg: Boolean = false,
    val supportsDolbyVision: Boolean = false,
    val supportsSurround51: Boolean = false,
    val supportsSurround71: Boolean = false,
    val hardwareAccelerationBackend: String = "Software"
) {
    fun supportsVideo(codec: VideoCodec): Boolean =
        (videoCodecs[codec] ?: CodecSupportLevel.UNSUPPORTED) != CodecSupportLevel.UNSUPPORTED

    fun isHardwareAccelerated(codec: VideoCodec): Boolean =
        videoCodecs[codec] == CodecSupportLevel.HARDWARE_ACCELERATED

    fun supportsAudio(codec: AudioCodec): Boolean =
        (audioCodecs[codec] ?: CodecSupportLevel.UNSUPPORTED) != CodecSupportLevel.UNSUPPORTED

    fun supportsContainer(container: MediaContainer): Boolean =
        container in containers
}
