@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.reinterpret
import platform.windows.FreeLibrary
import platform.windows.GetProcAddress
import platform.windows.HMODULE
import platform.windows.LoadLibraryA

/**
 * Win32 (`LoadLibraryA` / `GetProcAddress`) loader for `libvlc.dll`.
 */
actual object LibVlcDynamicLoader {
    private val candidatePaths = listOf(
        "libvlc.dll",
        "C:\\Program Files\\VideoLAN\\VLC\\libvlc.dll",
        "C:\\Program Files (x86)\\VideoLAN\\VLC\\libvlc.dll"
    )

    actual fun openLibVlc(): COpaquePointer? {
        for (path in candidatePaths) {
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
