package com.nexustools.genericui.ext.webview

/**
 * Feature capability flags for a [WebView] runtime.
 *
 * `null` properties inherit the active setting from
 * [com.nexustools.genericui.core.GenericUiExtensionRegistry] (`webview.capability.*`), defaulting
 * to `true` when unset. Non-null values override the registry setting for this instance.
 */
data class WebViewCapabilities(
    val canvas2d: Boolean? = null,
    val webgl: Boolean? = null,
    val media: Boolean? = null,
    val svg: Boolean? = null,
    val webAudio: Boolean? = null,
    val webSockets: Boolean? = null,
    val storage: Boolean? = null,
    val shadowDom: Boolean? = null,
    val iframes: Boolean? = null,
    val workers: Boolean? = null
)
