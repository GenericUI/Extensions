package com.nexustools.genericui.ext.media.vlc

/**
 * Represents a VLC audio output module (`aout`), such as `mmdevice`, `wasapi`, `directsound`,
 * `auhal`, `pulse`, or `alsa`.
 */
data class VlcAudioOutputModule(
    val name: String,
    val description: String
)
