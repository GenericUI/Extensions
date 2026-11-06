package com.nexustools.genericui.enums.media

/**
 * Comprehensive enum of audio codecs supported by VLC (`libvlc` / FFmpeg `libavcodec`),
 * with canonical wire names, aliases, OS decoder MIME types, and HTML5 `<video>.canPlayType(...)`
 * / `<audio>.canPlayType(...)` probe strings for real runtime verification across all media backends.
 */
enum class AudioCodec(
    val wireName: String,
    val aliases: Set<String>,
    val mimeTypes: Set<String>,
    val html5CanPlayProbes: List<String>
) {
    AAC(
        wireName = "aac",
        aliases = setOf("aac", "mp4a", "mp4a.40.2", "mp4a.40.5", "mp4a.40.29", "aac_latm", "he-aac"),
        mimeTypes = setOf("audio/mp4a-latm", "audio/aac", "audio/mp4"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"mp4a.40.2\"",
            "video/mp4; codecs=\"mp4a.40.5\"",
            "audio/mp4; codecs=\"mp4a.40.2\"",
            "audio/aac"
        )
    ),
    MP3(
        wireName = "mp3",
        aliases = setOf("mp3", "mp3float", "mpeg-layer-3", "mp4a.69", "mp4a.6b"),
        mimeTypes = setOf("audio/mpeg", "audio/mp3"),
        html5CanPlayProbes = listOf(
            "audio/mpeg; codecs=\"mp3\"",
            "audio/mpeg",
            "video/mp4; codecs=\"mp4a.69\"",
            "video/mp4; codecs=\"mp4a.6B\""
        )
    ),
    OPUS(
        wireName = "opus",
        aliases = setOf("opus", "libopus"),
        mimeTypes = setOf("audio/opus", "audio/ogg; codecs=opus"),
        html5CanPlayProbes = listOf(
            "video/webm; codecs=\"opus\"",
            "audio/webm; codecs=\"opus\"",
            "audio/ogg; codecs=\"opus\"",
            "video/mp4; codecs=\"opus\"",
            "audio/mp4; codecs=\"opus\""
        )
    ),
    VORBIS(
        wireName = "vorbis",
        aliases = setOf("vorbis", "libvorbis", "ogg-vorbis"),
        mimeTypes = setOf("audio/vorbis", "audio/ogg"),
        html5CanPlayProbes = listOf(
            "video/webm; codecs=\"vorbis\"",
            "audio/webm; codecs=\"vorbis\"",
            "audio/ogg; codecs=\"vorbis\"",
            "video/ogg; codecs=\"vorbis\""
        )
    ),
    FLAC(
        wireName = "flac",
        aliases = setOf("flac", "fLaC"),
        mimeTypes = setOf("audio/flac", "audio/x-flac"),
        html5CanPlayProbes = listOf(
            "audio/flac",
            "audio/ogg; codecs=\"flac\"",
            "video/mp4; codecs=\"flac\"",
            "audio/mp4; codecs=\"flac\""
        )
    ),
    AC3(
        wireName = "ac3",
        aliases = setOf("ac3", "ac-3", "a52", "dolby-digital"),
        mimeTypes = setOf("audio/ac3", "audio/x-ac3", "audio/vnd.dolby.dd"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"ac-3\"",
            "audio/mp4; codecs=\"ac-3\"",
            "audio/ac3"
        )
    ),
    EAC3(
        wireName = "eac3",
        aliases = setOf("eac3", "ec-3", "e-ac-3", "e-ac3", "dolby-digital-plus", "dd+"),
        mimeTypes = setOf("audio/eac3", "audio/x-eac3", "audio/eac3-joc"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"ec-3\"",
            "audio/mp4; codecs=\"ec-3\"",
            "audio/eac3"
        )
    ),
    TRUEHD(
        wireName = "truehd",
        aliases = setOf("truehd", "mlp-fba", "dolby-truehd"),
        mimeTypes = setOf("audio/true-hd", "audio/vnd.dolby.mlp"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"mlpa\"",
            "audio/mp4; codecs=\"mlpa\"",
            "audio/true-hd"
        )
    ),
    MLP(
        wireName = "mlp",
        aliases = setOf("mlp", "meridian-lossless"),
        mimeTypes = setOf("audio/vnd.dolby.mlp", "audio/mlp"),
        html5CanPlayProbes = listOf(
            "audio/vnd.dolby.mlp"
        )
    ),
    DTS(
        wireName = "dts",
        aliases = setOf("dts", "dca", "dtsc"),
        mimeTypes = setOf("audio/vnd.dts", "audio/dts"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"dtsc\"",
            "video/mp4; codecs=\"dts\"",
            "audio/vnd.dts"
        )
    ),
    DTS_HD(
        wireName = "dtshd",
        aliases = setOf("dtshd", "dts-hd", "dtsh", "dtse", "dtsl", "dtsx", "dts-ma"),
        mimeTypes = setOf("audio/vnd.dts.hd", "audio/vnd.dts.uhd"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"dtsh\"",
            "video/mp4; codecs=\"dtsl\"",
            "video/mp4; codecs=\"dtse\"",
            "audio/vnd.dts.hd"
        )
    ),
    PCM(
        wireName = "pcm",
        aliases = setOf(
            "pcm", "wav", "lpcm",
            "pcm_s16le", "pcm_s16be", "pcm_s24le", "pcm_s24be", "pcm_s32le", "pcm_s32be",
            "pcm_f32le", "pcm_f32be", "pcm_f64le", "pcm_u8", "pcm_alaw", "pcm_mulaw"
        ),
        mimeTypes = setOf("audio/raw", "audio/wav", "audio/x-wav", "audio/L16", "audio/g711-alaw", "audio/g711-mlaw"),
        html5CanPlayProbes = listOf(
            "audio/wav; codecs=\"1\"",
            "audio/wav",
            "audio/x-wav"
        )
    ),
    ALAC(
        wireName = "alac",
        aliases = setOf("alac", "apple-lossless"),
        mimeTypes = setOf("audio/alac", "audio/x-alac"),
        html5CanPlayProbes = listOf(
            "audio/mp4; codecs=\"alac\"",
            "video/mp4; codecs=\"alac\"",
            "audio/x-m4a; codecs=\"alac\""
        )
    ),
    WMAV1(
        wireName = "wmav1",
        aliases = setOf("wmav1", "wma1"),
        mimeTypes = setOf("audio/x-ms-wma", "audio/x-wma1"),
        html5CanPlayProbes = listOf(
            "audio/x-ms-wma; codecs=\"wmav1\""
        )
    ),
    WMAV2(
        wireName = "wmav2",
        aliases = setOf("wmav2", "wma2", "wma"),
        mimeTypes = setOf("audio/x-ms-wma", "audio/wma"),
        html5CanPlayProbes = listOf(
            "audio/x-ms-wma; codecs=\"wmav2\"",
            "audio/x-ms-wma"
        )
    ),
    WMAPRO(
        wireName = "wmapro",
        aliases = setOf("wmapro", "wma-pro", "wma9pro"),
        mimeTypes = setOf("audio/x-ms-wma-pro", "audio/x-ms-wma"),
        html5CanPlayProbes = listOf(
            "audio/x-ms-wma; codecs=\"wmapro\""
        )
    ),
    WMALOSSLESS(
        wireName = "wmalossless",
        aliases = setOf("wmalossless", "wma-lossless", "wmal"),
        mimeTypes = setOf("audio/x-ms-wma-lossless"),
        html5CanPlayProbes = listOf(
            "audio/x-ms-wma; codecs=\"wmalossless\""
        )
    ),
    AMR_NB(
        wireName = "amr_nb",
        aliases = setOf("amr_nb", "amrnb", "amr", "samr"),
        mimeTypes = setOf("audio/3gpp", "audio/amr"),
        html5CanPlayProbes = listOf(
            "audio/3gpp; codecs=\"samr\"",
            "audio/amr"
        )
    ),
    AMR_WB(
        wireName = "amr_wb",
        aliases = setOf("amr_wb", "amrwb", "sawb"),
        mimeTypes = setOf("audio/amr-wb"),
        html5CanPlayProbes = listOf(
            "audio/3gpp; codecs=\"sawb\"",
            "audio/amr-wb"
        )
    ),
    SPEEX(
        wireName = "speex",
        aliases = setOf("speex", "spx"),
        mimeTypes = setOf("audio/speex", "audio/x-speex"),
        html5CanPlayProbes = listOf(
            "audio/ogg; codecs=\"speex\""
        )
    ),
    MP2(
        wireName = "mp2",
        aliases = setOf("mp2", "mp2float", "mpeg-layer-2", "mp4a.69"),
        mimeTypes = setOf("audio/mpeg-L2", "audio/mp2"),
        html5CanPlayProbes = listOf(
            "audio/mpeg; codecs=\"mp2\"",
            "video/mp2t; codecs=\"mp2\""
        )
    ),
    MP1(
        wireName = "mp1",
        aliases = setOf("mp1", "mp1float", "mpeg-layer-1"),
        mimeTypes = setOf("audio/mpeg-L1", "audio/mp1"),
        html5CanPlayProbes = listOf(
            "audio/mpeg; codecs=\"mp1\""
        )
    ),
    MUSEPACK(
        wireName = "musepack",
        aliases = setOf("musepack", "mpc", "mpc7", "mpc8", "musepack7", "musepack8"),
        mimeTypes = setOf("audio/x-musepack", "audio/musepack"),
        html5CanPlayProbes = listOf(
            "audio/x-musepack"
        )
    ),
    APE(
        wireName = "ape",
        aliases = setOf("ape", "monkeys-audio", "mac"),
        mimeTypes = setOf("audio/x-ape", "audio/ape"),
        html5CanPlayProbes = listOf(
            "audio/x-ape"
        )
    ),
    WAVPACK(
        wireName = "wavpack",
        aliases = setOf("wavpack", "wv"),
        mimeTypes = setOf("audio/x-wavpack", "audio/wavpack"),
        html5CanPlayProbes = listOf(
            "audio/x-wavpack"
        )
    ),
    TTA(
        wireName = "tta",
        aliases = setOf("tta", "true-audio"),
        mimeTypes = setOf("audio/x-tta", "audio/tta"),
        html5CanPlayProbes = listOf(
            "audio/x-tta"
        )
    ),
    ATRAC3(
        wireName = "atrac3",
        aliases = setOf("atrac3", "atrac3p", "atrac", "atrc"),
        mimeTypes = setOf("audio/x-atrac3", "audio/atrac3"),
        html5CanPlayProbes = listOf(
            "audio/x-atrac3"
        )
    ),
    COOK(
        wireName = "cook",
        aliases = setOf("cook", "realaudio-cook"),
        mimeTypes = setOf("audio/x-pn-realaudio", "audio/cook"),
        html5CanPlayProbes = listOf(
            "audio/x-pn-realaudio; codecs=\"cook\""
        )
    ),
    REALAUDIO(
        wireName = "realaudio",
        aliases = setOf("realaudio", "ra_144", "ra_288", "ralf", "sipr"),
        mimeTypes = setOf("audio/vnd.rn-realaudio", "audio/x-realaudio"),
        html5CanPlayProbes = listOf(
            "audio/vnd.rn-realaudio"
        )
    ),
    GSM(
        wireName = "gsm",
        aliases = setOf("gsm", "gsm_ms", "agsm"),
        mimeTypes = setOf("audio/gsm", "audio/x-gsm"),
        html5CanPlayProbes = listOf(
            "audio/gsm"
        )
    ),
    ADPCM(
        wireName = "adpcm",
        aliases = setOf("adpcm", "adpcm_ima_wav", "adpcm_ms", "adpcm_swf", "adpcm_yamaha"),
        mimeTypes = setOf("audio/x-adpcm", "audio/adpcm"),
        html5CanPlayProbes = listOf(
            "audio/wav; codecs=\"2\"",
            "audio/wav; codecs=\"17\""
        )
    ),
    AC4(
        wireName = "ac4",
        aliases = setOf("ac4", "ac-4", "dolby-ac4"),
        mimeTypes = setOf("audio/ac4", "audio/vnd.dolby.ac4"),
        html5CanPlayProbes = listOf(
            "video/mp4; codecs=\"ac-4\"",
            "video/mp4; codecs=\"ac-4.02.01.01\"",
            "audio/mp4; codecs=\"ac-4\""
        )
    ),
    DSD(
        wireName = "dsd",
        aliases = setOf("dsd", "dsd_lsbf", "dsd_msbf", "dsf", "dff"),
        mimeTypes = setOf("audio/dsd", "audio/x-dsd"),
        html5CanPlayProbes = listOf(
            "audio/dsd"
        )
    ),
    SBC(
        wireName = "sbc",
        aliases = setOf("sbc", "bluetooth-sbc"),
        mimeTypes = setOf("audio/sbc"),
        html5CanPlayProbes = listOf(
            "audio/sbc"
        )
    );

    companion object {
        fun fromWireName(name: String): AudioCodec? {
            val normalized = name.trim().lowercase()
            if (normalized.isEmpty()) return null
            entries.firstOrNull { it.wireName == normalized || normalized in it.aliases }?.let { return it }
            return when {
                normalized.startsWith("mp4a.40") || normalized.startsWith("aac") -> AAC
                normalized.startsWith("pcm_") -> PCM
                normalized.startsWith("adpcm_") -> ADPCM
                normalized.startsWith("ec-3") || normalized.startsWith("eac3") -> EAC3
                normalized.startsWith("ac-3") || normalized.startsWith("ac3") -> AC3
                normalized.startsWith("ac-4") || normalized.startsWith("ac4") -> AC4
                normalized.startsWith("dts-") || normalized.startsWith("dtsh") || normalized.startsWith("dtsl") -> DTS_HD
                else -> null
            }
        }
    }
}
