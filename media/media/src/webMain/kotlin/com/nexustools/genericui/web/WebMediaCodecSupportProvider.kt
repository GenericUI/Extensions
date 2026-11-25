package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.ext.media.CachingMediaCodecSupportProvider

/**
 * Web [CachingMediaCodecSupportProvider] probing [WebBrowserDomBridge.mediaCanPlayType] on a
 * single lazy detached `<video>` / `<audio>` element without instantiating [WebVideoPlayerImpl]
 * or [WebAudioPlayerImpl].
 */
class WebMediaCodecSupportProvider : CachingMediaCodecSupportProvider() {
    private val probeVideoElement: WebDomElement by lazy { WebDomElement("video") }
    private val probeAudioElement: WebDomElement by lazy { WebDomElement("audio") }

    private fun canPlayVideoMime(mimes: List<String>): Boolean =
        mimes.any { WebBrowserDomBridge.mediaCanPlayType(probeVideoElement, it) }

    private fun canPlayAudioMime(mimes: List<String>): Boolean =
        mimes.any {
            WebBrowserDomBridge.mediaCanPlayType(probeAudioElement, it) ||
                WebBrowserDomBridge.mediaCanPlayType(probeVideoElement, it)
        }

    override fun probeVideoCodecSupported(codec: VideoCodec): Boolean =
        canPlayVideoMime(codec.html5CanPlayProbes)

    override fun probeAudioCodecSupported(codec: AudioCodec): Boolean =
        canPlayAudioMime(codec.html5CanPlayProbes)

    override fun probeMediaCapabilities(): MediaCapabilitiesReport {
        val videoMap = linkedMapOf<VideoCodec, CodecSupportLevel>()
        for (codec in VideoCodec.entries) {
            if (isVideoCodecSupported(codec)) {
                videoMap[codec] = when (codec) {
                    VideoCodec.H264,
                    VideoCodec.HEVC,
                    VideoCodec.VP9,
                    VideoCodec.AV1,
                    VideoCodec.VVC -> CodecSupportLevel.HARDWARE_ACCELERATED
                    else -> CodecSupportLevel.SUPPORTED
                }
            }
        }

        val audioMap = linkedMapOf<AudioCodec, CodecSupportLevel>()
        for (codec in AudioCodec.entries) {
            if (isAudioCodecSupported(codec)) {
                audioMap[codec] = when (codec) {
                    AudioCodec.AAC,
                    AudioCodec.AC3,
                    AudioCodec.EAC3,
                    AudioCodec.AC4 -> CodecSupportLevel.HARDWARE_ACCELERATED
                    else -> CodecSupportLevel.SUPPORTED
                }
            }
        }

        val containerSet = linkedSetOf<MediaContainer>()
        if (canPlayVideoMime(listOf("video/mp4"))) {
            containerSet.add(MediaContainer.MP4)
        }
        if (canPlayVideoMime(listOf("video/quicktime"))) {
            containerSet.add(MediaContainer.MOV)
        }
        if (canPlayVideoMime(listOf("video/webm"))) {
            containerSet.add(MediaContainer.WEBM)
        }
        if (
            canPlayVideoMime(
                listOf(
                    "video/x-matroska; codecs=\"avc1.42E01E\"",
                    "video/x-matroska; codecs=\"vp8\"",
                    "video/x-matroska"
                )
            )
        ) {
            containerSet.add(MediaContainer.MKV)
        }
        if (canPlayVideoMime(listOf("video/ogg", "audio/ogg"))) {
            containerSet.add(MediaContainer.OGG)
        }
        if (canPlayVideoMime(listOf("video/x-msvideo", "video/avi"))) {
            containerSet.add(MediaContainer.AVI)
        }
        if (canPlayVideoMime(listOf("video/mp2t"))) {
            containerSet.add(MediaContainer.MPEG_TS)
        }
        if (canPlayVideoMime(listOf("application/vnd.apple.mpegurl", "application/x-mpegurl"))) {
            containerSet.add(MediaContainer.HLS)
        }
        if (canPlayVideoMime(listOf("application/dash+xml"))) {
            containerSet.add(MediaContainer.DASH)
        }

        return MediaCapabilitiesReport(
            videoCodecs = videoMap,
            audioCodecs = audioMap,
            containers = containerSet,
            maxWidth = 3840,
            maxHeight = 2160,
            maxFrameRate = 60,
            supportsHdr10 = isVideoCodecSupported(VideoCodec.HEVC) || isVideoCodecSupported(VideoCodec.VP9) || isVideoCodecSupported(VideoCodec.AV1),
            supportsHlg = isVideoCodecSupported(VideoCodec.HEVC) || isVideoCodecSupported(VideoCodec.VP9) || isVideoCodecSupported(VideoCodec.AV1),
            supportsDolbyVision = canPlayVideoMime(listOf("video/mp4; codecs=\"dvh1.05.06\"", "video/mp4; codecs=\"dvhe.05.06\"")),
            supportsSurround51 = isAudioCodecSupported(AudioCodec.AC3) || isAudioCodecSupported(AudioCodec.EAC3) || isAudioCodecSupported(AudioCodec.OPUS) || isAudioCodecSupported(AudioCodec.AAC),
            supportsSurround71 = isAudioCodecSupported(AudioCodec.EAC3) || isAudioCodecSupported(AudioCodec.TRUEHD) || isAudioCodecSupported(AudioCodec.DTS_HD),
            supportsAudioTrackSelection = false,
            supportsSubtitleTrackSelection = true,
            hardwareAccelerationBackend = "HTML5 <video>.canPlayType"
        )
    }
}
