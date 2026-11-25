@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.windows.FreeLibrary
import platform.windows.GetEnvironmentVariableA
import platform.windows.GetFileAttributesA
import platform.windows.GetModuleFileNameA
import platform.windows.GetProcAddress
import platform.windows.HMODULE
import platform.windows.INVALID_FILE_ATTRIBUTES
import platform.windows.LoadLibraryA
import platform.windows.SetDllDirectoryA
import platform.windows.SetEnvironmentVariableA

/**
 * Win32 (`LoadLibraryA` / `GetProcAddress`) loader for `libvlc.dll`.
 *
 * Prioritizes the packaged `libvlc.dll`, `libvlccore.dll`, and `plugins/` located alongside the
 * application executable (`<exeDir>`) before checking `GENERICUI_VLC_DIR`, `VLC_PATH`, or system VLC installations.
 */
actual object LibVlcDynamicLoader {

    private fun queryExecutableDir(): String? = memScoped {
        val buf = allocArray<ByteVar>(1024)
        val len = GetModuleFileNameA(null, buf, 1024u)
        if (len == 0u || len >= 1024u) return null
        val full = buf.toKString()
        val lastSep = full.lastIndexOf('\\')
        if (lastSep > 0) full.substring(0, lastSep) else null
    }

    private fun queryEnv(name: String): String? = memScoped {
        val buf = allocArray<ByteVar>(1024)
        val len = GetEnvironmentVariableA(name, buf, 1024u)
        if (len == 0u || len >= 1024u) null else buf.toKString()
    }

    private fun pathExists(path: String): Boolean =
        GetFileAttributesA(path) != INVALID_FILE_ATTRIBUTES

    private fun buildCandidatePaths(): List<String> {
        val paths = mutableListOf<String>()
        val exeDir = queryExecutableDir()
        if (!exeDir.isNullOrBlank()) {
            paths.add("$exeDir\\libvlc.dll")
            paths.add("$exeDir\\bin\\libvlc.dll")
        }
        val envVlcDir = queryEnv("GENERICUI_VLC_DIR")?.trim()?.trimEnd('\\', '/')
        if (!envVlcDir.isNullOrBlank()) {
            paths.add("$envVlcDir\\libvlc.dll")
        }
        val vlcPathEnv = queryEnv("VLC_PATH")?.trim()?.trimEnd('\\', '/')
        if (!vlcPathEnv.isNullOrBlank()) {
            paths.add("$vlcPathEnv\\libvlc.dll")
        }
        paths.add("C:\\Program Files\\VideoLAN\\VLC\\libvlc.dll")
        paths.add("C:\\Program Files (x86)\\VideoLAN\\VLC\\libvlc.dll")
        paths.add("libvlc.dll")
        return paths.distinct()
    }

    private fun findOpusFixPluginsDir(): String? {
        val exeDir = queryExecutableDir()
        val envOpusFixDir = queryEnv("GENERICUI_VLC_OPUS_FIX_DIR")?.trim()?.trimEnd('\\', '/')
        val existingPluginPath = queryEnv("VLC_PLUGIN_PATH").orEmpty()
        val candidates = buildList {
            if (!envOpusFixDir.isNullOrBlank()) {
                add(envOpusFixDir)
            }
            if (!exeDir.isNullOrBlank()) {
                add("$exeDir\\opus_fix_plugins")
                add("$exeDir\\bin\\opus_fix_plugins")
                add("$exeDir\\..\\..\\..\\generated\\genericui\\nativeBundle\\vlc\\windows\\opus_fix_plugins")
            }
            for (entry in existingPluginPath.split(';')) {
                val trimmed = entry.trim().trimEnd('\\', '/')
                if (trimmed.isNotEmpty()) {
                    add(trimmed)
                }
            }
        }.distinct()
        return candidates.firstOrNull { dir ->
            pathExists("$dir\\libgenericui_opus_fix_plugin.dll")
        }
    }

    private fun setVlcPluginPathEnv(value: String) {
        SetEnvironmentVariableA("VLC_PLUGIN_PATH", value)
        platform.posix._putenv("VLC_PLUGIN_PATH=$value")
    }

    actual fun openLibVlc(): COpaquePointer? {
        val opusFixDir = findOpusFixPluginsDir()
        for (path in buildCandidatePaths()) {
            val dir = path.substringBeforeLast('\\', "")
            if (dir.isNotEmpty()) {
                if (!pathExists(path)) continue
                SetDllDirectoryA(dir)
                val pluginsDir = "$dir\\plugins".takeIf { pathExists(it) }
                val hasOpusFixInPlugins = pluginsDir != null && (
                    pathExists("$pluginsDir\\libgenericui_opus_fix_plugin.dll") ||
                        pathExists("$pluginsDir\\codec\\libgenericui_opus_fix_plugin.dll")
                    )
                val pluginPathEntries = buildList {
                    if (opusFixDir != null && !hasOpusFixInPlugins && !opusFixDir.equals(pluginsDir, ignoreCase = true)) {
                        add(opusFixDir)
                    }
                    if (pluginsDir != null) {
                        add(pluginsDir)
                    }
                }
                if (pluginPathEntries.isNotEmpty()) {
                    setVlcPluginPathEnv(pluginPathEntries.joinToString(";"))
                }
                val corePath = "$dir\\libvlccore.dll"
                if (pathExists(corePath)) {
                    LoadLibraryA(corePath)
                }
            } else if (opusFixDir != null) {
                setVlcPluginPathEnv(opusFixDir)
            }
            val handle = LoadLibraryA(path)
            if (handle != null) {
                return handle.reinterpret()
            }
        }
        return null
    }

    actual fun findSymbol(libraryHandle: COpaquePointer, name: String): COpaquePointer? {
        val hModule: HMODULE = libraryHandle.reinterpret()
        return GetProcAddress(hModule, name)?.reinterpret()
    }

    actual fun closeLibVlc(libraryHandle: COpaquePointer) {
        val hModule: HMODULE = libraryHandle.reinterpret()
        FreeLibrary(hModule)
    }
}
