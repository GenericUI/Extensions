package com.nexustools.genericui.ext.webview.web

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.web.WebDomElement

/**
 * Platform DOM bridge for `:extensions:webview:web` that connects [WebNativeIframeWebViewImpl]'s
 * [WebDomElement] (`<iframe class="webview-iframe">`) to the browser's native `HTMLIFrameElement`
 * (`src`, `srcdoc`, `contentWindow`, `postMessage`, `window.genericUi` bridge, and script evaluation).
 *
 * **Strictly Web Only — Never Uses NJS.**
 */
internal expect object WebIframeDomBridge {
    fun bindIframeElement(
        element: WebDomElement,
        onLoad: (String) -> Unit,
        onMessage: (String) -> Unit,
        onConsole: (WebViewConsoleMessage) -> Unit,
        onError: (String) -> Unit,
        commInvoker: (String, String) -> String
    )

    fun navigateIframeSrc(element: WebDomElement, url: String)

    fun setIframeSrcdoc(element: WebDomElement, html: String, baseUrl: String?)

    fun reloadIframe(element: WebDomElement, fallbackUrl: String?, fallbackHtml: String?)

    fun evaluateInIframe(
        element: WebDomElement,
        script: String,
        commInvoker: (String, String) -> String,
        onMessage: (String) -> Unit,
        onConsole: (WebViewConsoleMessage) -> Unit,
        onError: (String) -> Unit,
        callback: ((String?) -> Unit)?
    )

    fun postMessageToIframe(element: WebDomElement, message: String, onMessage: (String) -> Unit)

    fun callGuestFunctionInIframe(element: WebDomElement, name: String, payloadJson: String): String?
}
