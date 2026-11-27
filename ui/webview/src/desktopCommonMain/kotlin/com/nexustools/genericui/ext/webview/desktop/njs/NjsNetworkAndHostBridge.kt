@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebHtml5Parser
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.genericui.ext.webview.desktop.engine.WebResourceResult
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import com.nexustools.njs.compiler.RuntimeCompiler
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.JSError
import com.nexustools.njs.runtime.JSPromise
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Scope
import com.nexustools.njs.runtime.Uint8Array
import com.nexustools.njs.runtime.Undefined
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt

/**
 * Installs Web networking, data/form container types, GenericUI host<->guest bridge, console,
 * and `<iframe>` sub-document bindings onto an NJS worker runtime:
 * - `Headers`, `Blob`, `File`, `FormData`, `Request`, `Response`, `fetch(input, init?)`
 * - `XMLHttpRequest`
 * - `WebSocket` (backed by [WebSocketNativeImpl] with thread-safe callback marshalling)
 * - `console` (`debug`, `info`, `log`, `warn`, `error`, `assert`)
 * - `window.postMessage` & `window.genericUi` (`registerCommunicationFunction`,
 *   `unregisterCommunicationFunction`, `callCommunicationFunction`, `postMessage`, `onMessage`, `capabilities`)
 * - `<iframe>` `srcdoc` / `src` sub-document & sub-window instantiation
 */
class NjsNetworkAndHostBridge(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val scheduler: NjsEventScheduler,
    private val events: NjsDomEventSystem,
    private val registry: NjsDomNodeRegistry,
    private val bomBridge: NjsBomApisBridge,
    private val windowObj: GenericObject,
    private val onConsoleLog: (String, String) -> Unit,
    private val onPostMessageToHost: (String) -> Unit,
    private val onHostBridgeCall: (String, String) -> String
) {
    private val wsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsQueueLock = AtomicInt(0)
    private val pendingWsActions = ArrayList<() -> Unit>()
    private val activeSockets = ArrayList<WebSocketNativeImpl>()

    private val guestBridgeFunctions = LinkedHashMap<String, BaseFunction>()
    private lateinit var genericUiObj: GenericObject
    private lateinit var headersCtor: AbstractFunction
    private lateinit var blobCtor: AbstractFunction
    private lateinit var fileCtor: AbstractFunction
    private lateinit var responseCtor: AbstractFunction
    private lateinit var requestCtor: AbstractFunction
    private lateinit var formDataCtor: AbstractFunction

    private val iframeStates = LinkedHashMap<WebDomElement, Pair<GenericObject?, GenericObject?>>()

    fun install() {
        installConsole()
        installHeadersBlobFileFormData()
        installRequestResponseFetch()
        installXmlHttpRequest()
        installWebSocket()
        installGenericUiBridgeAndPostMessage()
        installIframeBindings()
    }

    fun clearTransientState() {
        guestBridgeFunctions.clear()
        iframeStates.clear()
        closeAllSockets()
    }

    fun closeAllSockets() {
        val sockets = activeSockets.toList()
        activeSockets.clear()
        for (ws in sockets) {
            try {
                ws.dispose()
            } catch (t: Throwable) {
                onConsoleLog("warn", "WebSocket dispose error: ${t.message}")
            }
        }
        withWsLock { pendingWsActions.clear() }
    }

    fun dispose() {
        closeAllSockets()
        wsScope.cancel()
    }

    fun pollPendingAsyncNetworkCallbacks(): Boolean {
        val batch = withWsLock {
            if (pendingWsActions.isEmpty()) return false
            val copy = pendingWsActions.toList()
            pendingWsActions.clear()
            copy
        }
        for (act in batch) {
            try {
                act()
            } catch (t: Throwable) {
                onConsoleLog("error", "Network async callback error: ${t.message}")
            }
        }
        scheduler.drainMicrotasks()
        return true
    }

    fun hasActiveWebSockets(): Boolean = activeSockets.isNotEmpty()

    fun hasPendingAsyncCallbacks(): Boolean = withWsLock { pendingWsActions.isNotEmpty() }

    fun invokeGuestCommunicationFunction(name: String, payloadJson: String): Pair<Boolean, String> {
        val fn = guestBridgeFunctions[name] ?: return false to ""
        val result = fn.call(genericUiObj, jsGlobal.wrap(payloadJson))
        return true to (if (result === Undefined || result === Null) "" else result.toString())
    }

    fun deliverHostMessageToGuest(message: String) {
        val handler = genericUiObj.get("onMessage", Scopable.OR_NULL) as? BaseFunction
        handler?.call(genericUiObj, jsGlobal.wrap(message))
        events.dispatchSimpleEvent(
            windowObj,
            "message",
            bubbles = false,
            cancelable = false,
            extraProps = { ev ->
                ev.set("data", jsGlobal.wrap(message))
                ev.set("origin", jsGlobal.wrap("genericui://host"))
                ev.set("source", windowObj)
            }
        )
    }

    fun loadOutstandingIframes() {
        fun visit(el: WebDomElement) {
            if (el.lowerTagName == "iframe" && !iframeStates.containsKey(el)) {
                val jsObj = registry.wrapNode(el)
                reloadIframeElement(el, jsObj)
            }
            for (c in el.children.toList()) {
                visit(c)
            }
        }
        engine.document.documentElement?.let { visit(it) }
    }

    private fun installConsole() {
        val consoleObj = GenericObject(jsGlobal)
        fun emit(level: String, args: Array<out BaseObject>): BaseObject {
            val formatted = args.joinToString(" ") { formatConsoleArg(it) }
            onConsoleLog(level, formatted)
            return Undefined
        }
        for (lvl in listOf("debug", "info", "log", "warn", "error")) {
            consoleObj.setHidden(lvl, fn(lvl) { _, params -> emit(lvl, params) })
        }
        consoleObj.setHidden("assert", fn("assert") { _, params ->
            val ok = params.getOrNull(0)?.toBool() == true
            if (!ok) {
                val rest = if (params.size > 1) params.copyOfRange(1, params.size) else arrayOf(jsGlobal.wrap("Assertion failed"))
                emit("error", rest)
            }
            Undefined
        })
        windowObj.setHidden("console", consoleObj)
        jsGlobal.setHidden("console", consoleObj)
    }

    private fun formatConsoleArg(v: BaseObject): String {
        if (v === Undefined) return "undefined"
        if (v === Null) return "null"
        if (v is com.nexustools.njs.runtime.JSString.Instance) return v.string
        if (v is com.nexustools.njs.runtime.JSNumber.Instance || v is com.nexustools.njs.runtime.JSBoolean.Instance) {
            return v.toString()
        }
        if (v is GenericObject) {
            val jsonObj = jsGlobal.get("JSON", Scopable.OR_NULL)
            val stringify = jsonObj?.get("stringify", Scopable.OR_NULL) as? BaseFunction
            if (stringify != null) {
                return try {
                    stringify.call(jsonObj, v).toString()
                } catch (t: Throwable) {
                    "{\"error\":\"stringify_failed\",\"fallback\":\"${v}\"}"
                }
            }
        }
        return v.toString()
    }

    private fun installHeadersBlobFileFormData() {
        headersCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                return createHeadersObject(params.getOrNull(0))
            }
            override fun name(): String = "Headers"
        }

        blobCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val bytes = collectBlobBytes(params.getOrNull(0))
                val mime = params.getOrNull(1)?.get("type", Scopable.OR_NULL)
                    ?.takeIf { it !== Undefined && it !== Null }?.toString()?.lowercase().orEmpty()
                return createBlobObject(this, bytes, mime)
            }
            override fun name(): String = "Blob"
        }

        fileCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val bytes = collectBlobBytes(params.getOrNull(0))
                val fileName = params.getOrNull(1)?.toString().orEmpty()
                val opts = params.getOrNull(2)
                val mime = opts?.get("type", Scopable.OR_NULL)
                    ?.takeIf { it !== Undefined && it !== Null }?.toString()?.lowercase().orEmpty()
                val lastMod = opts?.get("lastModified", Scopable.OR_NULL)
                    ?.takeIf { it !== Undefined && it !== Null }?.toDouble() ?: 0.0
                return createBlobObject(this, bytes, mime).apply {
                    setHidden("name", jsGlobal.wrap(fileName))
                    setHidden("lastModified", jsGlobal.wrap(lastMod))
                }
            }
            override fun name(): String = "File"
        }

        formDataCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val entries = ArrayList<Pair<String, BaseObject>>()
                val formArg = params.getOrNull(0)?.let { registry.unwrapElement(it) }
                if (formArg != null) {
                    collectFormControls(formArg, entries)
                }

                val inst = GenericObject(this, jsGlobal)
                inst.setHidden("append", fn("append") { _, a ->
                    val k = a.getOrNull(0)?.toString().orEmpty()
                    val v = a.getOrNull(1) ?: Undefined
                    entries.add(k to v)
                    Undefined
                })
                inst.setHidden("set", fn("set") { _, a ->
                    val k = a.getOrNull(0)?.toString().orEmpty()
                    val v = a.getOrNull(1) ?: Undefined
                    val idx = entries.indexOfFirst { it.first == k }
                    entries.removeAll { it.first == k }
                    if (idx >= 0) entries.add(idx.coerceAtMost(entries.size), k to v)
                    else entries.add(k to v)
                    Undefined
                })
                inst.setHidden("get", fn("get") { _, a ->
                    val k = a.getOrNull(0)?.toString().orEmpty()
                    entries.firstOrNull { it.first == k }?.second ?: Null
                })
                inst.setHidden("getAll", fn("getAll") { _, a ->
                    val k = a.getOrNull(0)?.toString().orEmpty()
                    val arr = entries.filter { it.first == k }.map { it.second as BaseObject? }.toTypedArray()
                    GenericArray(jsGlobal, jsGlobal.Array, arr)
                })
                inst.setHidden("has", fn("has") { _, a ->
                    val k = a.getOrNull(0)?.toString().orEmpty()
                    jsGlobal.wrap(entries.any { it.first == k })
                })
                inst.setHidden("delete", fn("delete") { _, a ->
                    val k = a.getOrNull(0)?.toString().orEmpty()
                    entries.removeAll { it.first == k }
                    Undefined
                })
                inst.setHidden("forEach", fn("forEach") { _, a ->
                    val cb = a.getOrNull(0) as? BaseFunction ?: return@fn Undefined
                    val thisArg = a.getOrNull(1) ?: Undefined
                    for ((k, v) in entries.toList()) {
                        cb.call(thisArg, v, jsGlobal.wrap(k), inst)
                    }
                    Undefined
                })
                inst.setHidden("keys", fn("keys") { _, _ ->
                    GenericArray(jsGlobal, jsGlobal.Array, entries.map { jsGlobal.wrap(it.first) as BaseObject? }.toTypedArray())
                })
                inst.setHidden("values", fn("values") { _, _ ->
                    GenericArray(jsGlobal, jsGlobal.Array, entries.map { it.second as BaseObject? }.toTypedArray())
                })
                inst.setHidden("entries", fn("entries") { _, _ ->
                    GenericArray(jsGlobal, jsGlobal.Array, entries.map {
                        GenericArray(jsGlobal, jsGlobal.Array, arrayOf(jsGlobal.wrap(it.first), it.second)) as BaseObject?
                    }.toTypedArray())
                })
                return inst
            }
            override fun name(): String = "FormData"
        }

        windowObj.setHidden("Headers", headersCtor)
        windowObj.setHidden("Blob", blobCtor)
        windowObj.setHidden("File", fileCtor)
        windowObj.setHidden("FormData", formDataCtor)
        jsGlobal.setHidden("Headers", headersCtor)
        jsGlobal.setHidden("Blob", blobCtor)
        jsGlobal.setHidden("File", fileCtor)
        jsGlobal.setHidden("FormData", formDataCtor)
    }

    private fun collectFormControls(root: WebDomElement, out: MutableList<Pair<String, BaseObject>>) {
        fun visit(el: WebDomElement) {
            val tag = el.lowerTagName
            if (!el.hasAttribute("disabled")) {
                val name = el.getAttribute("name")?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    when (tag) {
                        "input" -> {
                            val t = (el.getAttribute("type") ?: "text").trim().lowercase()
                            when (t) {
                                "checkbox", "radio" -> {
                                    if (el.checked) {
                                        val valStr = el.getAttribute("value") ?: "on"
                                        out.add(name to jsGlobal.wrap(valStr))
                                    }
                                }
                                "submit", "button", "reset", "image", "file" -> Unit
                                else -> out.add(name to jsGlobal.wrap(el.value))
                            }
                        }
                        "textarea" -> out.add(name to jsGlobal.wrap(el.value))
                        "select" -> {
                            val opts = el.collectOptions()
                            if (el.hasAttribute("multiple")) {
                                for (opt in opts) {
                                    if (opt.checked && !opt.hasAttribute("disabled")) {
                                        out.add(name to jsGlobal.wrap(opt.value))
                                    }
                                }
                            } else {
                                val sel = opts.firstOrNull { it.checked } ?: opts.firstOrNull()
                                if (sel != null) {
                                    out.add(name to jsGlobal.wrap(sel.value))
                                }
                            }
                        }
                    }
                }
            }
            for (c in el.children) visit(c)
        }
        visit(root)
    }

    private fun createHeadersObject(initArg: BaseObject?): GenericObject {
        val map = LinkedHashMap<String, String>()
        if (initArg != null && initArg !== Undefined && initArg !== Null) {
            val rawMap = initArg.get("__headerMap", Scopable.OR_NULL)
            if (rawMap != null && rawMap !== Undefined && rawMap is GenericObject) {
                for (k in rawMap.keys()) {
                    map[k.lowercase()] = rawMap.get(k).toString()
                }
            } else if (initArg is GenericArray) {
                for (i in 0 until initArg.length()) {
                    val pair = initArg.get(i)
                    val k = pair.get(0).toString().lowercase()
                    val v = pair.get(1).toString()
                    map[k] = map[k]?.let { "$it, $v" } ?: v
                }
            } else if (initArg is GenericObject) {
                for (k in initArg.keys()) {
                    map[k.lowercase()] = initArg.get(k).toString()
                }
            }
        }
        val inst = GenericObject(headersCtor, jsGlobal)
        val shadow = GenericObject(jsGlobal)
        map.forEach { (k, v) -> shadow.set(k, jsGlobal.wrap(v)) }
        inst.setHidden("__headerMap", shadow)

        inst.setHidden("get", fn("get") { _, a ->
            val k = a.getOrNull(0)?.toString()?.lowercase().orEmpty()
            map[k]?.let { jsGlobal.wrap(it) } ?: Null
        })
        inst.setHidden("has", fn("has") { _, a ->
            val k = a.getOrNull(0)?.toString()?.lowercase().orEmpty()
            jsGlobal.wrap(map.containsKey(k))
        })
        inst.setHidden("set", fn("set") { _, a ->
            val k = a.getOrNull(0)?.toString()?.lowercase().orEmpty()
            val v = a.getOrNull(1)?.toString().orEmpty()
            map[k] = v
            shadow.set(k, jsGlobal.wrap(v))
            Undefined
        })
        inst.setHidden("append", fn("append") { _, a ->
            val k = a.getOrNull(0)?.toString()?.lowercase().orEmpty()
            val v = a.getOrNull(1)?.toString().orEmpty()
            val combined = map[k]?.let { "$it, $v" } ?: v
            map[k] = combined
            shadow.set(k, jsGlobal.wrap(combined))
            Undefined
        })
        inst.setHidden("delete", fn("delete") { _, a ->
            val k = a.getOrNull(0)?.toString()?.lowercase().orEmpty()
            map.remove(k)
            shadow.delete(k)
            Undefined
        })
        inst.setHidden("forEach", fn("forEach") { _, a ->
            val cb = a.getOrNull(0) as? BaseFunction ?: return@fn Undefined
            val thisArg = a.getOrNull(1) ?: Undefined
            for ((k, v) in map.toList()) cb.call(thisArg, jsGlobal.wrap(v), jsGlobal.wrap(k), inst)
            Undefined
        })
        inst.setHidden("entries", fn("entries") { _, _ ->
            GenericArray(jsGlobal, jsGlobal.Array, map.entries.map {
                GenericArray(jsGlobal, jsGlobal.Array, arrayOf(jsGlobal.wrap(it.key), jsGlobal.wrap(it.value))) as BaseObject?
            }.toTypedArray())
        })
        return inst
    }

    private fun extractHeadersMap(headersObj: BaseObject?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (headersObj == null || headersObj === Undefined || headersObj === Null) return out
        val shadow = headersObj.get("__headerMap", Scopable.OR_NULL) as? GenericObject
        if (shadow != null) {
            for (k in shadow.keys()) out[k] = shadow.get(k).toString()
            return out
        }
        if (headersObj is GenericObject) {
            for (k in headersObj.keys()) out[k] = headersObj.get(k).toString()
        }
        return out
    }

    private fun collectBlobBytes(partsArg: BaseObject?): ByteArray {
        if (partsArg == null || partsArg === Undefined || partsArg === Null) return ByteArray(0)
        val len = partsArg.get("length", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toInt()
            ?: return ByteArray(0)
        val out = ArrayList<Byte>()
        for (i in 0 until len) {
            val part = partsArg.get(i)
            val partBytes = if (part.get("__bytes", Scopable.OR_NULL) != null || part is Uint8Array.Instance) {
                bomBridge.extractByteArrayFromJs(part)
            } else {
                part.toString().encodeToByteArray()
            }
            for (b in partBytes) out.add(b)
        }
        return out.toByteArray()
    }

    private fun createBlobObject(ctor: AbstractFunction, bytes: ByteArray, mimeType: String): GenericObject {
        val rawUint8Ctor = jsGlobal.get("Uint8Array", Scopable.OR_NULL) as? Uint8Array
        val inst = GenericObject(ctor, jsGlobal)
        inst.setHidden("__bytes", Uint8Array.Instance(jsGlobal, rawUint8Ctor, bytes))
        inst.setHidden("size", jsGlobal.wrap(bytes.size.toDouble()))
        inst.setHidden("type", jsGlobal.wrap(mimeType))
        inst.setHidden("text", fn("text") { _, _ -> resolvedPromise(jsGlobal.wrap(bytes.decodeToString())) })
        inst.setHidden("arrayBuffer", fn("arrayBuffer") { _, _ -> resolvedPromise(createArrayBufferFromBytes(bytes)) })
        inst.setHidden("slice", fn("slice") { _, a ->
            val start = (a.getOrNull(0)?.toInt() ?: 0).let { if (it < 0) bytes.size + it else it }.coerceIn(0, bytes.size)
            val end = (a.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: bytes.size)
                .let { if (it < 0) bytes.size + it else it }.coerceIn(start, bytes.size)
            val contentType = a.getOrNull(2)?.takeIf { it !== Undefined }?.toString()?.lowercase() ?: mimeType
            createBlobObject(blobCtor, bytes.copyOfRange(start, end), contentType)
        })
        return inst
    }

    private fun createArrayBufferFromBytes(bytes: ByteArray): BaseObject {
        val abCtor = jsGlobal.get("ArrayBuffer", Scopable.OR_NULL) as? BaseFunction
        val ab = abCtor?.construct(jsGlobal.wrap(bytes.size.toDouble()))
        if (ab != null) {
            val u8 = ab.get("__bytes", Scopable.OR_NULL) as? Uint8Array.Instance
            bytes.copyInto(u8?.arrayStorage ?: ByteArray(0))
            return ab
        }
        val rawUint8Ctor = jsGlobal.get("Uint8Array", Scopable.OR_NULL) as? Uint8Array
        return GenericObject(jsGlobal).apply {
            setHidden("byteLength", jsGlobal.wrap(bytes.size.toDouble()))
            setHidden("__bytes", Uint8Array.Instance(jsGlobal, rawUint8Ctor, bytes))
        }
    }

    private fun installRequestResponseFetch() {
        requestCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val input = params.getOrNull(0)
                val init = params.getOrNull(1)
                val baseUrl = if (input is GenericObject && input.get("url", Scopable.OR_NULL) != null) {
                    input.get("url").toString()
                } else {
                    input?.toString().orEmpty()
                }
                val method = init?.get("method", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toString()?.uppercase()
                    ?: input?.get("method", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toString()?.uppercase()
                    ?: "GET"
                val hdrs = createHeadersObject(init?.get("headers", Scopable.OR_NULL) ?: input?.get("headers", Scopable.OR_NULL))
                val inst = GenericObject(this, jsGlobal)
                inst.setHidden("url", jsGlobal.wrap(engine.resourceLoader.resolveUrl(baseUrl, engine.document.url)))
                inst.setHidden("method", jsGlobal.wrap(method))
                inst.setHidden("headers", hdrs)
                inst.setHidden("signal", init?.get("signal", Scopable.OR_NULL) ?: Null)
                inst.setHidden("body", init?.get("body", Scopable.OR_NULL) ?: Null)
                return inst
            }
            override fun name(): String = "Request"
        }

        responseCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val rawBody = params.getOrNull(0)
                val bodyBytes = when {
                    rawBody == null || rawBody === Undefined || rawBody === Null -> ByteArray(0)
                    rawBody.get("__bytes", Scopable.OR_NULL) != null || rawBody is Uint8Array.Instance -> bomBridge.extractByteArrayFromJs(rawBody)
                    else -> rawBody.toString().encodeToByteArray()
                }
                val init = params.getOrNull(1)
                val status = init?.get("status", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toInt() ?: 200
                val statusText = init?.get("statusText", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toString()
                    ?: if (status in 200..299) "OK" else ""
                val headersObj = createHeadersObject(init?.get("headers", Scopable.OR_NULL))
                return buildResponseObject(engine.document.url, status, statusText, headersObj, bodyBytes)
            }
            override fun name(): String = "Response"
        }
        responseCtor.setHidden("json", fn("json") { _, a ->
            val json = (jsGlobal.get("JSON")?.get("stringify") as? BaseFunction)
                ?.call(jsGlobal.get("JSON"), a.getOrNull(0) ?: Null)?.toString() ?: "null"
            val headers = createHeadersObject(GenericObject(jsGlobal).apply { set("content-type", jsGlobal.wrap("application/json")) })
            buildResponseObject(engine.document.url, 200, "OK", headers, json.encodeToByteArray())
        })

        val fetchFn = fn("fetch") { _, params ->
            val input = params.getOrNull(0)
            val init = params.getOrNull(1)
            val reqObj = requestCtor.construct(input ?: jsGlobal.wrap(""), init ?: Undefined)
            val targetUrl = reqObj.get("url").toString()
            val method = reqObj.get("method").toString()
            val reqHeaders = extractHeadersMap(reqObj.get("headers", Scopable.OR_NULL))
            val signal = reqObj.get("signal", Scopable.OR_NULL)
            val reqBodyObj = reqObj.get("body", Scopable.OR_NULL)
            val reqBytes = if (reqBodyObj != null && reqBodyObj !== Undefined && reqBodyObj !== Null) {
                if (reqBodyObj.get("__bytes", Scopable.OR_NULL) != null || reqBodyObj is Uint8Array.Instance) {
                    bomBridge.extractByteArrayFromJs(reqBodyObj)
                } else {
                    reqBodyObj.toString().encodeToByteArray()
                }
            } else null

            JSPromise(jsGlobal).construct(fn("fetchExecutor") { _, execArgs ->
                val resolve = execArgs[0] as BaseFunction
                val reject = execArgs[1] as BaseFunction
                if (signal != null && signal !== Null && signal !== Undefined && signal.get("aborted").toBool()) {
                    val abortErr = (jsGlobal.get("DOMException", Scopable.OR_NULL) as? BaseFunction)
                        ?.construct(jsGlobal.wrap("The user aborted a request."), jsGlobal.wrap("AbortError"))
                        ?: jsGlobal.wrap("AbortError")
                    scheduler.queueMicrotask { reject.call(Undefined, abortErr) }
                    return@fn Undefined
                }
                scheduler.queueMicrotask {
                    if (signal != null && signal !== Null && signal !== Undefined && signal.get("aborted").toBool()) {
                        val abortErr = (jsGlobal.get("DOMException", Scopable.OR_NULL) as? BaseFunction)
                            ?.construct(jsGlobal.wrap("The user aborted a request."), jsGlobal.wrap("AbortError"))
                            ?: jsGlobal.wrap("AbortError")
                        reject.call(Undefined, abortErr)
                        return@queueMicrotask
                    }
                    try {
                        val res = engine.resourceLoader.loadSync(
                            rawUrl = targetUrl,
                            baseUrl = engine.document.url,
                            method = method,
                            headers = reqHeaders,
                            bodyBytes = reqBytes
                        )
                        val hdrs = createHeadersObject(GenericObject(jsGlobal).apply {
                            for ((k, v) in res.headers) set(k, jsGlobal.wrap(v))
                            if (!res.headers.containsKey("content-type")) {
                                set("content-type", jsGlobal.wrap(res.mimeType))
                            }
                        })
                        resolve.call(Undefined, buildResponseObject(res.url, res.status, res.statusText, hdrs, res.bytes))
                    } catch (t: Throwable) {
                        val err = JSError.Instance(
                            jsGlobal.String!!, jsGlobal.Error, jsGlobal.Symbol.iteratorSymbol, jsGlobal.Number!!,
                            "TypeError", t.message ?: "Network request failed", t.message ?: "TypeError"
                        )
                        reject.call(Undefined, err)
                    }
                }
                Undefined
            })
        }

        windowObj.setHidden("Request", requestCtor)
        windowObj.setHidden("Response", responseCtor)
        windowObj.setHidden("fetch", fetchFn)
        jsGlobal.setHidden("Request", requestCtor)
        jsGlobal.setHidden("Response", responseCtor)
        jsGlobal.setHidden("fetch", fetchFn)
    }

    private fun buildResponseObject(
        url: String,
        status: Int,
        statusText: String,
        headersObj: GenericObject,
        bodyBytes: ByteArray
    ): GenericObject {
        val resp = GenericObject(responseCtor, jsGlobal)
        var consumed = false
        resp.setHidden("url", jsGlobal.wrap(url))
        resp.setHidden("status", jsGlobal.wrap(status.toDouble()))
        resp.setHidden("statusText", jsGlobal.wrap(statusText))
        resp.setHidden("ok", jsGlobal.wrap(status in 200..299))
        resp.setHidden("headers", headersObj)
        resp.setHidden("type", jsGlobal.wrap("basic"))
        resp.setHidden("redirected", jsGlobal.wrap(false))
        resp.defineProperty("bodyUsed", fn("get_bodyUsed") { _, _ -> jsGlobal.wrap(consumed) }, jsGlobal.NOOP)

        resp.setHidden("text", fn("text") { _, _ ->
            consumed = true
            resolvedPromise(jsGlobal.wrap(bodyBytes.decodeToString()))
        })
        resp.setHidden("json", fn("json") { _, _ ->
            consumed = true
            val text = bodyBytes.decodeToString()
            val jsonMod = jsGlobal.get("JSON")
            val parseFn = jsonMod.get("parse") as? BaseFunction
            try {
                val parsed = parseFn?.call(jsonMod, jsGlobal.wrap(text)) ?: Null
                resolvedPromise(parsed)
            } catch (t: Throwable) {
                rejectedPromise(jsGlobal.wrap(t))
            }
        })
        resp.setHidden("arrayBuffer", fn("arrayBuffer") { _, _ ->
            consumed = true
            resolvedPromise(createArrayBufferFromBytes(bodyBytes))
        })
        resp.setHidden("blob", fn("blob") { _, _ ->
            consumed = true
            val mime = headersObj.get("get").let { (it as BaseFunction).call(headersObj, jsGlobal.wrap("content-type")) }
                .takeIf { it !== Null && it !== Undefined }?.toString().orEmpty()
            resolvedPromise(createBlobObject(blobCtor, bodyBytes, mime))
        })
        resp.setHidden("clone", fn("clone") { _, _ ->
            buildResponseObject(url, status, statusText, headersObj, bodyBytes.copyOf())
        })
        return resp
    }

    private fun installXmlHttpRequest() {
        val xhrCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val inst = GenericObject(this, jsGlobal)
                var method = "GET"
                var url = ""
                var asyncFlag = true
                var readyState = 0
                var status = 0
                var statusText = ""
                var responseUrl = ""
                var responseBytes = ByteArray(0)
                var responseHeaders = emptyMap<String, String>()
                val requestHeaders = LinkedHashMap<String, String>()
                var aborted = false

                events.attachEventTargetMethods(inst, inst)
                for (evtName in listOf("onreadystatechange", "onload", "onerror", "onabort", "onloadend")) {
                    inst.setHidden(evtName, Null)
                }

                fun updateReadyState(next: Int) {
                    readyState = next
                    events.dispatchSimpleEvent(inst, "readystatechange", bubbles = false, cancelable = false)
                }

                inst.defineProperty("readyState", fn("get_rs") { _, _ -> jsGlobal.wrap(readyState.toDouble()) }, jsGlobal.NOOP)
                inst.defineProperty("status", fn("get_st") { _, _ -> jsGlobal.wrap(status.toDouble()) }, jsGlobal.NOOP)
                inst.defineProperty("statusText", fn("get_stt") { _, _ -> jsGlobal.wrap(statusText) }, jsGlobal.NOOP)
                inst.defineProperty("responseURL", fn("get_rurl") { _, _ -> jsGlobal.wrap(responseUrl) }, jsGlobal.NOOP)
                inst.defineProperty("responseText", fn("get_rt") { _, _ -> jsGlobal.wrap(responseBytes.decodeToString()) }, jsGlobal.NOOP)
                inst.defineProperty("response", fn("get_resp") { _, _ ->
                    val rType = inst.get("responseType", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toString()
                    when (rType) {
                        "arraybuffer" -> createArrayBufferFromBytes(responseBytes)
                        "json" -> {
                            val jsonMod = jsGlobal.get("JSON")
                            val parseFn = jsonMod.get("parse") as? BaseFunction
                            try {
                                parseFn?.call(jsonMod, jsGlobal.wrap(responseBytes.decodeToString())) ?: Null
                            } catch (t: Throwable) {
                                onConsoleLog("warn", "XHR JSON parse failed: ${t.message}")
                                Null
                            }
                        }
                        else -> jsGlobal.wrap(responseBytes.decodeToString())
                    }
                }, jsGlobal.NOOP)
                inst.setHidden("responseType", jsGlobal.wrap(""))

                inst.setHidden("open", fn("open") { _, p ->
                    method = p.getOrNull(0)?.toString()?.uppercase() ?: "GET"
                    url = p.getOrNull(1)?.toString().orEmpty()
                    asyncFlag = p.getOrNull(2)?.takeIf { it !== Undefined }?.toBool() ?: true
                    aborted = false
                    updateReadyState(1)
                    Undefined
                })
                inst.setHidden("setRequestHeader", fn("setRequestHeader") { _, p ->
                    val k = p.getOrNull(0)?.toString()?.lowercase().orEmpty()
                    val v = p.getOrNull(1)?.toString().orEmpty()
                    if (k.isNotEmpty()) requestHeaders[k] = v
                    Undefined
                })
                inst.setHidden("abort", fn("abort") { _, _ ->
                    aborted = true
                    readyState = 0
                    events.dispatchSimpleEvent(inst, "abort", bubbles = false, cancelable = false)
                    events.dispatchSimpleEvent(inst, "loadend", bubbles = false, cancelable = false)
                    Undefined
                })
                inst.setHidden("getResponseHeader", fn("getResponseHeader") { _, p ->
                    val k = p.getOrNull(0)?.toString()?.lowercase().orEmpty()
                    responseHeaders[k]?.let { jsGlobal.wrap(it) } ?: Null
                })
                inst.setHidden("getAllResponseHeaders", fn("getAllResponseHeaders") { _, _ ->
                    jsGlobal.wrap(responseHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" })
                })
                inst.setHidden("send", fn("send") { _, p ->
                    val bodyArg = p.getOrNull(0)
                    val bodyBytes = if (bodyArg != null && bodyArg !== Undefined && bodyArg !== Null) {
                        if (bodyArg.get("__bytes", Scopable.OR_NULL) != null || bodyArg is Uint8Array.Instance) {
                            bomBridge.extractByteArrayFromJs(bodyArg)
                        } else {
                            bodyArg.toString().encodeToByteArray()
                        }
                    } else null

                    fun executeLoad() {
                        if (aborted) return
                        try {
                            val res: WebResourceResult = engine.resourceLoader.loadSync(
                                rawUrl = url,
                                baseUrl = engine.document.url,
                                method = method,
                                headers = requestHeaders,
                                bodyBytes = bodyBytes
                            )
                            if (aborted) return
                            responseUrl = res.url
                            responseHeaders = res.headers
                            status = res.status
                            statusText = res.statusText
                            responseBytes = res.bytes
                            updateReadyState(2)
                            updateReadyState(3)
                            updateReadyState(4)
                            events.dispatchSimpleEvent(inst, "load", bubbles = false, cancelable = false)
                            events.dispatchSimpleEvent(inst, "loadend", bubbles = false, cancelable = false)
                        } catch (t: Throwable) {
                            onConsoleLog("error", "XHR request failed ($url): ${t.message}")
                            status = 0
                            updateReadyState(4)
                            events.dispatchSimpleEvent(inst, "error", bubbles = false, cancelable = false)
                            events.dispatchSimpleEvent(inst, "loadend", bubbles = false, cancelable = false)
                        }
                    }

                    if (asyncFlag) {
                        scheduler.queueMicrotask { executeLoad() }
                    } else {
                        executeLoad()
                    }
                    Undefined
                })
                return inst
            }
            override fun name(): String = "XMLHttpRequest"
        }

        windowObj.setHidden("XMLHttpRequest", xhrCtor)
        jsGlobal.setHidden("XMLHttpRequest", xhrCtor)
    }

    private fun installWebSocket() {
        val wsCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                if (!engine.capabilities.webSockets) {
                    val err = (jsGlobal.get("DOMException", Scopable.OR_NULL) as? BaseFunction)
                        ?.construct(jsGlobal.wrap("WebSocket is disabled by WebView capabilities"), jsGlobal.wrap("SecurityError"))
                        ?: jsGlobal.wrap("SecurityError: WebSocket disabled")
                    throw JSError.Thrown(err)
                }
                val wsUrl = params.getOrNull(0)?.toString().orEmpty()
                val nativeWs = engine.hostFactory.createWebSocket()
                activeSockets.add(nativeWs)

                var state = 0 // CONNECTING
                val inst = GenericObject(this, jsGlobal)
                events.attachEventTargetMethods(inst, inst)
                inst.setHidden("url", jsGlobal.wrap(wsUrl))
                inst.setHidden("binaryType", jsGlobal.wrap("blob"))
                inst.setHidden("protocol", jsGlobal.wrap(""))
                inst.setHidden("extensions", jsGlobal.wrap(""))
                inst.setHidden("bufferedAmount", jsGlobal.wrap(0.0))
                for (h in listOf("onopen", "onmessage", "onerror", "onclose")) inst.setHidden(h, Null)
                inst.defineProperty("readyState", fn("get_readyState") { _, _ -> jsGlobal.wrap(state.toDouble()) }, jsGlobal.NOOP)

                nativeWs.setOnOpen {
                    enqueueWsAction {
                        state = 1
                        events.dispatchSimpleEvent(inst, "open", bubbles = false, cancelable = false)
                    }
                }
                nativeWs.setOnTextMessage { msg ->
                    enqueueWsAction {
                        events.dispatchSimpleEvent(
                            inst,
                            "message",
                            bubbles = false,
                            cancelable = false,
                            extraProps = { ev ->
                                ev.set("data", jsGlobal.wrap(msg))
                                ev.set("origin", jsGlobal.wrap(wsUrl))
                            }
                        )
                    }
                }
                nativeWs.setOnBinaryMessage { data ->
                    enqueueWsAction {
                        val bType = inst.get("binaryType", Scopable.OR_NULL)?.toString() ?: "blob"
                        val payloadObj = if (bType == "arraybuffer") {
                            createArrayBufferFromBytes(data)
                        } else {
                            createBlobObject(blobCtor, data, "application/octet-stream")
                        }
                        events.dispatchSimpleEvent(
                            inst,
                            "message",
                            bubbles = false,
                            cancelable = false,
                            extraProps = { ev ->
                                ev.set("data", payloadObj)
                                ev.set("origin", jsGlobal.wrap(wsUrl))
                            }
                        )
                    }
                }
                nativeWs.setOnError { err ->
                    enqueueWsAction {
                        onConsoleLog("error", "WebSocket error ($wsUrl): ${err.message}")
                        events.dispatchSimpleEvent(inst, "error", bubbles = false, cancelable = false)
                    }
                }
                nativeWs.setOnClose { code, reason ->
                    enqueueWsAction {
                        state = 3
                        activeSockets.remove(nativeWs)
                        events.dispatchSimpleEvent(
                            inst,
                            "close",
                            bubbles = false,
                            cancelable = false,
                            extraProps = { ev ->
                                ev.set("code", jsGlobal.wrap(code.toDouble()))
                                ev.set("reason", jsGlobal.wrap(reason))
                                ev.set("wasClean", jsGlobal.wrap(code == 1000))
                            }
                        )
                    }
                }

                inst.setHidden("send", fn("send") { _, args ->
                    val data = args.getOrNull(0) ?: return@fn Undefined
                    if (state != 1) return@fn Undefined
                    if (data.get("__bytes", Scopable.OR_NULL) != null || data is Uint8Array.Instance) {
                        val bytes = bomBridge.extractByteArrayFromJs(data)
                        wsScope.launch {
                            try {
                                nativeWs.sendBinary(bytes)
                            } catch (t: Throwable) {
                                enqueueWsAction {
                                    onConsoleLog("error", "WebSocket binary send failed: ${t.message}")
                                    events.dispatchSimpleEvent(inst, "error", bubbles = false, cancelable = false)
                                }
                            }
                        }
                    } else {
                        val text = data.toString()
                        wsScope.launch {
                            try {
                                nativeWs.sendText(text)
                            } catch (t: Throwable) {
                                enqueueWsAction {
                                    onConsoleLog("error", "WebSocket text send failed: ${t.message}")
                                    events.dispatchSimpleEvent(inst, "error", bubbles = false, cancelable = false)
                                }
                            }
                        }
                    }
                    Undefined
                })
                inst.setHidden("close", fn("close") { _, args ->
                    if (state >= 2) return@fn Undefined
                    state = 2
                    val code = args.getOrNull(0)?.takeIf { it !== Undefined && it !== Null }?.toInt() ?: 1000
                    val reason = args.getOrNull(1)?.takeIf { it !== Undefined && it !== Null }?.toString() ?: "Normal Closure"
                    wsScope.launch {
                        try {
                            nativeWs.close(code, reason)
                        } catch (t: Throwable) {
                            enqueueWsAction {
                                state = 3
                                activeSockets.remove(nativeWs)
                                onConsoleLog("warn", "WebSocket close error: ${t.message}")
                            }
                        }
                    }
                    Undefined
                })

                wsScope.launch {
                    try {
                        val connected = nativeWs.connect(wsUrl)
                        if (!connected) {
                            enqueueWsAction {
                                state = 3
                                activeSockets.remove(nativeWs)
                                events.dispatchSimpleEvent(inst, "error", bubbles = false, cancelable = false)
                                events.dispatchSimpleEvent(
                                    inst,
                                    "close",
                                    bubbles = false,
                                    cancelable = false,
                                    extraProps = { ev ->
                                        ev.set("code", jsGlobal.wrap(1006.0))
                                        ev.set("reason", jsGlobal.wrap("Connection failed"))
                                        ev.set("wasClean", jsGlobal.wrap(false))
                                    }
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        enqueueWsAction {
                            state = 3
                            activeSockets.remove(nativeWs)
                            onConsoleLog("error", "WebSocket connect error ($wsUrl): ${t.message}")
                            events.dispatchSimpleEvent(inst, "error", bubbles = false, cancelable = false)
                        }
                    }
                }
                return inst
            }
            override fun name(): String = "WebSocket"
        }

        val proto = wsCtor.prototype() as? GenericObject
        for ((k, v) in listOf("CONNECTING" to 0.0, "OPEN" to 1.0, "CLOSING" to 2.0, "CLOSED" to 3.0)) {
            wsCtor.setHidden(k, jsGlobal.wrap(v))
            proto?.setHidden(k, jsGlobal.wrap(v))
        }
        windowObj.setHidden("WebSocket", wsCtor)
        jsGlobal.setHidden("WebSocket", wsCtor)
    }

    private fun installGenericUiBridgeAndPostMessage() {
        val capsObj = GenericObject(jsGlobal).apply {
            set("engineName", jsGlobal.wrap("Alien+NJS"))
            set("canvas2d", jsGlobal.wrap(engine.capabilities.canvas2d))
            set("webgl", jsGlobal.wrap(engine.capabilities.webgl))
            set("media", jsGlobal.wrap(engine.capabilities.media))
            set("svg", jsGlobal.wrap(engine.capabilities.svg))
            set("webAudio", jsGlobal.wrap(engine.capabilities.webAudio))
            set("webSockets", jsGlobal.wrap(engine.capabilities.webSockets))
            set("storage", jsGlobal.wrap(engine.capabilities.storage))
            set("shadowDom", jsGlobal.wrap(engine.capabilities.shadowDom))
            set("iframes", jsGlobal.wrap(engine.capabilities.iframes))
            set("workers", jsGlobal.wrap(engine.capabilities.workers))
        }

        genericUiObj = GenericObject(jsGlobal)
        genericUiObj.setHidden("capabilities", capsObj)
        genericUiObj.setHidden("onMessage", Null)
        genericUiObj.setHidden("registerCommunicationFunction", fn("registerCommunicationFunction") { _, params ->
            val name = params.getOrNull(0)?.toString()?.trim().orEmpty()
            val callback = params.getOrNull(1) as? BaseFunction
            if (name.isNotEmpty() && callback != null) {
                guestBridgeFunctions[name] = callback
            }
            Undefined
        })
        genericUiObj.setHidden("unregisterCommunicationFunction", fn("unregisterCommunicationFunction") { _, params ->
            val name = params.getOrNull(0)?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) guestBridgeFunctions.remove(name)
            Undefined
        })
        genericUiObj.setHidden("postMessage", fn("postMessage") { _, params ->
            val msg = params.getOrNull(0)?.takeIf { it !== Undefined && it !== Null }?.toString().orEmpty()
            onPostMessageToHost(msg)
            Undefined
        })
        genericUiObj.setHidden("callCommunicationFunction", fn("callCommunicationFunction") { _, params ->
            val name = params.getOrNull(0)?.toString()?.trim().orEmpty()
            val payload = params.getOrNull(1)?.takeIf { it !== Undefined && it !== Null }?.toString().orEmpty()
            val cb = params.getOrNull(2) as? BaseFunction
            val resStr = if (name.isNotEmpty()) onHostBridgeCall(name, payload) else ""
            val wrapped = jsGlobal.wrap(resStr)
            if (cb != null) {
                cb.call(genericUiObj, wrapped)
            }
            wrapped
        })

        windowObj.setHidden("genericUi", genericUiObj)
        jsGlobal.setHidden("genericUi", genericUiObj)

        val postMessageFn = fn("postMessage") { _this, params ->
            val data = params.getOrNull(0) ?: Undefined
            val origin = parseUriPartsOrigin()
            scheduler.queueMicrotask {
                val targetWin = if (_this is GenericObject && _this !== Undefined && _this !== Null) _this else windowObj
                events.dispatchSimpleEvent(
                    targetWin,
                    "message",
                    bubbles = false,
                    cancelable = false,
                    extraProps = { ev ->
                        ev.set("data", data)
                        ev.set("origin", jsGlobal.wrap(origin))
                        ev.set("source", windowObj)
                    }
                )
            }
            Undefined
        }
        windowObj.setHidden("postMessage", postMessageFn)
        jsGlobal.setHidden("postMessage", postMessageFn)
    }

    private fun parseUriPartsOrigin(): String {
        val url = engine.document.url
        val scheme = url.substringBefore("://", "")
        if (scheme == "http" || scheme == "https") {
            val rest = url.substringAfter("://")
            val authority = rest.substringBefore('/')
            return "$scheme://$authority"
        }
        return "null"
    }

    private fun installIframeBindings() {
        registry.iframeBindHook = { iframeEl, jsObj ->
            // Pre-initialize own properties so `"contentDocument" in iframe` is strictly true
            if (!jsObj.hasOwnProperty("contentDocument")) {
                jsObj.set("contentDocument", Null)
            }
            if (!jsObj.hasOwnProperty("contentWindow")) {
                jsObj.set("contentWindow", Null)
            }
            jsObj.defineProperty("srcdoc",
                fn("get_srcdoc") { _, _ -> jsGlobal.wrap(iframeEl.getAttribute("srcdoc").orEmpty()) },
                fn("set_srcdoc") { _, p ->
                    val v = p.getOrNull(0)?.toString().orEmpty()
                    iframeEl.setAttribute("srcdoc", v)
                    reloadIframeElement(iframeEl, jsObj)
                    Undefined
                }
            )
            jsObj.defineProperty("src",
                fn("get_src") { _, _ -> jsGlobal.wrap(iframeEl.getAttribute("src").orEmpty()) },
                fn("set_src") { _, p ->
                    val v = p.getOrNull(0)?.toString().orEmpty()
                    iframeEl.setAttribute("src", v)
                    reloadIframeElement(iframeEl, jsObj)
                    Undefined
                }
            )
            if (iframeEl.hasAttribute("srcdoc") || iframeEl.hasAttribute("src")) {
                reloadIframeElement(iframeEl, jsObj)
            }
        }
    }

    private fun reloadIframeElement(iframeEl: WebDomElement, jsObj: GenericObject) {
        if (!engine.capabilities.iframes) {
            jsObj.set("contentDocument", Null)
            jsObj.set("contentWindow", Null)
            iframeStates[iframeEl] = null to null
            return
        }
        val srcdoc = iframeEl.getAttribute("srcdoc")
        val src = iframeEl.getAttribute("src")?.trim()
        val htmlText: String
        val docUrl: String
        if (srcdoc != null) {
            htmlText = srcdoc
            docUrl = "about:srcdoc"
        } else if (!src.isNullOrEmpty() && !src.startsWith("about:", ignoreCase = true)) {
            val resolvedSrc = engine.resourceLoader.resolveUrl(src, engine.document.url)
            if (resolvedSrc == engine.document.url && engine.document.url != "about:blank") {
                onConsoleLog("warn", "Blocked recursive self-referential <iframe> src '$resolvedSrc'")
                htmlText = "<html><body></body></html>"
                docUrl = "about:blank"
            } else {
                val res = engine.resourceLoader.loadSubresourceSync(resolvedSrc, engine.document.url)
                if (!res.ok) {
                    onConsoleLog("warn", "Failed loading iframe src '$src': ${res.status} ${res.statusText}")
                }
                htmlText = if (res.ok) res.decodeUtf8Text() else "<html><body></body></html>"
                docUrl = res.url
            }
        } else {
            htmlText = "<html><head></head><body></body></html>"
            docUrl = "about:blank"
        }

        val subDoc = WebHtml5Parser.parseDocument(htmlText, docUrl, engine.capabilities.storage)
        val subGlobal = Global(RuntimeCompiler()).apply { initStandards() }
        val subScheduler = NjsEventScheduler().also { subSched ->
            subSched.onError = { t -> onConsoleLog("error", "Iframe script error: ${t.message}") }
        }
        NjsEs6Polyfills.install(subGlobal, subScheduler)
        val subEvents = NjsDomEventSystem(subGlobal, subScheduler)
        val subWin: GenericObject = subGlobal
        subEvents.windowObject = subWin
        subEvents.attachEventTargetMethods(subWin, subWin)
        subEvents.installConstructors()
        subWin.setHidden("window", subWin)
        subWin.setHidden("self", subWin)
        subWin.setHidden("globalThis", subWin)
        subWin.setHidden("parent", windowObj)
        subWin.setHidden("top", windowObj)

        val subEngine = WebAlienDocumentEngine(
            initialWidth = engine.session.viewportWidth.coerceAtMost(640),
            initialHeight = engine.session.viewportHeight.coerceAtMost(480),
            capabilities = engine.capabilities,
            allowFileAccess = engine.resourceLoader.allowFileAccess
        )
        subEngine.replaceDocument(subDoc)
        val subRegistry = NjsDomNodeRegistry(subGlobal, subEngine, subEvents).also {
            it.installConstructors()
        }
        val docWrapper = subRegistry.wrapNode(subDoc)
        subWin.setHidden("document", docWrapper)

        // Expose host-visible contentDocument / contentWindow cleanly
        val hostSubDocWrapper = registry.wrapNode(subDoc)
        jsObj.set("contentDocument", hostSubDocWrapper)
        jsObj.set("contentWindow", subWin)
        iframeStates[iframeEl] = hostSubDocWrapper to subWin

        // Execute inline scripts inside srcdoc/iframe sequentially
        fun collectScripts(node: WebDomElement, out: MutableList<WebDomElement>) {
            if (node.lowerTagName == "script") out.add(node)
            for (c in node.children) collectScripts(c, out)
        }
        val scripts = ArrayList<WebDomElement>()
        subDoc.documentElement?.let { collectScripts(it, scripts) }
        for (sc in scripts) {
            val sType = sc.getAttribute("type")?.trim()?.lowercase().orEmpty()
            if (sType.isNotEmpty() && sType != "text/javascript" && sType != "application/javascript") continue
            try {
                val code = NjsEs6Preprocessor.preprocess(sc.textContent)
                if (code.isNotBlank()) {
                    val s = Scope(subGlobal)
                    subGlobal.compiler?.compile(code, docUrl, false)?.exec(subGlobal, s)
                    subScheduler.flushReadyTimersAndPump()
                }
            } catch (t: Throwable) {
                onConsoleLog("error", "Iframe script execution error: ${t.message}")
            }
        }
        scheduler.queueMicrotask {
            events.dispatchSimpleEvent(iframeEl, "load", bubbles = false, cancelable = false)
        }
    }

    private fun resolvedPromise(value: BaseObject): BaseObject {
        val fnObj = fn("resolveImmediate") { _, execArgs ->
            val resolve = execArgs[0] as BaseFunction
            resolve.call(Undefined, value)
            Undefined
        }
        return JSPromise(jsGlobal).construct(fnObj)
    }

    private fun rejectedPromise(reason: BaseObject): BaseObject {
        val fnObj = fn("rejectImmediate") { _, execArgs ->
            val reject = execArgs[1] as BaseFunction
            reject.call(Undefined, reason)
            Undefined
        }
        return JSPromise(jsGlobal).construct(fnObj)
    }

    private inline fun <T> withWsLock(block: () -> T): T {
        while (!wsQueueLock.compareAndSet(0, 1)) {
            // Brief spin lock synchronizing async WebSocket callbacks with worker loop.
        }
        return try {
            block()
        } finally {
            wsQueueLock.store(0)
        }
    }

    private fun enqueueWsAction(action: () -> Unit) {
        withWsLock { pendingWsActions.add(action) }
    }

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
