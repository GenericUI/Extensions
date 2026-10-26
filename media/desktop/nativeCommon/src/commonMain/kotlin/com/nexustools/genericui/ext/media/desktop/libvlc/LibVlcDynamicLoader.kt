@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.COpaquePointer

/**
 * Platform dynamic linker loader for native `libvlc` (`libvlc.dll` on Windows,
 * `libvlc.so.5`/`libvlc.so` on Linux, `libvlc.5.dylib`/`libvlc.dylib` on macOS).
 */
expect object LibVlcDynamicLoader {
    fun openLibVlc(): COpaquePointer?
    fun findSymbol(libraryHandle: COpaquePointer, name: String): COpaquePointer?
    fun closeLibVlc(libraryHandle: COpaquePointer)
}
