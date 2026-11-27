package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.ext.ipc.IpcChannelAddress
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CpuArchitecture
import kotlin.native.OsFamily
import kotlin.native.Platform

internal expect object NativeWorkerPlatformSupport {
    fun currentPid(): Long
    fun resolveExecutableDirectory(): String?
    fun resolveCurrentWorkingDirectory(): String?
    fun resolveTempBaseDir(): String
    fun fileExists(path: String): Boolean
    fun ensureDirectoryExists(path: String): Boolean
    fun spawnExecutable(executablePath: String, args: List<String>): WebViewWorkerProcessHandle
}

actual object WebViewWorkerLauncher {
    actual fun currentOsProcessId(): Long = NativeWorkerPlatformSupport.currentPid()

    actual fun resolveRuntimeDirectory(webViewId: String): String {
        val base = NativeWorkerPlatformSupport.resolveTempBaseDir()
            .replace('\\', '/')
            .trimEnd('/')
        val runtimeDir = "$base/gui-wv-${currentOsProcessId()}"
        NativeWorkerPlatformSupport.ensureDirectoryExists(runtimeDir)
        return runtimeDir
    }

    @OptIn(ExperimentalNativeApi::class)
    actual fun spawnWorker(
        address: IpcChannelAddress,
        webViewId: String,
        config: WebViewResolvedRuntimeConfig
    ): WebViewWorkerProcessHandle {
        val executablePath = resolveWorkerExecutablePath(config.workerBinaryBaseName)
        val args = listOf(
            "--ipc-kind=${address.transportKind}",
            "--ipc-endpoint=${address.endpointAddress}",
            "--webview-id=$webViewId"
        )
        return NativeWorkerPlatformSupport.spawnExecutable(executablePath, args)
    }

    @OptIn(ExperimentalNativeApi::class)
    private fun resolveWorkerExecutablePath(configuredBaseName: String): String {
        val baseName = configuredBaseName.trim()
            .ifEmpty { WebViewRuntimeConfigResolver.DEFAULT_DESKTOP_WORKER_BINARY_BASE_NAME }
        val isWin = Platform.osFamily == OsFamily.WINDOWS
        val sep = if (isWin) "\\" else "/"

        fun toExeName(stem: String): String =
            if (isWin && !stem.endsWith(".exe", ignoreCase = true)) "$stem.exe" else stem

        val primaryExe = toExeName(baseName)
        val defaultExe = toExeName(WebViewRuntimeConfigResolver.DEFAULT_DESKTOP_WORKER_BINARY_BASE_NAME)
        val exeNames = linkedSetOf(primaryExe, defaultExe)

        val candidates = ArrayList<String>()
        val exeDir = NativeWorkerPlatformSupport.resolveExecutableDirectory()?.trimEnd('/', '\\')
        if (!exeDir.isNullOrEmpty()) {
            for (name in exeNames) {
                candidates.add("$exeDir$sep$name")
                candidates.add("$exeDir$sep$baseName$sep$name")
            }
            val parentIdx = maxOf(exeDir.lastIndexOf('/'), exeDir.lastIndexOf('\\'))
            if (parentIdx > 0) {
                val parentDir = exeDir.substring(0, parentIdx)
                for (name in exeNames) {
                    candidates.add("$parentDir${sep}webviewProcessDebugExecutable$sep$name")
                    candidates.add("$parentDir${sep}webviewProcessReleaseExecutable$sep$name")
                }
            }
        }

        val targetFolder = when (Platform.osFamily) {
            OsFamily.WINDOWS -> "mingwX64"
            OsFamily.MACOSX -> "macosArm64"
            OsFamily.LINUX -> if (Platform.cpuArchitecture == CpuArchitecture.ARM64) "linuxArm64" else "linuxX64"
            else -> "linuxX64"
        }
        val cwd = NativeWorkerPlatformSupport.resolveCurrentWorkingDirectory()?.trimEnd('/', '\\')
        if (!cwd.isNullOrEmpty()) {
            for (name in exeNames) {
                candidates.add("$cwd${sep}build${sep}bin${sep}$targetFolder${sep}webviewProcessDebugExecutable$sep$name")
                candidates.add("$cwd${sep}build${sep}bin${sep}$targetFolder${sep}webviewProcessReleaseExecutable$sep$name")
                candidates.add("$cwd${sep}extensions${sep}ui${sep}webview${sep}build${sep}bin${sep}$targetFolder${sep}webviewProcessDebugExecutable$sep$name")
                candidates.add("$cwd${sep}extensions${sep}ui${sep}webview${sep}build${sep}bin${sep}$targetFolder${sep}webviewProcessReleaseExecutable$sep$name")
            }
        }

        for (candidate in candidates) {
            if (NativeWorkerPlatformSupport.fileExists(candidate)) {
                return candidate
            }
        }
        throw IllegalStateException(
            "Unable to locate desktop WebView worker binary '$primaryExe'. Searched: ${candidates.joinToString(", ")}"
        )
    }
}
