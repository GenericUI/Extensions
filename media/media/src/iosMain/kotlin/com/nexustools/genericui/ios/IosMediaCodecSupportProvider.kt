@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.ext.media.CachingMediaCodecSupportProvider
import platform.AVFoundation.AVURLAsset

/**
 * Apple iOS & iPadOS [CachingMediaCodecSupportProvider] querying
 * `AVURLAsset.isPlayableExtendedMIMEType` directly without creating any `AVPlayer` or `UIView`.
 */
class IosMediaCodecSupportProvider : CachingMediaCodecSupportProvider() {

    private fun isExtendedMimePlayable(probes: List<String>): Boolean =
        probes.any { probe ->
            runCatching { AVURLAsset.isPlayableExtendedMIMEType(probe) }.getOrDefault(false)
        }

    override fun probeVideoCodecSupported(codec: VideoCodec): Boolean =
        isExtendedMimePlayable(codec.html5CanPlayProbes)

    override fun probeAudioCodecSupported(codec: AudioCodec): Boolean =
        isExtendedMimePlayable(codec.html5CanPlayProbes)

    override fun probeMediaCapabilities(): MediaCapabilitiesReport {
        val videoMap = linkedMapOf<VideoCodec, CodecSupportLevel>()
        for (codec in VideoCodec.entries) {
            if (isVideoCodecSupported(codec)) {
                videoMap[codec] = when (codec) {
                    VideoCodec.H264,
                    VideoCodec.HEVC,
                    VideoCodec.VP9,
                    VideoCodec.AV1,
                    VideoCodec.PRORES -> CodecSupportLevel.HARDWARE_ACCELERATED
                    else -> CodecSupportLevel.SUPPORTED
                }
            }
        }
        val audioMap = linkedMapOf<AudioCodec, CodecSupportLevel>()
        for (codec in AudioCodec.entries) {
            if (isAudioCodecSupported(codec)) {
                audioMap[codec] = when (codec) {
                    AudioCodec.AAC,
                    AudioCodec.ALAC,
                    AudioCodec.AC3,
                    AudioCodec.EAC3 -> CodecSupportLevel.HARDWARE_ACCELERATED
                    else -> CodecSupportLevel.SUPPORTED
                }
            }
        }
        val containerSet = linkedSetOf<MediaContainer>()
        if (isExtendedMimePlayable(listOf("video/mp4"))) containerSet.add(MediaContainer.MP4)
        if (isExtendedMimePlayable(listOf("video/quicktime"))) containerSet.add(MediaContainer.MOV)
        if (isExtendedMimePlayable(listOf("application/vnd.apple.mpegurl", "application/x-mpegurl"))) {
            containerSet.add(MediaContainer.HLS)
        }

        return MediaCapabilitiesReport(
            videoCodecs = videoMap,
            audioCodecs = audioMap,
            containers = containerSet,
            maxWidth = 3840,
            maxHeight = 2160,
            maxFrameRate = 120,
            supportsHdr10 = VideoCodec.HEVC in videoMap || VideoCodec.AV1 in videoMap,
            supportsHlg = VideoCodec.HEVC in videoMap || VideoCodec.AV1 in videoMap,
            supportsDolbyVision = isExtendedMimePlayable(
                listOf("video/mp4; codecs=\"dvh1.05.06\"", "video/mp4; codecs=\"dvhe.05.06\"")
            ),
            supportsSurround51 = AudioCodec.AC3 in audioMap || AudioCodec.EAC3 in audioMap || AudioCodec.AAC in audioMap,
            supportsSurround71 = AudioCodec.EAC3 in audioMap,
            supportsAudioTrackSelection = true,
            supportsSubtitleTrackSelection = true,
            hardwareAccelerationBackend = "AVPlayer / AVURLAsset / VideoToolbox"
        )
    }
}
