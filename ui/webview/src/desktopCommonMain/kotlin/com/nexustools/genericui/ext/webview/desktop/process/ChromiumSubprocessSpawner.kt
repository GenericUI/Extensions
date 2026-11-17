package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Locates and spawns the isolated Chromium WebView helper executable
 * (`genericui-webview-process.exe` on Windows, `genericui-webview-process` on macOS/Linux,
 * or a child JVM process running `ChromiumSwingSubprocessMain` on Swing) with
 * `--type=renderer|gpu|network --channel-id=<id>`, providing a bidirectional
 * line-framed [ChromiumMojoTransport].
 */
expect object ChromiumSubprocessSpawner {
    fun locateSubprocessExecutable(): String?

    fun buildCommandLineArguments(
        executablePath: String,
        processType: ChromiumProcessType,
        channelId: String
    ): List<String>

    fun spawnSubprocess(
        processType: ChromiumProcessType,
        channelId: String,
        workerTransport: ChromiumMojoTransport
    ): ChromiumMojoTransport
}
