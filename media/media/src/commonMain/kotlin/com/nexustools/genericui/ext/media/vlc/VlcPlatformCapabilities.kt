package com.nexustools.genericui.ext.media.vlc

/**
 * Shared OS capability tables and LibVLC CLI/media option builders used by both native desktop
 * (`libvlc`) and JVM Swing (`vlcj`) backends.
 */
internal object VlcPlatformCapabilities {
    private val OVERRIDDEN_ARG_PREFIXES = listOf(
        "--spdif",
        "--no-spdif",
        "--avcodec-hw=",
        "--network-caching=",
        "--file-caching=",
        "--aout=",
        "--vout=",
        "--deinterlace=",
        "--deinterlace-mode="
    )

    private val NON_INTERACTIVE_AOUT_MODULES = setOf(
        "adummy",
        "dummy",
        "afile",
        "file",
        "amem"
    )

    fun supportedHardwareDecodeModes(os: VlcDesktopOsKind): List<VlcHardwareDecodeMode> = when (os) {
        VlcDesktopOsKind.WINDOWS -> listOf(
            VlcHardwareDecodeMode.ANY,
            VlcHardwareDecodeMode.D3D11VA,
            VlcHardwareDecodeMode.DXVA2,
            VlcHardwareDecodeMode.NVDEC,
            VlcHardwareDecodeMode.NONE
        )
        VlcDesktopOsKind.MACOS -> listOf(
            VlcHardwareDecodeMode.ANY,
            VlcHardwareDecodeMode.VIDEOTOOLBOX,
            VlcHardwareDecodeMode.NONE
        )
        VlcDesktopOsKind.LINUX -> listOf(
            VlcHardwareDecodeMode.ANY,
            VlcHardwareDecodeMode.VAAPI,
            VlcHardwareDecodeMode.VDPAU,
            VlcHardwareDecodeMode.NVDEC,
            VlcHardwareDecodeMode.NONE
        )
        VlcDesktopOsKind.OTHER -> listOf(
            VlcHardwareDecodeMode.ANY,
            VlcHardwareDecodeMode.NONE
        )
    }

    fun supportedVideoRenderers(os: VlcDesktopOsKind): List<VlcVideoRendererMode> = when (os) {
        VlcDesktopOsKind.WINDOWS -> listOf(
            VlcVideoRendererMode.AUTO,
            VlcVideoRendererMode.DIRECT3D11,
            VlcVideoRendererMode.OPENGL,
            VlcVideoRendererMode.RV32,
            VlcVideoRendererMode.RV16
        )
        VlcDesktopOsKind.MACOS -> listOf(
            VlcVideoRendererMode.AUTO,
            VlcVideoRendererMode.MACOSX,
            VlcVideoRendererMode.OPENGL,
            VlcVideoRendererMode.RV32,
            VlcVideoRendererMode.RV16
        )
        VlcDesktopOsKind.LINUX -> listOf(
            VlcVideoRendererMode.AUTO,
            VlcVideoRendererMode.OPENGL,
            VlcVideoRendererMode.XCB_X11,
            VlcVideoRendererMode.WL_SHM,
            VlcVideoRendererMode.RV32,
            VlcVideoRendererMode.RV16
        )
        VlcDesktopOsKind.OTHER -> listOf(
            VlcVideoRendererMode.AUTO,
            VlcVideoRendererMode.OPENGL,
            VlcVideoRendererMode.RV32,
            VlcVideoRendererMode.RV16
        )
    }

    fun preferredAudioOutputNames(os: VlcDesktopOsKind): List<String> = when (os) {
        VlcDesktopOsKind.WINDOWS -> listOf("mmdevice", "wasapi", "directsound", "waveout")
        VlcDesktopOsKind.MACOS -> listOf("auhal")
        VlcDesktopOsKind.LINUX -> listOf("pulse", "pipewire", "alsa", "jack", "oss")
        VlcDesktopOsKind.OTHER -> emptyList()
    }

    fun isNonInteractiveAudioOutput(moduleName: String): Boolean =
        moduleName.trim().lowercase() in NON_INTERACTIVE_AOUT_MODULES

    inline fun resolveDefaultAudioOutputDevices(
        os: VlcDesktopOsKind,
        moduleNames: List<String>,
        fetchDevices: (String) -> List<VlcAudioOutputDevice>
    ): List<VlcAudioOutputDevice> {
        val preferredOrder = preferredAudioOutputNames(os)
        val candidates = buildList {
            for (preferred in preferredOrder) {
                val match = moduleNames.firstOrNull { it.equals(preferred, ignoreCase = true) }
                if (match != null) add(match)
            }
            for (name in moduleNames) {
                if (!isNonInteractiveAudioOutput(name) && none { it.equals(name, ignoreCase = true) }) {
                    add(name)
                }
            }
        }
        for (moduleName in candidates) {
            val devices = fetchDevices(moduleName)
            if (devices.isNotEmpty()) {
                return devices
            }
        }
        return emptyList()
    }

    fun isWindowVoutMode(mode: VlcVideoRendererMode): Boolean = when (mode) {
        VlcVideoRendererMode.DIRECT3D11,
        VlcVideoRendererMode.OPENGL,
        VlcVideoRendererMode.MACOSX,
        VlcVideoRendererMode.XCB_X11,
        VlcVideoRendererMode.WL_SHM -> true
        VlcVideoRendererMode.AUTO,
        VlcVideoRendererMode.RV32,
        VlcVideoRendererMode.RV16 -> false
    }

    fun mergeLibVlcArgs(
        baseArgs: List<String>,
        config: VlcConfiguration,
        includeVideoOptions: Boolean,
        allowWindowVoutFlag: Boolean
    ): List<String> {
        val merged = baseArgs.filterNot { arg ->
            OVERRIDDEN_ARG_PREFIXES.any { prefix -> arg == prefix || arg.startsWith(prefix) }
        }.toMutableList()
        merged.add(if (config.spdifPassthrough) "--spdif" else "--no-spdif")
        merged.add("--network-caching=${config.networkCachingMs.coerceAtLeast(0)}")
        merged.add("--file-caching=${config.fileCachingMs.coerceAtLeast(0)}")
        if (config.audioOutputModule.isNotBlank()) {
            merged.add("--aout=${config.audioOutputModule}")
        }
        if (includeVideoOptions) {
            merged.add("--avcodec-hw=${config.hardwareDecode.cliValue}")
            if (allowWindowVoutFlag && isWindowVoutMode(config.videoRenderer)) {
                merged.add("--vout=${config.videoRenderer.id}")
            }
            val deinterlace = config.deinterlaceMode.vlcMode
            if (deinterlace != null) {
                merged.add("--deinterlace=1")
                merged.add("--deinterlace-mode=$deinterlace")
            }
        }
        return merged
    }

    fun buildMediaOptions(
        config: VlcConfiguration,
        seekOffsetSeconds: Double,
        includeVideoOptions: Boolean,
        allowWindowVoutOption: Boolean,
        includeHttpReconnect: Boolean = false
    ): List<String> {
        val options = mutableListOf<String>()
        if (seekOffsetSeconds > 0.0) {
            options.add(":start-time=$seekOffsetSeconds")
        }
        if (includeHttpReconnect) {
            options.add(":http-reconnect")
        }
        options.add(":network-caching=${config.networkCachingMs.coerceAtLeast(0)}")
        options.add(":file-caching=${config.fileCachingMs.coerceAtLeast(0)}")
        options.add(if (config.spdifPassthrough) ":spdif" else ":no-spdif")
        if (config.audioOutputModule.isNotBlank()) {
            options.add(":aout=${config.audioOutputModule}")
        }
        if (includeVideoOptions) {
            options.add(":avcodec-hw=${config.hardwareDecode.cliValue}")
            if (allowWindowVoutOption && isWindowVoutMode(config.videoRenderer)) {
                options.add(":vout=${config.videoRenderer.id}")
            }
            val deinterlace = config.deinterlaceMode.vlcMode
            if (deinterlace != null) {
                options.add(":deinterlace=1")
                options.add(":deinterlace-mode=$deinterlace")
            } else {
                options.add(":deinterlace=0")
            }
        }
        return options
    }
}
