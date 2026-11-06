package com.nexustools.genericui.enums.media

/**
 * Comprehensive enum of video codecs supported by VLC (`libvlc` / FFmpeg `libavcodec`),
 * with canonical wire names, aliases, OS decoder MIME types, and HTML5 `<video>.canPlayType(...)`
 * probe strings for real runtime verification across all media backends.
 */
enum class VideoCodec(
    val wireName: String,
    val aliases: Set<String>,
    val mimeTypes: Set<String>,
    val html5CanPlayProbes: List<String>
) {
    H264(
        wireName = "h264",
        aliases = setOf("h264", "avc", "avc1", "x264", "h.264", "mpeg4-avc"),
        mimeTypes = setOf("video/avc", "video/h264"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"avc1.42E01E\"",
            "video/mp4; codecs=\"avc1.4D401F\"",
            "video/mp4; codecs=\"avc1.640028\""
        )
    ),
    HEVC(
        wireName = "hevc",
        aliases = setOf("hevc", "h265", "hev1", "hvc1", "x265", "h.265", "mpeg-h-part2"),
        mimeTypes = setOf("video/hevc", "video/h265"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"hev1.1.6.L93.B0\"",
            "video/mp4; codecs=\"hvc1.1.6.L93.B0\"",
            "video/mp4; codecs=\"hev1\"",
            "video/mp4; codecs=\"hvc1\""
        )
    ),
    VP8(
        wireName = "vp8",
        aliases = setOf("vp8", "vp80", "on2-vp8"),
        mimeTypes = setOf("video/x-vnd.on2.vp8", "video/vp8"),
        html5CanPlayProbes = listOf(
            "video/webm; codecs=\"vp8\"",
            "video/mp4; codecs=\"vp08.00.10.08\""
        )
    ),
    VP9(
        wireName = "vp9",
        aliases = setOf("vp9", "vp90", "vp09"),
        mimeTypes = setOf("video/x-vnd.on2.vp9", "video/vp9"),
        html5CanPlayProbes = listOf(
            "video/webm; codecs=\"vp9\"",
            "video/webm; codecs=\"vp09.00.10.08\"",
            "video/mp4; codecs=\"vp09.00.10.08\""
        )
    ),
    AV1(
        wireName = "av1",
        aliases = setOf("av1", "av01", "aom-av1"),
        mimeTypes = setOf("video/av01", "video/av1"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"av01.0.05M.08\"",
            "video/webm; codecs=\"av01.0.05M.08\"",
            "video/webm; codecs=\"av1\""
        )
    ),
    VVC(
        wireName = "vvc",
        aliases = setOf("vvc", "h266", "h.266", "vvc1", "vvi1"),
        mimeTypes = setOf("video/vvc", "video/h266"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"vvc1.1.L83.CQA\"",
            "video/mp4; codecs=\"vvi1.1.L83.CQA\"",
            "video/mp4; codecs=\"vvc1\""
        )
    ),
    MPEG1(
        wireName = "mpeg1video",
        aliases = setOf("mpeg1video", "mpeg1", "mp1v", "mpg1"),
        mimeTypes = setOf("video/mpeg", "video/mpeg1"),
        html5CanPlayProbes = listOf(
            "video/mpeg; codecs=\"mpeg1\"",
            "video/mp4; codecs=\"mp4v.6A\"",
            "video/mp4; codecs=\"mp4v.6B\""
        )
    ),
    MPEG2(
        wireName = "mpeg2video",
        aliases = setOf("mpeg2video", "mpeg2", "mp2v", "mpg2", "h262", "h.262"),
        mimeTypes = setOf("video/mpeg2", "video/mp2v"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"mp4v.61\"",
            "video/mp4; codecs=\"mp2v\"",
            "video/mp2t; codecs=\"mp2v\""
        )
    ),
    MPEG4(
        wireName = "mpeg4",
        aliases = setOf("mpeg4", "mp4v", "divx", "xvid", "fmp4", "dx50", "mp4v-es"),
        mimeTypes = setOf("video/mp4v-es", "video/mpeg4"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"mp4v.20.8\"",
            "video/mp4; codecs=\"mp4v.20.240\""
        )
    ),
    H263(
        wireName = "h263",
        aliases = setOf("h263", "h263p", "h263i", "h.263", "s263"),
        mimeTypes = setOf("video/3gpp", "video/h263"),
        html5CanPlayProbes = listOf(
            "video/3gpp; codecs=\"s263\"",
            "video/mp4; codecs=\"s263\""
        )
    ),
    THEORA(
        wireName = "theora",
        aliases = setOf("theora", "theo", "thra"),
        mimeTypes = setOf("video/theora", "video/x-theora"),
        html5CanPlayProbes = listOf(
            "video/ogg; codecs=\"theora\""
        )
    ),
    VC1(
        wireName = "vc1",
        aliases = setOf("vc1", "vc-1", "wvc1", "wmva"),
        mimeTypes = setOf("video/vc1", "video/wvc1"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"vc-1\"",
            "video/x-ms-wmv; codecs=\"vc-1\""
        )
    ),
    WMV1(
        wireName = "wmv1",
        aliases = setOf("wmv1", "wmv7"),
        mimeTypes = setOf("video/x-ms-wmv", "video/x-wmv1"),
        html5CanPlayProbes = listOf(
            "video/x-ms-wmv; codecs=\"wmv1\""
        )
    ),
    WMV2(
        wireName = "wmv2",
        aliases = setOf("wmv2", "wmv8"),
        mimeTypes = setOf("video/x-ms-wmv", "video/x-wmv2"),
        html5CanPlayProbes = listOf(
            "video/x-ms-wmv; codecs=\"wmv2\""
        )
    ),
    WMV3(
        wireName = "wmv3",
        aliases = setOf("wmv3", "wmv9"),
        mimeTypes = setOf("video/x-ms-wmv", "video/x-wmv3"),
        html5CanPlayProbes = listOf(
            "video/x-ms-wmv; codecs=\"wmv3\""
        )
    ),
    MJPEG(
        wireName = "mjpeg",
        aliases = setOf("mjpeg", "mjpg", "motion-jpeg", "jpeg"),
        mimeTypes = setOf("video/mjpeg", "video/x-motion-jpeg"),
        html5CanPlayProbes = listOf(
            "video/avi; codecs=\"mjpg\"",
            "video/quicktime; codecs=\"mjpa\""
        )
    ),
    PRORES(
        wireName = "prores",
        aliases = setOf("prores", "apcn", "apch", "apcs", "apco", "ap4h", "ap4x"),
        mimeTypes = setOf("video/prores", "video/x-apple-prores"),
        html5CanPlayProbes = listOf(
            "video/quicktime; codecs=\"apcn\"",
            "video/mp4; codecs=\"apcn\""
        )
    ),
    DNXHD(
        wireName = "dnxhd",
        aliases = setOf("dnxhd", "dnxhr", "avdn"),
        mimeTypes = setOf("video/dnxhd", "video/x-dnxhd"),
        html5CanPlayProbes = listOf(
            "video/quicktime; codecs=\"AVdn\""
        )
    ),
    DIRAC(
        wireName = "dirac",
        aliases = setOf("dirac", "vc2", "drac"),
        mimeTypes = setOf("video/x-dirac", "video/dirac"),
        html5CanPlayProbes = listOf(
            "video/ogg; codecs=\"dirac\"",
            "video/mp4; codecs=\"drac\""
        )
    ),
    CINEPAK(
        wireName = "cinepak",
        aliases = setOf("cinepak", "cvid"),
        mimeTypes = setOf("video/cinepak", "video/x-cinepak"),
        html5CanPlayProbes = listOf(
            "video/quicktime; codecs=\"cvid\""
        )
    ),
    RV10(
        wireName = "rv10",
        aliases = setOf("rv10", "realvideo1"),
        mimeTypes = setOf("video/x-pn-realvideo", "video/rv10"),
        html5CanPlayProbes = listOf(
            "application/vnd.rn-realmedia; codecs=\"rv10\""
        )
    ),
    RV20(
        wireName = "rv20",
        aliases = setOf("rv20", "realvideo2"),
        mimeTypes = setOf("video/x-pn-realvideo", "video/rv20"),
        html5CanPlayProbes = listOf(
            "application/vnd.rn-realmedia; codecs=\"rv20\""
        )
    ),
    RV30(
        wireName = "rv30",
        aliases = setOf("rv30", "realvideo8"),
        mimeTypes = setOf("video/x-pn-realvideo", "video/rv30"),
        html5CanPlayProbes = listOf(
            "application/vnd.rn-realmedia; codecs=\"rv30\""
        )
    ),
    RV40(
        wireName = "rv40",
        aliases = setOf("rv40", "realvideo9", "realvideo10"),
        mimeTypes = setOf("video/x-pn-realvideo", "video/rv40"),
        html5CanPlayProbes = listOf(
            "application/vnd.rn-realmedia; codecs=\"rv40\""
        )
    ),
    SVQ1(
        wireName = "svq1",
        aliases = setOf("svq1", "sorenson1"),
        mimeTypes = setOf("video/x-svq", "video/svq1"),
        html5CanPlayProbes = listOf(
            "video/quicktime; codecs=\"SVQ1\""
        )
    ),
    SVQ3(
        wireName = "svq3",
        aliases = setOf("svq3", "sorenson3"),
        mimeTypes = setOf("video/x-svq", "video/svq3"),
        html5CanPlayProbes = listOf(
            "video/quicktime; codecs=\"SVQ3\""
        )
    ),
    FLV1(
        wireName = "flv1",
        aliases = setOf("flv1", "flv", "sorenson-spark"),
        mimeTypes = setOf("video/x-flv", "video/flv1"),
        html5CanPlayProbes = listOf(
            "video/x-flv; codecs=\"flv1\""
        )
    ),
    VP6(
        wireName = "vp6",
        aliases = setOf("vp6", "vp6f", "vp6a", "vp60", "vp61", "vp62"),
        mimeTypes = setOf("video/x-vp6", "video/vp6"),
        html5CanPlayProbes = listOf(
            "video/x-flv; codecs=\"vp6\""
        )
    ),
    INDEO(
        wireName = "indeo",
        aliases = setOf("indeo", "indeo2", "indeo3", "indeo4", "indeo5", "iv31", "iv32", "iv41", "iv50"),
        mimeTypes = setOf("video/x-indeo", "video/indeo"),
        html5CanPlayProbes = listOf(
            "video/avi; codecs=\"IV50\""
        )
    ),
    DVVIDEO(
        wireName = "dvvideo",
        aliases = setOf("dvvideo", "dv", "dvcpro", "dvcp", "dvsd"),
        mimeTypes = setOf("video/dv", "video/x-dv"),
        html5CanPlayProbes = listOf(
            "video/quicktime; codecs=\"dvc \""
        )
    ),
    RAWVIDEO(
        wireName = "rawvideo",
        aliases = setOf("rawvideo", "raw", "uyvy", "yuy2", "i420", "yv12", "rgb24", "rgba"),
        mimeTypes = setOf("video/raw", "video/x-raw"),
        html5CanPlayProbes = listOf(
            "video/avi; codecs=\"raw \""
        )
    ),
    HUFFYUV(
        wireName = "huffyuv",
        aliases = setOf("huffyuv", "ffvh", "hfyu"),
        mimeTypes = setOf("video/x-huffyuv", "video/huffyuv"),
        html5CanPlayProbes = listOf(
            "video/avi; codecs=\"HFYU\""
        )
    ),
    FFV1(
        wireName = "ffv1",
        aliases = setOf("ffv1", "ffv"),
        mimeTypes = setOf("video/x-ffv", "video/ffv1"),
        html5CanPlayProbes = listOf(
            "video/x-matroska; codecs=\"FFV1\""
        )
    ),
    UTVIDEO(
        wireName = "utvideo",
        aliases = setOf("utvideo", "ulra", "ulrg", "uly0", "uly2"),
        mimeTypes = setOf("video/x-utvideo", "video/utvideo"),
        html5CanPlayProbes = listOf(
            "video/avi; codecs=\"ULY0\""
        )
    ),
    CLLC(
        wireName = "cllc",
        aliases = setOf("cllc", "canopus-lossless"),
        mimeTypes = setOf("video/x-cllc", "video/cllc"),
        html5CanPlayProbes = listOf(
            "video/avi; codecs=\"CLLC\""
        )
    ),
    LAGARITH(
        wireName = "lagarith",
        aliases = setOf("lagarith", "lags"),
        mimeTypes = setOf("video/x-lagarith", "video/lagarith"),
        html5CanPlayProbes = listOf(
            "video/avi; codecs=\"LAGS\""
        )
    ),
    BINKVIDEO(
        wireName = "binkvideo",
        aliases = setOf("binkvideo", "bink", "bikb", "biki"),
        mimeTypes = setOf("video/x-bink", "video/binkvideo"),
        html5CanPlayProbes = listOf(
            "video/vnd.radgamettools.bink; codecs=\"bink\""
        )
    ),
    SMACKER(
        wireName = "smackvideo",
        aliases = setOf("smackvideo", "smacker", "smk2", "smk4"),
        mimeTypes = setOf("video/x-smacker", "video/smackvideo"),
        html5CanPlayProbes = listOf(
            "video/vnd.radgamettools.smacker; codecs=\"smk4\""
        )
    ),
    CAVS(
        wireName = "cavs",
        aliases = setOf("cavs", "avs", "avs1"),
        mimeTypes = setOf("video/x-cavs", "video/cavs"),
        html5CanPlayProbes = listOf(
            "video/mp2t; codecs=\"cavs\""
        )
    ),
    AVS2(
        wireName = "avs2",
        aliases = setOf("avs2", "cavs2"),
        mimeTypes = setOf("video/avs2", "video/x-avs2"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"avs2\""
        )
    ),
    AVS3(
        wireName = "avs3",
        aliases = setOf("avs3", "cavs3"),
        mimeTypes = setOf("video/avs3", "video/x-avs3"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"avs3\""
        )
    );

    companion object {
        fun fromWireName(name: String): VideoCodec? {
            val normalized = name.trim().lowercase()
            if (normalized.isEmpty()) return null
            entries.firstOrNull { it.wireName == normalized || normalized in it.aliases }?.let { return it }
            return when {
                normalized.startsWith("avc1") || normalized.startsWith("h264") -> H264
                normalized.startsWith("hev1") || normalized.startsWith("hvc1") || normalized.startsWith("h265") -> HEVC
                normalized.startsWith("vp08") || normalized.startsWith("vp8") -> VP8
                normalized.startsWith("vp09") || normalized.startsWith("vp9") -> VP9
                normalized.startsWith("av01") || normalized.startsWith("av1") -> AV1
                normalized.startsWith("vvc1") || normalized.startsWith("vvi1") || normalized.startsWith("h266") -> VVC
                normalized.startsWith("mp4v") -> MPEG4
                else -> null
            }
        }
    }
}
