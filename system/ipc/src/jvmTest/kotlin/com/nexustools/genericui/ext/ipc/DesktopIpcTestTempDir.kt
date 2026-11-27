package com.nexustools.genericui.ext.ipc

import java.io.File
import java.nio.file.Files

internal actual object DesktopIpcTestTempDir {
    actual fun createTempDir(prefix: String): String =
        Files.createTempDirectory(prefix).toFile().absolutePath

    actual fun deleteTempDir(path: String) {
        File(path).deleteRecursively()
    }
}
