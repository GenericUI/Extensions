package com.nexustools.genericui.enums.media

/**
 * Standardized media container formats reported by [com.nexustools.genericui.component.media.VideoPlayer.getMediaCapabilities].
 */
enum class MediaContainer(val wireName: String, val mimeTypes: List<String>, val extensions: List<String>) {
    MP4("mp4", listOf("video/mp4", "audio/mp4"), listOf("mp4", "m4v", "m4a")),
    MOV("mov", listOf("video/quicktime"), listOf("mov", "qt")),
    WEBM("webm", listOf("video/webm", "audio/webm"), listOf("webm")),
    MKV("mkv", listOf("video/x-matroska", "audio/x-matroska"), listOf("mkv", "mka")),
    AVI("avi", listOf("video/x-msvideo", "video/avi"), listOf("avi")),
    MPEG_TS("mpegts", listOf("video/mp2t"), listOf("ts", "m2ts", "mts")),
    OGG("ogg", listOf("video/ogg", "audio/ogg", "application/ogg"), listOf("ogg", "ogv", "oga")),
    FLV("flv", listOf("video/x-flv"), listOf("flv")),
    HLS("hls", listOf("application/vnd.apple.mpegurl", "application/x-mpegURL"), listOf("m3u8")),
    DASH("dash", listOf("application/dash+xml"), listOf("mpd"));

    companion object {
        fun fromWireNameOrExtension(value: String): MediaContainer? {
            val clean = value.trim().trimStart('.').lowercase()
            if (clean == "matroska") return MKV
            return entries.firstOrNull { it.wireName == clean || clean in it.extensions }
        }
    }
}
