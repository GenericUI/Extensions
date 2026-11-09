package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Identifies the role of an isolated process in the multi-process Chromium Desktop WebView architecture.
 */
enum class ChromiumProcessType(val cliSwitchValue: String) {
    BROWSER("browser"),
    RENDERER("renderer"),
    GPU("gpu"),
    NETWORK("network");

    companion object {
        fun fromCliSwitch(value: String): ChromiumProcessType? {
            val clean = value.trim()
            return entries.firstOrNull {
                it.cliSwitchValue.equals(clean, ignoreCase = true) ||
                    it.name.equals(clean, ignoreCase = true)
            }
        }
    }
}
