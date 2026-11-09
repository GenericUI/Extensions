@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin._NSGetExecutablePath
import platform.posix.F_OK
import platform.posix.RTLD_LAZY
import platform.posix.access
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.getenv
import platform.posix.setenv

/**
 * macOS (`dlopen` / `dlsym`) loader for `libvlc.5.dylib` / `libvlc.dylib`.
 *
 * Prioritizes packaged libraries in `<AppBundle>/Contents/Frameworks/` and `<exeDir>/` before
 * checking `GENERICUI_VLC_DIR`, `/Applications/VLC.app`, or Homebrew `/opt/homebrew/lib`.
 */
actual object LibVlcDynamicLoader {

    private fun queryExecutableDir(): String? = memScoped {
        val sizeVar = alloc<UIntVar>()
        sizeVar.value = 1024u
        val buf = allocArray<ByteVar>(1024)
        if (_NSGetExecutablePath(buf, sizeVar.ptr) != 0) return null
        val full = buf.toKString()
        val lastSep = full.lastIndexOf('/')
        if (lastSep > 0) full.substring(0, lastSep) else null
    }

    private fun buildCandidatePaths(): List<String> {
        val paths = mutableListOf<String>()
        val exeDir = queryExecutableDir()
        if (!exeDir.isNullOrBlank()) {
            paths.add("$exeDir/../Frameworks/libvlc.5.dylib")
            paths.add("$exeDir/../Frameworks/libvlc.dylib")
            paths.add("$exeDir/libvlc.5.dylib")
            paths.add("$exeDir/libvlc.dylib")
        }
        val envVlcDir = getenv("GENERICUI_VLC_DIR")?.toKString()?.trim()?.trimEnd('/')
        if (!envVlcDir.isNullOrBlank()) {
            paths.add("$envVlcDir/libvlc.5.dylib")
            paths.add("$envVlcDir/libvlc.dylib")
        }
        paths.add("/Applications/VLC.app/Contents/MacOS/lib/libvlc.5.dylib")
        paths.add("/Applications/VLC.app/Contents/MacOS/lib/libvlc.dylib")
        paths.add("/opt/homebrew/lib/libvlc.5.dylib")
        paths.add("/usr/local/lib/libvlc.5.dylib")
        paths.add("libvlc.5.dylib")
        paths.add("libvlc.dylib")
        return paths.distinct()
    }

    actual fun openLibVlc(): COpaquePointer? {
        for (path in buildCandidatePaths()) {
            val dir = path.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) {
                val pluginsDir = "$dir/plugins"
                if (access(pluginsDir, F_OK) == 0) {
                    setenv("VLC_PLUGIN_PATH", pluginsDir, 1)
                }
                val corePath = "$dir/libvlccore.9.dylib"
                if (access(corePath, F_OK) == 0) {
                    dlopen(corePath, RTLD_LAZY)
                }
            }
            val handle = dlopen(path, RTLD_LAZY)
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
