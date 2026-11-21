package com.nexustools.genericui.ext.media.vlc

/**
 * Represents an audio output device exposed by a VLC audio output module.
 */
data class VlcAudioOutputDevice(
    val deviceId: String,
    val description: String
)
