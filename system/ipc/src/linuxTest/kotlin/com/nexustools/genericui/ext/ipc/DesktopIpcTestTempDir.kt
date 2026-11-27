package com.nexustools.genericui.ext.ipc

import platform.posix.getpid
import platform.posix.rmdir
import kotlin.random.Random

internal actual object DesktopIpcTestTempDir {
    actual fun createTempDir(prefix: String): String {
        val path = "/tmp/$prefix-${getpid()}-${Random.nextInt(100_000, 999_999)}"
        LinuxUnixSocketAddressHelper.ensureDirectoryExists(path)
        return path
    }

    actual fun deleteTempDir(path: String) {
        rmdir(path)
    }
}
