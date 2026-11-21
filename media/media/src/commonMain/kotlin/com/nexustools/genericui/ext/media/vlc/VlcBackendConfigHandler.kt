package com.nexustools.genericui.ext.media.vlc

/**
 * Provider bridge registered by VLC-backed media backends (native desktop `libvlc` and JVM Swing `vlcj`)
 * to expose audio output enumeration, OS capabilities, and live [VlcConfiguration] updates.
 */
interface VlcBackendConfigHandler {
    fun onVlcConfigurationChanged(configuration: VlcConfiguration)
    fun listAudioOutputModules(): List<VlcAudioOutputModule>
    fun listAudioOutputDevices(audioOutputModule: String): List<VlcAudioOutputDevice>
    fun listSupportedHardwareDecodeModes(): List<VlcHardwareDecodeMode>
    fun listSupportedVideoRenderers(): List<VlcVideoRendererMode>
    fun onDetached() {}
}
