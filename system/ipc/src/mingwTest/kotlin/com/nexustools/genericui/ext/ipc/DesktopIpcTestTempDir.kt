@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.CreateDirectoryW
import platform.windows.GetCurrentProcessId
import platform.windows.GetTempPathW
import platform.windows.MAX_PATH
import platform.windows.RemoveDirectoryW
import kotlin.random.Random

internal actual object DesktopIpcTestTempDir {
    actual fun createTempDir(prefix: String): String {
        val base = memScoped {
            val buf = allocArray<UShortVar>(MAX_PATH * 2)
            val len = GetTempPathW((MAX_PATH * 2).toUInt(), buf).toInt()
            if (len > 0) buf.toKString().trimEnd('\\', '/') else "C:\\Temp"
        }
        val path = "$base\\$prefix-${GetCurrentProcessId()}-${Random.nextInt(100_000, 999_999)}"
        CreateDirectoryW(path, null)
        return path
    }

    actual fun deleteTempDir(path: String) {
        RemoveDirectoryW(path.replace('/', '\\'))
    }
}
