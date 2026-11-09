package com.nexustools.genericui.ext.webview.desktop.swing

import com.nexustools.genericui.ext.webview.desktop.NjsPlatformCompilerBootstrap
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumGpuProcessWorker
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumMojoMessageType
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumMojoTransport
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumNetworkProcessWorker
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumProcessType
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumRendererProcessWorker

/**
 * JVM subprocess entry point for Swing Desktop WebView multi-process worker execution
 * (`--type=renderer|gpu|network --channel-id=<id>`).
 */
object ChromiumSwingSubprocessMain {
    @JvmStatic
    fun main(args: Array<String>) {
        var processType = ChromiumProcessType.RENDERER
        var channelId = "chromium-swing-ipc-0"

        for (arg in args) {
            when {
                arg.startsWith("--type=") -> {
                    val rawType = arg.substringAfter("--type=")
                    processType = ChromiumProcessType.fromCliSwitch(rawType) ?: ChromiumProcessType.RENDERER
                }
                arg.startsWith("--channel-id=") -> {
                    channelId = arg.substringAfter("--channel-id=").ifBlank { "chromium-swing-ipc-0" }
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
                System.out.println(wireLine)
                System.out.flush()
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
