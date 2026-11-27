package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.ext.ipc.IpcChannelAddress
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.concurrent.TimeUnit

actual object WebViewWorkerLauncher {
    private const val MAX_LOG_LINES: Int = 200

    actual fun currentOsProcessId(): Long = ProcessHandle.current().pid()

    actual fun resolveRuntimeDirectory(webViewId: String): String {
        val baseTmp = System.getProperty("java.io.tmpdir")
            ?.trim()
            ?.ifEmpty { null }
            ?: "."
        val dir = File(baseTmp, "genericui-webview-ipc/${currentOsProcessId()}")
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath())
        }
        return dir.absolutePath
    }

    actual fun spawnWorker(
        address: IpcChannelAddress,
        webViewId: String,
        config: WebViewResolvedRuntimeConfig
    ): WebViewWorkerProcessHandle {
        val javaHome = System.getProperty("java.home")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("System property 'java.home' is unavailable for spawning worker JVM")
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val javaBin = File(javaHome, if (isWindows) "bin/java.exe" else "bin/java")
        val javaExecutable = if (javaBin.isFile) javaBin.absolutePath else "java"
        val classpath = System.getProperty("java.class.path")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("System property 'java.class.path' is empty for spawning worker JVM")

        val command = ArrayList<String>().apply {
            add(javaExecutable)
            add("-Djava.awt.headless=true")
            add("-Dapple.awt.UIElement=true")
            System.getProperty("webview.desktop.workerBinaryBaseName")?.takeIf { it.isNotBlank() }?.let {
                add("-Dwebview.desktop.workerBinaryBaseName=$it")
            }
            System.getProperty("webview.swing.workerMainClass")?.takeIf { it.isNotBlank() }?.let {
                add("-Dwebview.swing.workerMainClass=$it")
            }
            add("-cp")
            add(classpath)
            add(config.swingWorkerMainClass)
            add("--ipc-kind=${address.transportKind}")
            add("--ipc-endpoint=${address.endpointAddress}")
            add("--webview-id=$webViewId")
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val logLines = ArrayDeque<String>()
        val stdoutThread = startStreamDrainThread(process.inputStream, "webview-worker-stdout-$webViewId", logLines, isError = false)
        val stderrThread = startStreamDrainThread(process.errorStream, "webview-worker-stderr-$webViewId", logLines, isError = true)

        return JvmWorkerProcessHandle(
            process = process,
            pid = process.pid(),
            logLines = logLines,
            stdoutThread = stdoutThread,
            stderrThread = stderrThread
        )
    }

    private fun startStreamDrainThread(
        stream: java.io.InputStream,
        threadName: String,
        logLines: ArrayDeque<String>,
        isError: Boolean
    ): Thread = Thread({
        try {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    synchronized(logLines) {
                        if (logLines.size >= MAX_LOG_LINES) {
                            logLines.removeFirst()
                        }
                        logLines.addLast(if (isError) "[stderr] $line" else "[stdout] $line")
                    }
                    if (isError) {
                        System.err.println("[WebViewWorker][$threadName] $line")
                    }
                }
            }
        } catch (t: Throwable) {
            synchronized(logLines) {
                if (logLines.size >= MAX_LOG_LINES) {
                    logLines.removeFirst()
                }
                logLines.addLast("[stream-read-terminated] ${t::class.simpleName}: ${t.message}")
            }
        }
    }, threadName).apply {
        isDaemon = true
        start()
    }

    private class JvmWorkerProcessHandle(
        private val process: Process,
        override val pid: Long,
        private val logLines: ArrayDeque<String>,
        private val stdoutThread: Thread,
        private val stderrThread: Thread
    ) : WebViewWorkerProcessHandle {
        override fun isAlive(): Boolean = process.isAlive

        override fun killForcibly() {
            process.destroyForcibly()
            process.waitFor(3_000L, TimeUnit.MILLISECONDS)
        }

        override fun waitForExit(timeoutMs: Int): Boolean =
            process.waitFor(timeoutMs.coerceAtLeast(0).toLong(), TimeUnit.MILLISECONDS)

        override fun exitCodeOrNull(): Int? =
            if (process.isAlive) null else process.exitValue()

        override fun diagnosticsSummary(): String {
            val tail = synchronized(logLines) { logLines.toList().takeLast(20) }
            return "pid=$pid alive=${isAlive()} exitCode=${exitCodeOrNull()} logs=${tail.joinToString(" | ")}"
        }

        override fun closeHandle() {
            stdoutThread.interrupt()
            stderrThread.interrupt()
        }
    }
}
