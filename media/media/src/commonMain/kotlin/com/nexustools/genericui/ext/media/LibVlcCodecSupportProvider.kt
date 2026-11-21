package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec

/**
 * Transparent [MediaCodecSupportProvider] for LibVLC backends (`desktop` and `swing`) returning
 * flat `true` codec/track support and precomputed immutable capability tables with zero caching
 * or runtime probing overhead.
 */
open class LibVlcCodecSupportProvider(
    hardwareAccelerationBackend: String = DEFAULT_BACKEND_NAME
) : MediaCodecSupportProvider {

    private val capabilitiesReport: MediaCapabilitiesReport =
        if (hardwareAccelerationBackend == DEFAULT_BACKEND_NAME) {
            ALL_SUPPORTED_CAPABILITIES
        } else {
            ALL_SUPPORTED_CAPABILITIES.copy(hardwareAccelerationBackend = hardwareAccelerationBackend)
        }

    override fun queryMediaCapabilities(): MediaCapabilitiesReport = capabilitiesReport

    override fun isVideoCodecSupported(codec: VideoCodec): Boolean = true

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean = true

    override fun getSupportedVideoCodecs(): Set<VideoCodec> = ALL_VIDEO_CODECS

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = ALL_AUDIO_CODECS

    override fun supportsAudioTrackSelection(): Boolean = true

    override fun supportsSubtitleTrackSelection(): Boolean = true

    companion object {
        private const val DEFAULT_BACKEND_NAME: String = "libvlc"

        val ALL_VIDEO_CODECS: Set<VideoCodec> = VideoCodec.entries.toSet()
        val ALL_AUDIO_CODECS: Set<AudioCodec> = AudioCodec.entries.toSet()
        val ALL_CONTAINERS: Set<MediaContainer> = MediaContainer.entries.toSet()

        val ALL_SUPPORTED_CAPABILITIES: MediaCapabilitiesReport = MediaCapabilitiesReport(
            videoCodecs = VideoCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
            audioCodecs = AudioCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
            containers = ALL_CONTAINERS,
            supportsHdr10 = true,
            supportsHlg = true,
            supportsDolbyVision = true,
            supportsSurround51 = true,
            supportsSurround71 = true,
            supportsAudioTrackSelection = true,
            supportsSubtitleTrackSelection = true,
            hardwareAccelerationBackend = DEFAULT_BACKEND_NAME
        )
    }
}
