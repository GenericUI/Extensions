@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getpid
import kotlin.random.Random

internal actual object DesktopIpcTestTempDir {
    actual fun createTempDir(prefix: String): String {
        val base = NSTemporaryDirectory().trimEnd('/')
        val path = "$base/$prefix-${getpid()}-${Random.nextInt(100_000, 999_999)}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return path
    }

    actual fun deleteTempDir(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}
