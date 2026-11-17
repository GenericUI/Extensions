package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Lifecycle state of a Chromium child process managed by the Browser process host.
 */
enum class ChromiumProcessState {
    STARTING,
    RUNNING,
    CRASHED,
    TERMINATED
}
