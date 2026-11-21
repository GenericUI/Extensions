package com.nexustools.genericui.ext.media.vlc

/**
 * LibVLC video output (`--vout` / callback chroma) renderer selection.
 */
enum class VlcVideoRendererMode(
    val id: String,
    val displayName: String
) {
    AUTO("auto", "Automatic"),
    RV32("RV32", "Memory RGBA / Software"),
    RV16("RV16", "Memory RGB16 / Low Bandwidth"),
    DIRECT3D11("direct3d11", "Direct3D 11 (Windows)"),
    OPENGL("gl", "OpenGL"),
    MACOSX("macosx", "macOS CoreVideo"),
    XCB_X11("xcb_x11", "X11 (XCB)"),
    WL_SHM("wl_shm", "Wayland Shared Memory");

    companion object {
        fun fromId(id: String): VlcVideoRendererMode {
            val trimmed = id.trim()
            return entries.firstOrNull {
                it.id.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true)
            } ?: AUTO
        }
    }
}
