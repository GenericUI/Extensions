package com.nexustools.genericui.ext.serviceworker

/**
 * Desktop OS integration primitives for runtime directories, atomic `0600` file writes,
 * single-instance locks, process launching, and login autostart management.
 */
internal expect object DesktopOsHelpers {
    fun currentExecutablePath(): String?

    fun currentProcessId(): Long

    fun runtimeDataDirectory(appId: String): String

    fun ensureDirectoryExists(path: String): Boolean

    fun atomicWriteTextFile(
        path: String,
        content: String,
        ownerOnlyPermissions: Boolean = false
    ): Boolean

    fun readTextFileOrNull(path: String): String?

    fun deleteFileIfExists(path: String): Boolean

    fun acquireSingleInstanceLock(lockKey: String, lockFilePath: String): Boolean

    fun releaseSingleInstanceLock(lockKey: String)

    fun releaseSingleInstanceLock()

    fun registerShutdownHook(hook: () -> Unit)

    fun spawnDetachedProcess(
        executablePath: String,
        args: List<String>,
        workingDir: String? = null
    ): Boolean

    fun isAutostartEnabled(appId: String, appName: String): Boolean

    fun setAutostartEnabled(
        appId: String,
        appName: String,
        targetExecutablePath: String,
        args: List<String>,
        enabled: Boolean
    ): Boolean

    fun generateRandomHexToken(byteCount: Int = 32): String

    fun sleepMillis(ms: Long)

    fun resolveSiblingBinaryCommand(
        siblingBaseName: String,
        jvmMainClassFqn: String
    ): Pair<String, List<String>>?

    fun tryWakeRegisteredBackgroundService(appId: String): Boolean
}
