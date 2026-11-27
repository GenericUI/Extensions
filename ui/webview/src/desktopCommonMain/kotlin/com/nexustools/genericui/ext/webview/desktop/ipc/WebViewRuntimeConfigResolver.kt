package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.ext.webview.WebViewConfig

/**
 * Resolves Gradle-plugin extension registry properties (`webview.*`) merged with instance-level
 * [WebViewConfig] overrides for launching and configuring isolated desktop worker processes.
 */
object WebViewRuntimeConfigResolver {
    const val KEY_DESKTOP_WORKER_BINARY_BASE_NAME: String = "webview.desktop.workerBinaryBaseName"
    const val KEY_SWING_WORKER_MAIN_CLASS: String = "webview.swing.workerMainClass"
    const val KEY_CAP_CANVAS2D: String = "webview.capability.canvas2d"
    const val KEY_CAP_WEBGL: String = "webview.capability.webgl"
    const val KEY_CAP_MEDIA: String = "webview.capability.media"
    const val KEY_CAP_SVG: String = "webview.capability.svg"
    const val KEY_CAP_WEB_AUDIO: String = "webview.capability.webAudio"
    const val KEY_CAP_WEB_SOCKETS: String = "webview.capability.webSockets"
    const val KEY_CAP_STORAGE: String = "webview.capability.storage"
    const val KEY_CAP_SHADOW_DOM: String = "webview.capability.shadowDom"
    const val KEY_CAP_IFRAMES: String = "webview.capability.iframes"
    const val KEY_CAP_WORKERS: String = "webview.capability.workers"

    const val DEFAULT_DESKTOP_WORKER_BINARY_BASE_NAME: String = "genericui-webview-process"
    const val DEFAULT_SWING_WORKER_MAIN_CLASS: String =
        "com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain"

    fun resolve(config: WebViewConfig): WebViewResolvedRuntimeConfig {
        val binaryBase = readStringProperty(
            key = KEY_DESKTOP_WORKER_BINARY_BASE_NAME,
            fallback = DEFAULT_DESKTOP_WORKER_BINARY_BASE_NAME
        )
        val swingMain = readStringProperty(
            key = KEY_SWING_WORKER_MAIN_CLASS,
            fallback = DEFAULT_SWING_WORKER_MAIN_CLASS
        )
        val storageFlag = config.domStorageEnabled && resolveCapability(config.capabilities.storage, KEY_CAP_STORAGE)
        val capabilities = WebViewResolvedCapabilities(
            canvas2d = resolveCapability(config.capabilities.canvas2d, KEY_CAP_CANVAS2D),
            webgl = resolveCapability(config.capabilities.webgl, KEY_CAP_WEBGL),
            media = resolveCapability(config.capabilities.media, KEY_CAP_MEDIA),
            svg = resolveCapability(config.capabilities.svg, KEY_CAP_SVG),
            webAudio = resolveCapability(config.capabilities.webAudio, KEY_CAP_WEB_AUDIO),
            webSockets = resolveCapability(config.capabilities.webSockets, KEY_CAP_WEB_SOCKETS),
            storage = storageFlag,
            shadowDom = resolveCapability(config.capabilities.shadowDom, KEY_CAP_SHADOW_DOM),
            iframes = resolveCapability(config.capabilities.iframes, KEY_CAP_IFRAMES),
            workers = resolveCapability(config.capabilities.workers, KEY_CAP_WORKERS)
        )
        return WebViewResolvedRuntimeConfig(
            workerBinaryBaseName = binaryBase,
            swingWorkerMainClass = swingMain,
            capabilities = capabilities
        )
    }

    private fun readStringProperty(key: String, fallback: String): String {
        val raw = GenericUiExtensionRegistry.getExtensionProperty(key)?.toString()?.trim().orEmpty()
        return raw.ifEmpty { fallback }
    }

    private fun resolveCapability(overrideValue: Boolean?, registryKey: String): Boolean {
        if (overrideValue != null) return overrideValue
        return when (val raw = GenericUiExtensionRegistry.getExtensionProperty(registryKey)) {
            is Boolean -> raw
            is String -> raw.trim().lowercase().toBooleanStrictOrNull() ?: true
            is Number -> raw.toInt() != 0
            else -> true
        }
    }
}
