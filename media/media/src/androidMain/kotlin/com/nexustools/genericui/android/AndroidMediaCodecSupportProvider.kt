package com.nexustools.genericui.android

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.ext.media.CachingMediaCodecSupportProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Android [CachingMediaCodecSupportProvider] querying `MediaCodecList(MediaCodecList.ALL_CODECS)`
 * directly with zero player or `TextureView` instantiation.
 */
class AndroidMediaCodecSupportProvider : CachingMediaCodecSupportProvider() {

    private val cachedCodecInfos: Array<MediaCodecInfo> by lazy {
        runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos ?: emptyArray() }
            .getOrDefault(emptyArray())
    }

    private val cachedSupportByMimeKey = ConcurrentHashMap<String, CodecSupportLevel>()

    private fun queryDecoderSupportLevel(mimeTypes: Set<String>): CodecSupportLevel {
        val key = mimeTypes.map { it.lowercase() }.sorted().joinToString("|")
        return cachedSupportByMimeKey.getOrPut(key) {
            runCatching {
                var foundSoftware = false
                for (info in cachedCodecInfos) {
                    if (info.isEncoder) continue
                    val supported = info.supportedTypes ?: continue
                    val matches = supported.any { supportedMime ->
                        mimeTypes.any { targetMime -> supportedMime.equals(targetMime, ignoreCase = true) }
                    }
                    if (matches) {
                        val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            info.isHardwareAccelerated
                        } else {
                            val lowerName = info.name.lowercase()
                            !lowerName.startsWith("omx.google.") &&
                                !lowerName.startsWith("c2.android.") &&
                                !lowerName.contains(".sw.")
                        }
                        if (isHardware) {
                            return@getOrPut CodecSupportLevel.HARDWARE_ACCELERATED
                        }
                        foundSoftware = true
                    }
                }
                if (foundSoftware) CodecSupportLevel.SOFTWARE_ONLY else CodecSupportLevel.UNSUPPORTED
            }.getOrDefault(CodecSupportLevel.UNSUPPORTED)
        }
    }

    override fun probeVideoCodecSupported(codec: VideoCodec): Boolean =
        queryDecoderSupportLevel(codec.mimeTypes) != CodecSupportLevel.UNSUPPORTED

    override fun probeAudioCodecSupported(codec: AudioCodec): Boolean =
        queryDecoderSupportLevel(codec.mimeTypes) != CodecSupportLevel.UNSUPPORTED

    override fun probeMediaCapabilities(): MediaCapabilitiesReport {
        val videoMap = linkedMapOf<VideoCodec, CodecSupportLevel>()
        for (codec in VideoCodec.entries) {
            val level = queryDecoderSupportLevel(codec.mimeTypes)
            if (level != CodecSupportLevel.UNSUPPORTED) {
                videoMap[codec] = level
            }
        }
        val audioMap = linkedMapOf<AudioCodec, CodecSupportLevel>()
        for (codec in AudioCodec.entries) {
            val level = queryDecoderSupportLevel(codec.mimeTypes)
            if (level != CodecSupportLevel.UNSUPPORTED) {
                audioMap[codec] = level
            }
        }
        return MediaCapabilitiesReport(
            videoCodecs = videoMap,
            audioCodecs = audioMap,
            containers = setOf(
                MediaContainer.MP4,
                MediaContainer.MKV,
                MediaContainer.WEBM,
                MediaContainer.HLS,
                MediaContainer.DASH
            ),
            supportsHdr10 = VideoCodec.HEVC in videoMap || VideoCodec.VP9 in videoMap || VideoCodec.AV1 in videoMap,
            supportsHlg = VideoCodec.HEVC in videoMap || VideoCodec.VP9 in videoMap || VideoCodec.AV1 in videoMap,
            supportsDolbyVision = queryDecoderSupportLevel(setOf("video/dolby-vision")) != CodecSupportLevel.UNSUPPORTED,
            supportsSurround51 = AudioCodec.AC3 in audioMap || AudioCodec.EAC3 in audioMap || AudioCodec.AAC in audioMap,
            supportsSurround71 = AudioCodec.EAC3 in audioMap || AudioCodec.TRUEHD in audioMap || AudioCodec.DTS_HD in audioMap,
            supportsAudioTrackSelection = true,
            supportsSubtitleTrackSelection = true,
            hardwareAccelerationBackend = "Android MediaCodecList / TextureView"
        )
    }
}
