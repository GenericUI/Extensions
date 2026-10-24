package com.nexustools.genericui.enums.media

/**
 * Standardized audio codecs reported by [com.nexustools.genericui.component.media.VideoPlayer] and [com.nexustools.genericui.component.media.AudioPlayer].
 */
enum class AudioCodec(val wireName: String, val standardMimeCodecs: List<String>) {
    AAC("aac", listOf("mp4a.40.2", "mp4a.40.5")),
    MP3("mp3", listOf("mp3", "mp4a.69", "mp4a.6B")),
    OPUS("opus", listOf("opus")),
    VORBIS("vorbis", listOf("vorbis")),
    FLAC("flac", listOf("flac")),
    AC3("ac3", listOf("ac-3", "ac3")),
    EAC3("eac3", listOf("ec-3", "eac3")),
    DTS("dts", listOf("dts", "dtsc")),
    PCM("pcm", listOf("pcm", "1")),
    ALAC("alac", listOf("alac"));

    companion object {
        fun fromWireName(name: String): AudioCodec? =
            entries.firstOrNull { it.wireName.equals(name.trim(), ignoreCase = true) }
    }
}
