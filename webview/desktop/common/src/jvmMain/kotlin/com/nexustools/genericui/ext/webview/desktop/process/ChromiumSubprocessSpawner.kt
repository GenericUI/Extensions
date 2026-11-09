package com.nexustools.genericui.ext.webview.desktop.process

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

actual object ChromiumSubprocessSpawner {
    private val candidateRelativePaths = listOf(
        "genericui-webview-process.exe",
        "genericui-webview-process",
        "Contents/MacOS/genericui-webview-process",
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

    actual fun locateSubprocessExecutable(): String? {
        val envOverride = System.getenv("GENERICUI_WEBVIEW_PROCESS_EXE")?.trim()
        if (!envOverride.isNullOrEmpty()) {
            val envFile = File(envOverride)
            if (envFile.isFile) return envFile.absolutePath
        }

        val searchRoots = LinkedHashSet<File>()
        val userDir = System.getProperty("user.dir")?.let { File(it).absoluteFile }
        var cur = userDir
        var depth = 0
        while (cur != null && depth < 6) {
            searchRoots.add(cur)
            cur = cur.parentFile
            depth++
        }

        for (root in searchRoots) {
            for (rel in candidateRelativePaths) {
                val candidate = File(root, rel)
                if (candidate.isFile) {
                    return candidate.absolutePath
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

        val command = resolveJvmOrNativeCommand(processType, channelId)
        if (command != null) {
            val spawned = runCatching {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start()
                val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                val shutdownHook = Thread {
                    runCatching {
                        if (process.isAlive) {
                            process.destroyForcibly()
                        }
                    }
                }
                Runtime.getRuntime().addShutdownHook(shutdownHook)

                browserTransport.executablePath = command.firstOrNull()
                browserTransport.commandLine = command
                browserTransport.isExternalOsProcess = true
                browserTransport.processId = runCatching { process.pid() }.getOrDefault(1000L + processType.ordinal)
                browserTransport.attachPipeCallbacks(
                    writer = { wireLine ->
                        if (!process.isAlive) {
                            false
                        } else {
                            writer.write(wireLine)
                            writer.newLine()
                            writer.flush()
                            true
                        }
                    },
                    reader = {
                        if (!process.isAlive) null else reader.readLine()
                    },
                    onClose = {
                        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
                        runCatching { writer.close() }
                        runCatching { reader.close() }
                        runCatching {
                            if (process.isAlive) {
                                process.destroy()
                            }
                        }
                    }
                )
                true
            }.getOrDefault(false)

            if (spawned) {
                return browserTransport
            }
        }

        browserTransport.isExternalOsProcess = false
        browserTransport.processId = runCatching { ProcessHandle.current().pid() }.getOrDefault(100L) + processType.ordinal + 1L
        browserTransport.commandLine = buildCommandLineArguments(
            executablePath = "genericui-webview-process",
            processType = processType,
            channelId = channelId
        )
        return browserTransport
    }

    private fun resolveJvmOrNativeCommand(
        processType: ChromiumProcessType,
        channelId: String
    ): List<String>? {
        val swingMainClass = "com.nexustools.genericui.ext.webview.desktop.swing.ChromiumSwingSubprocessMain"
        val swingMainAvailable = runCatching {
            Class.forName(swingMainClass)
            true
        }.getOrDefault(false)

        if (swingMainAvailable) {
            val javaHome = System.getProperty("java.home")
            val classPath = System.getProperty("java.class.path")
            if (!javaHome.isNullOrBlank() && !classPath.isNullOrBlank()) {
                val javaExeWin = File(javaHome, "bin/java.exe")
                val javaExeUnix = File(javaHome, "bin/java")
                val javaBin = when {
                    javaExeWin.isFile -> javaExeWin.absolutePath
                    javaExeUnix.isFile -> javaExeUnix.absolutePath
                    else -> null
                }
                if (javaBin != null) {
                    return listOf(
                        javaBin,
                        "-cp",
                        classPath,
                        swingMainClass,
                        "--type=${processType.cliSwitchValue}",
                        "--channel-id=$channelId"
                    )
                }
            }
        }

        val nativeExe = locateSubprocessExecutable()
        if (nativeExe != null) {
            return buildCommandLineArguments(nativeExe, processType, channelId)
        }
        return null
    }
}
