package com.nexustools.genericui.ext.webview.web

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.web.WebDomElement

internal actual object WebIframeDomBridge {

    private val frameContexts = HashMap<WebDomElement, dynamic>()
    private val scriptTagRegex = Regex("<script\\b[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
    private val titleTagRegex = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)

    private fun ensureFrameContext(
        element: WebDomElement,
        onMessage: (String) -> Unit,
        onConsole: (WebViewConsoleMessage) -> Unit,
        onError: (String) -> Unit,
        commInvoker: (String, String) -> String
    ): dynamic {
        val existing = frameContexts[element]
        if (existing != null) {
            existing.__onMessage = onMessage
            existing.__onConsole = onConsole
            existing.__onError = onError
            existing.__commInvoker = commInvoker
            return existing
        }

        val ctx: dynamic = js("({})")
        ctx.__onMessage = onMessage
        ctx.__onConsole = onConsole
        ctx.__onError = onError
        ctx.__commInvoker = commInvoker
        ctx.__guestFunctions = js("({})")
        ctx.__messageListeners = js("([])")
        ctx.__elementsById = js("({})")

        val makeElemFn: dynamic = js(
            """
            (function(ctxRef, tag) {
                var el = {
                    tagName: String(tag || 'DIV').toUpperCase(),
                    id: '',
                    className: '',
                    textContent: '',
                    innerHTML: '',
                    attributes: {},
                    children: [],
                    setAttribute: function(k, v) {
                        this.attributes[String(k)] = String(v);
                        if (String(k).toLowerCase() === 'id') {
                            this.id = String(v);
                            ctxRef.__elementsById[this.id] = this;
                        }
                    },
                    getAttribute: function(k) {
                        if (String(k).toLowerCase() === 'id' && this.id) return this.id;
                        return this.attributes[String(k)] !== undefined ? this.attributes[String(k)] : null;
                    },
                    appendChild: function(c) {
                        if (c) {
                            this.children.push(c);
                            if (c.id) ctxRef.__elementsById[c.id] = c;
                        }
                        return c;
                    }
                };
                return el;
            })
            """
        )

        val docObj: dynamic = js("({})")
        docObj.title = ""
        docObj.readyState = "complete"
        docObj.body = makeElemFn(ctx, "body")
        docObj.head = makeElemFn(ctx, "head")
        docObj.createElement = { tag: String -> makeElemFn(ctx, tag) }
        docObj.getElementById = { id: String ->
            val found = ctx.__elementsById[id]
            if (found != undefined) found else null
        }
        docObj.querySelector = { sel: String ->
            if (sel.startsWith("#")) {
                val id = sel.substring(1)
                val found = ctx.__elementsById[id]
                if (found != undefined) found else null
            } else if (sel == "body") {
                docObj.body
            } else if (sel == "head") {
                docObj.head
            } else {
                null
            }
        }
        ctx.document = docObj

        val consoleObj: dynamic = js("({})")
        consoleObj.log = { msg: Any? ->
            val cb = ctx.__onConsole
            if (cb != null) cb(WebViewConsoleMessage("log", msg?.toString() ?: ""))
        }
        consoleObj.info = { msg: Any? ->
            val cb = ctx.__onConsole
            if (cb != null) cb(WebViewConsoleMessage("info", msg?.toString() ?: ""))
        }
        consoleObj.warn = { msg: Any? ->
            val cb = ctx.__onConsole
            if (cb != null) cb(WebViewConsoleMessage("warn", msg?.toString() ?: ""))
        }
        consoleObj.error = { msg: Any? ->
            val cb = ctx.__onConsole
            if (cb != null) cb(WebViewConsoleMessage("error", msg?.toString() ?: ""))
        }
        ctx.console = consoleObj

        val bridgeObj: dynamic = js("({})")
        val postMsgFn = { msg: Any? ->
            val text = msg?.toString() ?: ""
            val cb = ctx.__onMessage
            if (cb != null) cb(text)
            Unit
        }
        val callBridgeFn = { name: Any?, payload: Any? ->
            val fnName = name?.toString() ?: ""
            val arg = payload?.toString() ?: ""
            val inv = ctx.__commInvoker
            if (inv != null) inv(fnName, arg) as String else "{}"
        }
        val regFn = { name: Any?, fn: dynamic ->
            val fnName = name?.toString() ?: ""
            if (fnName.isNotEmpty() && fn != null) {
                ctx.__guestFunctions[fnName] = fn
            }
            Unit
        }
        val unregFn = { name: Any? ->
            val fnName = name?.toString() ?: ""
            if (fnName.isNotEmpty()) {
                js("delete ctx.__guestFunctions[fnName]")
            }
            Unit
        }
        bridgeObj.postMessage = postMsgFn
        bridgeObj.call = callBridgeFn
        bridgeObj.callCommunicationFunction = callBridgeFn
        bridgeObj.invokeBridge = callBridgeFn
        bridgeObj.registerFunction = regFn
        bridgeObj.registerCommunicationFunction = regFn
        bridgeObj.unregisterFunction = unregFn
        bridgeObj.unregisterCommunicationFunction = unregFn

        ctx.genericUi = bridgeObj
        ctx.postMessage = postMsgFn
        ctx.addEventListener = { type: String, listener: dynamic ->
            if (type == "message" && listener != null) {
                ctx.__messageListeners.push(listener)
            }
        }
        ctx.window = ctx
        ctx.globalThis = ctx
        ctx.self = ctx

        frameContexts[element] = ctx
        return ctx
    }

    actual fun bindIframeElement(
        element: WebDomElement,
        onLoad: (String) -> Unit,
        onMessage: (String) -> Unit,
        onConsole: (WebViewConsoleMessage) -> Unit,
        onError: (String) -> Unit,
        commInvoker: (String, String) -> String
    ) {
        val ctx = ensureFrameContext(element, onMessage, onConsole, onError, commInvoker)
        val peer: dynamic = element.nativeBrowserPeer
        if (peer != null) {
            peer.onload = {
                val cw: dynamic = peer.contentWindow
                if (cw != null) {
                    runCatching {
                        cw.genericUi = ctx.genericUi
                    }
                }
                val loadedUrl = (peer.getAttribute("src") as? String)
                    ?: element.getAttribute("src")
                    ?: element.getAttribute("data-base-url")
                    ?: "about:blank"
                onLoad(loadedUrl)
            }
        }
    }

    actual fun navigateIframeSrc(element: WebDomElement, url: String) {
        val peer: dynamic = element.nativeBrowserPeer
        if (peer != null) {
            runCatching {
                peer.removeAttribute("srcdoc")
                peer.setAttribute("src", url)
            }
        }
    }

    actual fun setIframeSrcdoc(element: WebDomElement, html: String, baseUrl: String?) {
        val peer: dynamic = element.nativeBrowserPeer
        if (peer != null) {
            runCatching {
                peer.setAttribute("srcdoc", html)
            }
        }
        val ctx = frameContexts[element] ?: return
        ctx.document.title = titleTagRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val idAttrRegex = Regex("<([a-zA-Z0-9:_-]+)\\b[^>]*?\\bid\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
        for (match in idAttrRegex.findAll(html)) {
            val tag = match.groupValues[1]
            val id = match.groupValues[2]
            val inner = match.groupValues[3].replace(Regex("<[^>]+>"), "").trim()
            val el = ctx.document.createElement(tag)
            el.setAttribute("id", id)
            el.textContent = inner
            ctx.document.body.appendChild(el)
        }
        for (match in scriptTagRegex.findAll(html)) {
            val code = match.groupValues[1].trim()
            if (code.isNotEmpty()) {
                evaluateScriptInContext(element, ctx, code, null)
            }
        }
    }

    actual fun reloadIframe(element: WebDomElement, fallbackUrl: String?, fallbackHtml: String?) {
        val peer: dynamic = element.nativeBrowserPeer
        if (peer != null) {
            val cw: dynamic = peer.contentWindow
            if (cw != null && cw.location != null) {
                runCatching { cw.location.reload() }
                return
            }
        }
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
        val ctx = ensureFrameContext(element, onMessage, onConsole, onError, commInvoker)
        evaluateScriptInContext(element, ctx, script, callback)
    }

    private fun evaluateScriptInContext(
        element: WebDomElement,
        ctx: dynamic,
        script: String,
        callback: ((String?) -> Unit)?
    ) {
        val peer: dynamic = element.nativeBrowserPeer
        if (peer != null) {
            val cw: dynamic = peer.contentWindow
            if (cw != null && js("typeof cw.eval === 'function'") as Boolean) {
                try {
                    cw.genericUi = ctx.genericUi
                    val raw = cw.eval(script)
                    callback?.invoke(if (raw == undefined || raw == null) null else raw.toString())
                    return
                } catch (t: Throwable) {
                    val msg = t.message ?: t.toString()
                    val errCb = ctx.__onError
                    if (errCb != null) errCb(msg)
                    callback?.invoke(null)
                    return
                }
            }
        }

        try {
            val runner: dynamic = js(
                """
                (function(ctxObj, code) {
                    var window = ctxObj;
                    var globalThis = ctxObj;
                    var self = ctxObj;
                    var document = ctxObj.document;
                    var console = ctxObj.console;
                    var genericUi = ctxObj.genericUi;
                    var postMessage = ctxObj.postMessage;
                    return eval(code);
                })
                """
            )
            val result = runner(ctx, script)
            val formatted = if (result == undefined || result == null) null else result.toString()
            callback?.invoke(formatted)
        } catch (t: Throwable) {
            val errMsg = t.message ?: t.toString()
            val errCb = ctx.__onError
            if (errCb != null) errCb(errMsg)
            callback?.invoke(null)
        }
    }

    actual fun postMessageToIframe(element: WebDomElement, message: String, onMessage: (String) -> Unit) {
        val peer: dynamic = element.nativeBrowserPeer
        if (peer != null) {
            val cw: dynamic = peer.contentWindow
            if (cw != null && js("typeof cw.postMessage === 'function'") as Boolean) {
                runCatching { cw.postMessage(message, "*") }
            }
        }
        val ctx = frameContexts[element]
        if (ctx != null) {
            val listeners: dynamic = ctx.__messageListeners
            val len = (listeners.length as? Int) ?: 0
            if (len > 0) {
                val ev: dynamic = js("({})")
                ev.data = message
                ev.type = "message"
                for (i in 0 until len) {
                    runCatching { listeners[i](ev) }
                }
                return
            }
        }
        onMessage(message)
    }

    actual fun callGuestFunctionInIframe(element: WebDomElement, name: String, payloadJson: String): String? {
        val ctx = frameContexts[element] ?: return null
        val fn: dynamic = ctx.__guestFunctions[name] ?: ctx[name]
        if (fn != undefined && fn != null && js("typeof fn === 'function'") as Boolean) {
            val res = fn(payloadJson)
            return if (res == undefined || res == null) null else res.toString()
        }
        return null
    }
}
