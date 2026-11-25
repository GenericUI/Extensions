@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.posix.F_OK
import platform.posix.RTLD_GLOBAL
import platform.posix.RTLD_LAZY
import platform.posix.access
import platform.posix.closedir
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.setenv

/**
 * macOS (`dlopen` / `dlsym`) loader for `libvlc.5.dylib` / `libvlc.dylib`.
 *
 * Prioritizes packaged libraries in `<AppBundle>/Contents/Frameworks/` and `<exeDir>/` before
 * checking `GENERICUI_VLC_DIR`, `/Applications/VLC.app`, or Homebrew `/opt/homebrew/lib`.
 */
actual object LibVlcDynamicLoader {

    private fun queryExecutableDir(): String? {
        val full = NSBundle.mainBundle.executablePath ?: return null
        val lastSep = full.lastIndexOf('/')
        return if (lastSep > 0) full.substring(0, lastSep) else null
    }

    private fun buildCandidatePaths(): List<String> {
        val paths = mutableListOf<String>()
        val exeDir = queryExecutableDir()
        if (!exeDir.isNullOrBlank()) {
            paths.add("$exeDir/../Frameworks/libvlc.dylib")
            paths.add("$exeDir/../Frameworks/libvlc.5.dylib")
            paths.add("$exeDir/libvlc.dylib")
            paths.add("$exeDir/libvlc.5.dylib")
        }
        val envVlcDir = getenv("GENERICUI_VLC_DIR")?.toKString()?.trim()?.trimEnd('/')
        if (!envVlcDir.isNullOrBlank()) {
            paths.add("$envVlcDir/libvlc.dylib")
            paths.add("$envVlcDir/libvlc.5.dylib")
        }
        paths.add("/Applications/VLC.app/Contents/MacOS/lib/libvlc.dylib")
        paths.add("/Applications/VLC.app/Contents/MacOS/lib/libvlc.5.dylib")
        paths.add("/opt/homebrew/lib/libvlc.dylib")
        paths.add("/opt/homebrew/lib/libvlc.5.dylib")
        paths.add("/usr/local/lib/libvlc.dylib")
        paths.add("/usr/local/lib/libvlc.5.dylib")
        paths.add("libvlc.dylib")
        paths.add("libvlc.5.dylib")
        return paths.distinct()
    }

    private fun hasAnyDylibPlugin(dirPath: String): Boolean {
        val dirPtr = opendir(dirPath) ?: return false
        try {
            while (true) {
                val entry = readdir(dirPtr) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name.endsWith(".dylib", ignoreCase = true)) {
                    return true
                }
            }
        } finally {
            closedir(dirPtr)
        }
        return false
    }

    private fun findOpusFixPluginsDir(): String? {
        val exeDir = queryExecutableDir()
        val envOpusFixDir = getenv("GENERICUI_VLC_OPUS_FIX_DIR")?.toKString()?.trim()?.trimEnd('/')
        val existingPluginPath = getenv("VLC_PLUGIN_PATH")?.toKString().orEmpty()
        val candidates = buildList {
            if (!envOpusFixDir.isNullOrBlank()) {
                add(envOpusFixDir)
            }
            if (!exeDir.isNullOrBlank()) {
                add("$exeDir/opus_fix_plugins")
                add("$exeDir/../Frameworks/opus_fix_plugins")
                add("$exeDir/../../../generated/genericui/nativeBundle/vlc/macos/opus_fix_plugins")
            }
            for (entry in existingPluginPath.split(':')) {
                val trimmed = entry.trim().trimEnd('/')
                if (trimmed.isNotEmpty()) {
                    add(trimmed)
                }
            }
        }.distinct()
        return candidates.firstOrNull { dir ->
            access("$dir/libgenericui_opus_fix_plugin.dylib", F_OK) == 0
        }
    }

    actual fun openLibVlc(): COpaquePointer? {
        val flags = RTLD_LAZY or RTLD_GLOBAL
        val opusFixDir = findOpusFixPluginsDir()
        for (path in buildCandidatePaths()) {
            val dir = path.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) {
                val candidatePluginDirs = listOf(
                    "$dir/plugins",
                    "$dir/../plugins",
                    "/Applications/VLC.app/Contents/MacOS/plugins",
                    "/opt/homebrew/lib/vlc/plugins"
                )
                val validPluginsDir = candidatePluginDirs.firstOrNull { access(it, F_OK) == 0 && hasAnyDylibPlugin(it) }
                    ?: candidatePluginDirs.firstOrNull { access(it, F_OK) == 0 }
                val hasOpusFixInPluginsDir = validPluginsDir != null &&
                    access("$validPluginsDir/libgenericui_opus_fix_plugin.dylib", F_OK) == 0
                val pluginPathEntries = buildList {
                    if (opusFixDir != null && !hasOpusFixInPluginsDir && opusFixDir != validPluginsDir) {
                        add(opusFixDir)
                    }
                    if (validPluginsDir != null) {
                        add(validPluginsDir)
                    }
                }
                if (pluginPathEntries.isNotEmpty()) {
                    setenv("VLC_PLUGIN_PATH", pluginPathEntries.joinToString(":"), 1)
                }
                for (coreName in listOf("libvlccore.dylib", "libvlccore.9.dylib")) {
                    val corePath = "$dir/$coreName"
                    if (access(corePath, F_OK) == 0) {
                        if (dlopen(corePath, flags) != null) {
                            break
                        }
                    }
                }
            } else if (opusFixDir != null) {
                val existing = getenv("VLC_PLUGIN_PATH")?.toKString()?.trim().orEmpty()
                if (opusFixDir !in existing.split(':')) {
                    val updated = if (existing.isEmpty()) opusFixDir else "$opusFixDir:$existing"
                    setenv("VLC_PLUGIN_PATH", updated, 1)
                }
            }
            val handle = dlopen(path, flags)
            if (handle != null) return handle
        }
        return null
    }

    actual fun findSymbol(libraryHandle: COpaquePointer, name: String): COpaquePointer? =
        dlsym(libraryHandle, name)

    actual fun closeLibVlc(libraryHandle: COpaquePointer) {
        dlclose(libraryHandle)
    }
}
