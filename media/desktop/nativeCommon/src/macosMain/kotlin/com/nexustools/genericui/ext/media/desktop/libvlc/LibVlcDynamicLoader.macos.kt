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

    actual fun openLibVlc(): COpaquePointer? {
        val flags = RTLD_LAZY or RTLD_GLOBAL
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
                if (validPluginsDir != null) {
                    setenv("VLC_PLUGIN_PATH", validPluginsDir, 1)
                }
                for (coreName in listOf("libvlccore.dylib", "libvlccore.9.dylib")) {
                    val corePath = "$dir/$coreName"
                    if (access(corePath, F_OK) == 0) {
                        if (dlopen(corePath, flags) != null) {
                            break
                        }
                    }
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
