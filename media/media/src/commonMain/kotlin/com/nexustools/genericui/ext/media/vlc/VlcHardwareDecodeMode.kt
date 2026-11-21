package com.nexustools.genericui.ext.media.vlc

/**
 * LibVLC `--avcodec-hw` hardware decoding backend mode.
 */
enum class VlcHardwareDecodeMode(
    val cliValue: String,
    val displayName: String
) {
    ANY("any", "Automatic"),
    D3D11VA("d3d11va", "Direct3D 11 (Windows)"),
    DXVA2("dxva2", "DXVA 2.0 (Windows)"),
    NVDEC("nvdec", "NVIDIA NVDEC"),
    VIDEOTOOLBOX("videotoolbox", "Apple VideoToolbox"),
    VAAPI("vaapi", "VA-API (Linux)"),
    VDPAU("vdpau", "VDPAU (Linux)"),
    NONE("none", "Software (Disabled)");

    companion object {
        fun fromCliValue(value: String): VlcHardwareDecodeMode {
            val trimmed = value.trim()
            return entries.firstOrNull {
                it.cliValue.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true)
            } ?: ANY
        }
    }
}
