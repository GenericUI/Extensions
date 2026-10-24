package com.nexustools.genericui.enums.media

/**
 * Standardized video codecs reported by [com.nexustools.genericui.component.media.VideoPlayer.getMediaCapabilities].
 */
enum class VideoCodec(val wireName: String, val standardMimeCodecs: List<String>) {
    H264("h264", listOf("avc1.42E01E", "avc1.4D401F", "avc1.640028")),
    HEVC("hevc", listOf("hev1.1.6.L93.B0", "hvc1.1.6.L93.B0", "h265")),
    VP8("vp8", listOf("vp8")),
    VP9("vp9", listOf("vp09.00.10.08", "vp9")),
    AV1("av1", listOf("av01.0.05M.08", "av1")),
    MPEG2("mpeg2video", listOf("mp2v", "mpeg2")),
    MPEG4("mpeg4", listOf("mp4v.20.8", "mp4v")),
    THEORA("theora", listOf("theora")),
    VC1("vc1", listOf("vc-1"));

    companion object {
        fun fromWireName(name: String): VideoCodec? {
            val normalized = name.trim().lowercase()
            if (normalized == "h265" || normalized == "hev1" || normalized == "hvc1") return HEVC
            if (normalized == "avc1" || normalized == "avc") return H264
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}
