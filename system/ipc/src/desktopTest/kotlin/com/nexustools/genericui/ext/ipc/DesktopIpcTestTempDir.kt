package com.nexustools.genericui.ext.ipc

internal expect object DesktopIpcTestTempDir {
    fun createTempDir(prefix: String): String
    fun deleteTempDir(path: String)
}
