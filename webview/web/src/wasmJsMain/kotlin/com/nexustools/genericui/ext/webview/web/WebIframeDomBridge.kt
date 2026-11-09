@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.webview.web

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.web.WebDomElement

@JsFun("(peer, url) => { if (peer) { try { peer.removeAttribute('srcdoc'); peer.setAttribute('src', url); } catch (_e) {} } }")
private external fun wasmNavigateIframeSrc(peer: JsAny?, url: String)

@JsFun("(peer, html) => { if (peer) { try { peer.setAttribute('srcdoc', html); } catch (_e) {} } }")
private external fun wasmSetIframeSrcdoc(peer: JsAny?, html: String)

@JsFun("(peer, message) => { if (peer && peer.contentWindow && typeof peer.contentWindow.postMessage === 'function') { try { peer.contentWindow.postMessage(message, '*'); return true; } catch (_e) {} } return false; }")
private external fun wasmPostMessageToIframe(peer: JsAny?, message: String): Boolean

@JsFun("(code) => { try { const r = eval(code); return (r === undefined || r === null) ? null : String(r); } catch (_e) { return null; } }")
private external fun wasmEvalScript(code: String): String?

internal actual object WebIframeDomBridge {

    private val guestScriptFunctions = HashMap<WebDomElement, MutableMap<String, String>>()

    actual fun bindIframeElement(
        element: WebDomElement,
        onLoad: (String) -> Unit,
        onMessage: (String) -> Unit,
        onConsole: (WebViewConsoleMessage) -> Unit,
        onError: (String) -> Unit,
        commInvoker: (String, String) -> String
    ) {
        guestScriptFunctions.getOrPut(element) { LinkedHashMap() }
    }

    actual fun navigateIframeSrc(element: WebDomElement, url: String) {
        wasmNavigateIframeSrc(element.nativeBrowserPeer as? JsAny, url)
    }

    actual fun setIframeSrcdoc(element: WebDomElement, html: String, baseUrl: String?) {
        wasmSetIframeSrcdoc(element.nativeBrowserPeer as? JsAny, html)
    }

    actual fun reloadIframe(element: WebDomElement, fallbackUrl: String?, fallbackHtml: String?) {
        if (fallbackHtml != null) {
            setIframeSrcdoc(element, fallbackHtml, fallbackUrl)
        } else if (fallbackUrl != null) {
            navigateIframeSrc(element, fallbackUrl)
        }
    }

    actual fun evaluateInIframe(
        element: WebDomElement,
        script: String,
        commInvoker: (String, String) -> String,
        onMessage: (String) -> Unit,
        onConsole: (WebViewConsoleMessage) -> Unit,
        onError: (String) -> Unit,
        callback: ((String?) -> Unit)?
    ) {
        val res = wasmEvalScript(script)
        callback?.invoke(res)
    }

    actual fun postMessageToIframe(element: WebDomElement, message: String, onMessage: (String) -> Unit) {
        val posted = wasmPostMessageToIframe(element.nativeBrowserPeer as? JsAny, message)
        if (!posted) {
            onMessage(message)
        }
    }

    actual fun callGuestFunctionInIframe(element: WebDomElement, name: String, payloadJson: String): String? =
        guestScriptFunctions[element]?.get(name)
}
