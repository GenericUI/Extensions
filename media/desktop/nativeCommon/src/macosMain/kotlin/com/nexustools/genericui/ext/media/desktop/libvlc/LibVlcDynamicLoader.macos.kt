@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.COpaquePointer
import platform.posix.RTLD_LAZY
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.dlsym

/**
 * macOS (`dlopen` / `dlsym`) loader for `libvlc.5.dylib` / `libvlc.dylib`.
 */
actual object LibVlcDynamicLoader {
    private val candidatePaths = listOf(
        "libvlc.5.dylib",
        "libvlc.dylib",
        "/Applications/VLC.app/Contents/MacOS/lib/libvlc.5.dylib",
        "/Applications/VLC.app/Contents/MacOS/lib/libvlc.dylib",
        "/opt/homebrew/lib/libvlc.5.dylib",
        "/usr/local/lib/libvlc.5.dylib"
    )

    actual fun openLibVlc(): COpaquePointer? {
        for (path in candidatePaths) {
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
