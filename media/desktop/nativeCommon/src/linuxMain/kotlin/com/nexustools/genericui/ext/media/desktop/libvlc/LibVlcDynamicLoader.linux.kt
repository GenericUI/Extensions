@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.COpaquePointer
import platform.posix.RTLD_LAZY
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.dlsym

/**
 * Linux (`dlopen` / `dlsym`) loader for `libvlc.so.5` / `libvlc.so`.
 */
actual object LibVlcDynamicLoader {
    private val candidatePaths = listOf(
        "libvlc.so.5",
        "libvlc.so",
        "/usr/lib/x86_64-linux-gnu/libvlc.so.5",
        "/usr/lib/aarch64-linux-gnu/libvlc.so.5",
        "/usr/lib64/libvlc.so.5",
        "/usr/lib/libvlc.so.5"
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
