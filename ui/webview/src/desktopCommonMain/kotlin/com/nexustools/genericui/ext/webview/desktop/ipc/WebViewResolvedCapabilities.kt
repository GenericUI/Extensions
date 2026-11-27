package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.component.system.PreferenceElement

/**
 * Resolved feature capability flags transmitted to a worker OS process during `webview.init`
 * and exposed to guest content via feature inspection APIs.
 */
data class WebViewResolvedCapabilities(
    val canvas2d: Boolean = true,
    val webgl: Boolean = true,
    val media: Boolean = true,
    val svg: Boolean = true,
    val webAudio: Boolean = true,
    val webSockets: Boolean = true,
    val storage: Boolean = true,
    val shadowDom: Boolean = true,
    val iframes: Boolean = true,
    val workers: Boolean = true
) {
    fun toPreferenceElement(): PreferenceElement = PreferenceElement.ofObject(
        linkedMapOf(
            "canvas2d" to PreferenceElement.ofBoolean(canvas2d),
            "webgl" to PreferenceElement.ofBoolean(webgl),
            "media" to PreferenceElement.ofBoolean(media),
            "svg" to PreferenceElement.ofBoolean(svg),
            "webAudio" to PreferenceElement.ofBoolean(webAudio),
            "webSockets" to PreferenceElement.ofBoolean(webSockets),
            "storage" to PreferenceElement.ofBoolean(storage),
            "shadowDom" to PreferenceElement.ofBoolean(shadowDom),
            "iframes" to PreferenceElement.ofBoolean(iframes),
            "workers" to PreferenceElement.ofBoolean(workers)
        )
    )

    companion object {
        fun fromPreferenceElement(element: PreferenceElement?): WebViewResolvedCapabilities {
            if (element == null || element.isNull) return WebViewResolvedCapabilities()
            return WebViewResolvedCapabilities(
                canvas2d = element["canvas2d"]?.asBoolean(true) ?: true,
                webgl = element["webgl"]?.asBoolean(true) ?: true,
                media = element["media"]?.asBoolean(true) ?: true,
                svg = element["svg"]?.asBoolean(true) ?: true,
                webAudio = element["webAudio"]?.asBoolean(true) ?: true,
                webSockets = element["webSockets"]?.asBoolean(true) ?: true,
                storage = element["storage"]?.asBoolean(true) ?: true,
                shadowDom = element["shadowDom"]?.asBoolean(true) ?: true,
                iframes = element["iframes"]?.asBoolean(true) ?: true,
                workers = element["workers"]?.asBoolean(true) ?: true
            )
        }
    }
}
