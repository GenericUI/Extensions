@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.F_OK
import platform.posix.RTLD_LAZY
import platform.posix.access
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.getenv
import platform.posix.readlink
import platform.posix.setenv

/**
 * Linux (`dlopen` / `dlsym`) loader for `libvlc.so.5` / `libvlc.so`.
 *
 * Prioritizes packaged libraries in `<exeDir>/` and `<exeDir>/lib/` before checking
 * `GENERICUI_VLC_DIR` or system library directories.
 */
actual object LibVlcDynamicLoader {

    private fun queryExecutableDir(): String? = memScoped {
        val buf = allocArray<ByteVar>(1024)
        val len = readlink("/proc/self/exe", buf, 1023u)
        if (len <= 0L) return null
        buf[len.toInt()] = 0
        val full = buf.toKString()
        val lastSep = full.lastIndexOf('/')
        if (lastSep > 0) full.substring(0, lastSep) else null
    }

    private fun buildCandidatePaths(): List<String> {
        val paths = mutableListOf<String>()
        val exeDir = queryExecutableDir()
        if (!exeDir.isNullOrBlank()) {
            paths.add("$exeDir/libvlc.so.5")
            paths.add("$exeDir/libvlc.so")
            paths.add("$exeDir/lib/libvlc.so.5")
            paths.add("$exeDir/lib/libvlc.so")
        }
        val envVlcDir = getenv("GENERICUI_VLC_DIR")?.toKString()?.trim()?.trimEnd('/')
        if (!envVlcDir.isNullOrBlank()) {
            paths.add("$envVlcDir/libvlc.so.5")
            paths.add("$envVlcDir/libvlc.so")
        }
        paths.add("/usr/lib/x86_64-linux-gnu/libvlc.so.5")
        paths.add("/usr/lib/aarch64-linux-gnu/libvlc.so.5")
        paths.add("/usr/lib64/libvlc.so.5")
        paths.add("/usr/lib/libvlc.so.5")
        paths.add("libvlc.so.5")
        paths.add("libvlc.so")
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
                val corePath = "$dir/libvlccore.so.9"
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
