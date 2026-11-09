@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.webview.desktop.process

import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.getenv
import platform.posix.getpid

actual object ChromiumSubprocessSpawner {
    private val candidateRelativePaths = listOf(
        "genericui-webview-process.exe",
        "genericui-webview-process",
        "Contents/MacOS/genericui-webview-process",
        "../MacOS/genericui-webview-process",
        "build/bin/mingwX64/webviewProcessDebugExecutable/genericui-webview-process.exe",
        "build/bin/mingwX64/webviewProcessReleaseExecutable/genericui-webview-process.exe",
        "build/bin/macosArm64/webviewProcessDebugExecutable/genericui-webview-process.kexe",
        "build/bin/macosArm64/webviewProcessReleaseExecutable/genericui-webview-process.kexe",
        "build/bin/linuxX64/webviewProcessDebugExecutable/genericui-webview-process.kexe",
        "build/bin/linuxX64/webviewProcessReleaseExecutable/genericui-webview-process.kexe",
        "build/bin/linuxArm64/webviewProcessDebugExecutable/genericui-webview-process.kexe",
        "build/bin/linuxArm64/webviewProcessReleaseExecutable/genericui-webview-process.kexe",
        "extensions/webview/desktop/nativeCommon/build/bin/mingwX64/webviewProcessDebugExecutable/genericui-webview-process.exe",
        "extensions/webview/desktop/nativeCommon/build/bin/mingwX64/webviewProcessReleaseExecutable/genericui-webview-process.exe",
        "extensions/webview/desktop/nativeCommon/build/bin/macosArm64/webviewProcessDebugExecutable/genericui-webview-process.kexe",
        "extensions/webview/desktop/nativeCommon/build/bin/linuxX64/webviewProcessDebugExecutable/genericui-webview-process.kexe",
        "extensions/webview/desktop/nativeCommon/build/bin/linuxArm64/webviewProcessDebugExecutable/genericui-webview-process.kexe"
    )

    private val parentPrefixes = listOf(
        "",
        "../",
        "../../",
        "../../../",
        "../../../../",
        "../../../../../"
    )

    actual fun locateSubprocessExecutable(): String? {
        val envOverride = getenv("GENERICUI_WEBVIEW_PROCESS_EXE")?.toKString()?.trim()
        if (!envOverride.isNullOrEmpty() && fileExists(envOverride)) {
            return envOverride
        }

        for (prefix in parentPrefixes) {
            for (rel in candidateRelativePaths) {
                val candidate = "$prefix$rel"
                if (fileExists(candidate)) {
                    return candidate
                }
            }
        }
        return null
    }

    actual fun buildCommandLineArguments(
        executablePath: String,
        processType: ChromiumProcessType,
        channelId: String
    ): List<String> = listOf(
        executablePath,
        "--type=${processType.cliSwitchValue}",
        "--channel-id=$channelId"
    )

    actual fun spawnSubprocess(
        processType: ChromiumProcessType,
        channelId: String,
        workerTransport: ChromiumMojoTransport
    ): ChromiumMojoTransport {
        val browserTransport = ChromiumMojoTransport(channelId, ChromiumProcessType.BROWSER)
        browserTransport.connectPeer(workerTransport)

        val locatedExe = locateSubprocessExecutable()
        val effectiveExe = locatedExe ?: "genericui-webview-process"
        browserTransport.executablePath = locatedExe
        browserTransport.commandLine = buildCommandLineArguments(effectiveExe, processType, channelId)
        browserTransport.isExternalOsProcess = locatedExe != null
        browserTransport.processId = getpid().toLong() * 10L + processType.ordinal + 1L
        return browserTransport
    }

    private fun fileExists(path: String): Boolean {
        val fp = fopen(path, "rb") ?: return false
        fclose(fp)
        return true
    }
}
