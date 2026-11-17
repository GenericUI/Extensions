@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.webview.desktop.process

import com.nexustools.genericui.ext.webview.desktop.NjsPlatformCompilerBootstrap
import platform.posix.fflush
import platform.posix.stdout

/**
 * Native helper executable entry point (`genericui-webview-process`) for isolated Chromium
 * Renderer (`--type=renderer`), GPU (`--type=gpu`), and Network (`--type=network`) OS processes.
 */
object ChromiumSubprocessMain {
    fun run(args: Array<String>) {
        var processType = ChromiumProcessType.RENDERER
        var channelId = "chromium-ipc-0"

        for (arg in args) {
            when {
                arg.startsWith("--type=") -> {
                    val rawType = arg.substringAfter("--type=")
                    processType = ChromiumProcessType.fromCliSwitch(rawType) ?: ChromiumProcessType.RENDERER
                }
                arg.startsWith("--channel-id=") -> {
                    channelId = arg.substringAfter("--channel-id=").ifBlank { "chromium-ipc-0" }
                }
            }
        }

        NjsPlatformCompilerBootstrap.ensureInitialized()

        val workerTransport: ChromiumMojoTransport = when (processType) {
            ChromiumProcessType.RENDERER -> ChromiumRendererProcessWorker(channelId).transport
            ChromiumProcessType.GPU -> ChromiumGpuProcessWorker(channelId).transport
            ChromiumProcessType.NETWORK -> ChromiumNetworkProcessWorker(channelId).transport
            ChromiumProcessType.BROWSER -> ChromiumRendererProcessWorker(channelId).transport
        }

        workerTransport.attachPipeCallbacks(
            writer = { wireLine ->
                println(wireLine)
                fflush(stdout)
                true
            },
            reader = {
                readlnOrNull()
            }
        )

        while (!workerTransport.isClosed) {
            val line = readlnOrNull() ?: break
            if (line.isBlank()) continue
            val decoded = workerTransport.receiveWireLine(line)
            if (decoded?.type == ChromiumMojoMessageType.SHUTDOWN) {
                break
            }
        }
    }
}

fun main(args: Array<String>) {
    ChromiumSubprocessMain.run(args)
}
