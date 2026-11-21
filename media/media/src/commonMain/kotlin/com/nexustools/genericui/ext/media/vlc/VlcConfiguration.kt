package com.nexustools.genericui.ext.media.vlc

import com.nexustools.genericui.ext.schema.SchemaData
import com.nexustools.genericui.ext.schema.SchemaField
import com.nexustools.genericui.ext.schema.SchemaWidgetHint

/**
 * Runtime VLC / LibVLC playback configuration shared across both desktop (`libvlc`) and Swing (`vlcj`)
 * video & audio backends, annotated with `@SchemaData` for `:extensions:data:schema` interop.
 */
@SchemaData(
    id = "vlc_configuration",
    title = "VLC Player Settings",
    description = "Runtime audio, video renderer, hardware decoding, deinterlacing, and caching configuration for LibVLC backends."
)
data class VlcConfiguration(
    @SchemaField(
        label = "Audio Output Module",
        description = "VLC audio output module (aout) or blank for automatic selection."
    )
    val audioOutputModule: String = "",
    @SchemaField(
        label = "Audio Output Device",
        description = "Target audio output device identifier or blank for system default."
    )
    val audioOutputDeviceId: String = "",
    @SchemaField(
        label = "Video Renderer",
        description = "LibVLC video output renderer (--vout)."
    )
    val videoRenderer: VlcVideoRendererMode = VlcVideoRendererMode.AUTO,
    @SchemaField(
        label = "Hardware Decoding",
        description = "LibVLC --avcodec-hw hardware acceleration mode."
    )
    val hardwareDecode: VlcHardwareDecodeMode = VlcHardwareDecodeMode.ANY,
    @SchemaField(
        label = "Deinterlace Mode",
        description = "LibVLC video deinterlacing filter mode."
    )
    val deinterlaceMode: VlcDeinterlaceMode = VlcDeinterlaceMode.OFF,
    @SchemaField(
        label = "S/PDIF Passthrough",
        description = "Pass compressed AC3/DTS bitstreams directly to digital outputs.",
        widgetHint = SchemaWidgetHint.SWITCH
    )
    val spdifPassthrough: Boolean = false,
    @SchemaField(
        label = "Network Caching",
        description = "Read-ahead buffer size for network media streams.",
        unit = "ms",
        min = 0.0,
        max = 60000.0,
        step = 50.0
    )
    val networkCachingMs: Int = 1000,
    @SchemaField(
        label = "File Caching",
        description = "Read-ahead buffer size for local media files.",
        unit = "ms",
        min = 0.0,
        max = 60000.0,
        step = 50.0
    )
    val fileCachingMs: Int = 300
)
