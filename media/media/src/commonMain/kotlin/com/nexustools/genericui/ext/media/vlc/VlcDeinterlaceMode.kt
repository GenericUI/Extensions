package com.nexustools.genericui.ext.media.vlc

/**
 * LibVLC video deinterlacing mode passed to `libvlc_video_set_deinterlace` and media options.
 */
enum class VlcDeinterlaceMode(
    val id: String,
    val vlcMode: String?,
    val displayName: String
) {
    OFF("off", null, "Off"),
    AUTO("auto", "auto", "Auto"),
    YADIF("yadif", "yadif", "Yadif"),
    YADIF2X("yadif2x", "yadif2x", "Yadif (2x)"),
    BLEND("blend", "blend", "Blend"),
    BOB("bob", "bob", "Bob"),
    LINEAR("linear", "linear", "Linear"),
    X("x", "x", "X");

    companion object {
        fun fromId(id: String): VlcDeinterlaceMode {
            val trimmed = id.trim()
            return entries.firstOrNull {
                it.id.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true)
            } ?: OFF
        }
    }
}
