package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomMutationRecord
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomNode
import com.nexustools.genericui.ext.webview.desktop.dom.WebHtml5Parser
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.njs.compiler.RuntimeCompiler
import com.nexustools.njs.runtime.AbstractArray
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.Float32Array
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.Int16Array
import com.nexustools.njs.runtime.Int32Array
import com.nexustools.njs.runtime.Int8Array
import com.nexustools.njs.runtime.JSBoolean
import com.nexustools.njs.runtime.JSError
import com.nexustools.njs.runtime.JSNumber
import com.nexustools.njs.runtime.JSString
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Scope
import com.nexustools.njs.runtime.Uint16Array
import com.nexustools.njs.runtime.Uint32Array
import com.nexustools.njs.runtime.Uint8Array
import com.nexustools.njs.runtime.Uint8ClampedArray
import com.nexustools.njs.runtime.Undefined
import kotlin.random.Random

/**
 * Installs BOM & Observer browser APIs onto an NJS worker runtime:
 * - `location`, `history` (`pushState`/`replaceState`/`back`/`forward`/`go` + `popstate`)
 * - `navigator`, `screen`, `performance` (`now`, `timeOrigin`, `mark`, `measure`)
 * - `localStorage` & `sessionStorage` (respecting `capabilities.storage`)
 * - `URL` (`createObjectURL`/`revokeObjectURL`), `URLSearchParams`
 * - `TextEncoder`, `TextDecoder`, `atob`, `btoa`, `DOMParser`
 * - `crypto` (`getRandomValues`, `randomUUID`)
 * - `AbortController` & `AbortSignal`
 * - `MutationObserver`, `ResizeObserver`, `IntersectionObserver`
 * - `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `requestAnimationFrame`, `cancelAnimationFrame`
 * - `Worker` (respecting `capabilities.workers`)
 */
class NjsBomApisBridge(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val scheduler: NjsEventScheduler,
    private val events: NjsDomEventSystem,
    private val registry: NjsDomNodeRegistry,
    private val windowObj: GenericObject,
    private val userAgentOverride: String?,
    private val onNavigateRequest: (String) -> Unit,
    private val onReloadRequest: () -> Unit,
    private val onConsoleLog: (String, String) -> Unit
) {
    private data class ParsedUriParts(
        var protocol: String = "about:",
        var username: String = "",
        var password: String = "",
        var hostname: String = "",
        var port: String = "",
        var pathname: String = "blank",
        var search: String = "",
        var hash: String = ""
    ) {
        val host: String
            get() = if (port.isNotEmpty() && hostname.isNotEmpty()) "$hostname:$port" else hostname

        val origin: String
            get() = when {
                protocol == "file:" || protocol == "about:" || protocol == "data:" -> "null"
                host.isNotEmpty() -> "$protocol//$host"
                else -> "null"
            }

        val href: String
            get() {
                if (protocol == "about:" || protocol == "data:" || protocol == "blob:") {
                    return buildString {
                        append(protocol)
                        if (protocol == "blob:" && hostname.isNotEmpty()) {
                            append(hostname)
                        }
                        append(pathname)
                        append(search)
                        append(hash)
                    }
                }
                val auth = when {
                    username.isNotEmpty() && password.isNotEmpty() -> "$username:$password@"
                    username.isNotEmpty() -> "$username@"
                    else -> ""
                }
                val sep = if (protocol == "file:") "///" else "//"
                val cleanPath = if (protocol == "file:") pathname.trimStart('/') else pathname.ifEmpty { "/" }
                return "$protocol$sep$auth$host$cleanPath$search$hash"
            }
    }

    private data class HistoryEntry(
        val url: String,
        val state: BaseObject
    )

    private data class MutationObservation(
        val target: WebDomNode,
        val childList: Boolean,
        val attributes: Boolean,
        val characterData: Boolean,
        val subtree: Boolean,
        val attributeOldValue: Boolean,
        val characterDataOldValue: Boolean,
        val attributeFilter: Set<String>?
    )

    private inner class MutationObserverBinding(
        val jsObserver: GenericObject,
        val callback: BaseFunction
    ) {
        val observations = ArrayList<MutationObservation>()
        val pendingRecords = ArrayList<GenericObject>()
        var microtaskScheduled = false
    }

    private inner class ResizeObserverBinding(
        val jsObserver: GenericObject,
        val callback: BaseFunction
    ) {
        val lastSizes = LinkedHashMap<WebDomElement, Pair<Double, Double>>()
        var needsInitialDelivery = false
    }

    private inner class IntersectionObserverBinding(
        val jsObserver: GenericObject,
        val callback: BaseFunction,
        val rootElement: WebDomElement?
    ) {
        val lastStates = LinkedHashMap<WebDomElement, Boolean>()
        var needsInitialDelivery = false
    }

    private val localStorageMap = LinkedHashMap<String, String>()
    private val sessionStorageMap = LinkedHashMap<String, String>()
    private val historyStack = ArrayList<HistoryEntry>().apply { add(HistoryEntry("about:blank", Null)) }
    private var historyIndex = 0
    private var suppressingNavReload = false

    private val mutationObservers = ArrayList<MutationObserverBinding>()
    private val resizeObservers = ArrayList<ResizeObserverBinding>()
    private val intersectionObservers = ArrayList<IntersectionObserverBinding>()

    private lateinit var locationObj: GenericObject
    private lateinit var historyObj: GenericObject
    private lateinit var urlCtor: AbstractFunction
    private lateinit var urlSearchParamsCtor: AbstractFunction

    fun install() {
        installTimers()
        installBase64AndEncoding()
        installUrlAndSearchParams()
        installLocationAndHistory()
        installNavigatorScreenAndPerf()
        installStorageApis()
        installCrypto()
        installDomParser()
        installAbortController()
        installObservers()
        installWorkerConstructor()
    }

    fun syncForNewDocument(url: String, resetHistory: Boolean = false) {
        engine.document.onMutation = { record -> onDomMutation(record) }
        if (resetHistory) {
            historyStack.clear()
            historyStack.add(HistoryEntry(url, Null))
            historyIndex = 0
        } else if (historyIndex in historyStack.indices) {
            historyStack[historyIndex] = historyStack[historyIndex].copy(url = url)
        }
        syncLocationFieldsFromUrl(url)
        mutationObservers.forEach { it.pendingRecords.clear(); it.observations.clear() }
        resizeObservers.forEach { it.lastSizes.clear() }
        intersectionObservers.forEach { it.lastStates.clear() }
    }

    fun clearTransientState() {
        mutationObservers.clear()
        resizeObservers.clear()
        intersectionObservers.clear()
    }

    fun evaluateObservers() {
        if (resizeObservers.isEmpty() && intersectionObservers.isEmpty()) return
        evaluateResizeObservers()
        evaluateIntersectionObservers()
    }

    fun canGoBack(): Boolean = historyIndex > 0
    fun canGoForward(): Boolean = historyIndex < historyStack.size - 1

    fun historyGo(delta: Int): String? {
        val nextIdx = historyIndex + delta
        if (nextIdx !in historyStack.indices) return null
        historyIndex = nextIdx
        val target = historyStack[historyIndex]
        syncLocationFieldsFromUrl(target.url)
        return target.url
    }

    private fun installTimers() {
        fun wrapTimerFn(name: String, fnImpl: (BaseObject, Array<out BaseObject>) -> BaseObject) {
            val f = fn(name, fnImpl)
            windowObj.setHidden(name, f)
            jsGlobal.setHidden(name, f)
        }

        wrapTimerFn("setTimeout") { _, params ->
            val cb = params.getOrNull(0) as? BaseFunction
                ?: return@wrapTimerFn jsGlobal.wrap(0.0)
            val delay = params.getOrNull(1)?.takeIf { it !== Undefined && it !== Null }?.toDouble() ?: 0.0
            val extraArgs = if (params.size > 2) params.copyOfRange(2, params.size) else emptyArray()
            val id = scheduler.setTimeout(delay) {
                cb.call(windowObj, *extraArgs)
            }
            jsGlobal.wrap(id.toDouble())
        }

        wrapTimerFn("clearTimeout") { _, params ->
            val id = params.getOrNull(0)?.takeIf { it !== Undefined && it !== Null }?.toInt() ?: return@wrapTimerFn Undefined
            scheduler.clearTimer(id)
            Undefined
        }

        wrapTimerFn("setInterval") { _, params ->
            val cb = params.getOrNull(0) as? BaseFunction
                ?: return@wrapTimerFn jsGlobal.wrap(0.0)
            val interval = params.getOrNull(1)?.takeIf { it !== Undefined && it !== Null }?.toDouble() ?: 0.0
            val extraArgs = if (params.size > 2) params.copyOfRange(2, params.size) else emptyArray()
            val id = scheduler.setInterval(interval) {
                cb.call(windowObj, *extraArgs)
            }
            jsGlobal.wrap(id.toDouble())
        }

        wrapTimerFn("clearInterval") { _, params ->
            val id = params.getOrNull(0)?.takeIf { it !== Undefined && it !== Null }?.toInt() ?: return@wrapTimerFn Undefined
            scheduler.clearTimer(id)
            Undefined
        }

        wrapTimerFn("requestAnimationFrame") { _, params ->
            val cb = params.getOrNull(0) as? BaseFunction
                ?: return@wrapTimerFn jsGlobal.wrap(0.0)
            val id = scheduler.requestAnimationFrame { ts ->
                cb.call(windowObj, jsGlobal.wrap(ts))
            }
            jsGlobal.wrap(id.toDouble())
        }

        wrapTimerFn("cancelAnimationFrame") { _, params ->
            val id = params.getOrNull(0)?.takeIf { it !== Undefined && it !== Null }?.toInt() ?: return@wrapTimerFn Undefined
            scheduler.cancelAnimationFrame(id)
            Undefined
        }
    }

    private fun installBase64AndEncoding() {
        val atobFn = fn("atob") { _, params ->
            val encoded = params.getOrNull(0)?.toString().orEmpty()
            val decoded = base64Decode(encoded)
                ?: throwDomException("The string to be decoded is not correctly encoded.", "InvalidCharacterError")
            jsGlobal.wrap(buildString(decoded.size) {
                for (b in decoded) append((b.toInt() and 0xFF).toChar())
            })
        }
        val btoaFn = fn("btoa") { _, params ->
            val raw = params.getOrNull(0)?.toString().orEmpty()
            val bytes = ByteArray(raw.length) { i ->
                val code = raw[i].code
                if (code > 0xFF) {
                    throwDomException("The string to be encoded contains characters outside of the Latin1 range.", "InvalidCharacterError")
                }
                code.toByte()
            }
            jsGlobal.wrap(base64Encode(bytes))
        }
        windowObj.setHidden("atob", atobFn)
        windowObj.setHidden("btoa", btoaFn)
        jsGlobal.setHidden("atob", atobFn)
        jsGlobal.setHidden("btoa", btoaFn)

        val textEncoderCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val inst = GenericObject(this, jsGlobal)
                inst.setHidden("encoding", jsGlobal.wrap("utf-8"))
                inst.setHidden("encode", fn("encode") { _, p ->
                    val s = p.getOrNull(0)?.takeIf { it !== Undefined }?.toString().orEmpty()
                    val utf8 = s.encodeToByteArray()
                    val u8Ctor = jsGlobal.get("Uint8Array", Scopable.OR_NULL) as? BaseFunction
                    val u8 = u8Ctor?.construct(jsGlobal.wrap(utf8.size.toDouble()))
                        ?: Uint8Array.Instance(jsGlobal, jsGlobal.get("Uint8Array", Scopable.OR_NULL) as? Uint8Array, utf8)
                    if (u8 is Uint8Array.Instance && u8.arrayStorage != null && u8.arrayStorage !== utf8) {
                        utf8.copyInto(u8.arrayStorage!!)
                    } else if (u8 !is Uint8Array.Instance) {
                        for (i in utf8.indices) {
                            u8.set(i, jsGlobal.wrap((utf8[i].toInt() and 0xFF).toDouble()))
                        }
                    }
                    u8
                })
                inst.setHidden("encodeInto", fn("encodeInto") { _, p ->
                    val s = p.getOrNull(0)?.takeIf { it !== Undefined }?.toString().orEmpty()
                    val dst = p.getOrNull(1)
                    val utf8 = s.encodeToByteArray()
                    val dstLen = dst?.get("length")?.toInt() ?: 0
                    val written = minOf(utf8.size, dstLen)
                    for (i in 0 until written) {
                        dst?.set(i, jsGlobal.wrap((utf8[i].toInt() and 0xFF).toDouble()))
                    }
                    GenericObject(jsGlobal).apply {
                        set("read", jsGlobal.wrap(s.length.toDouble()))
                        set("written", jsGlobal.wrap(written.toDouble()))
                    }
                })
                return inst
            }
            override fun name(): String = "TextEncoder"
        }

        val textDecoderCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val label = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString()?.lowercase()?.ifEmpty { "utf-8" } ?: "utf-8"
                val opts = params.getOrNull(1)
                val fatal = opts?.get("fatal", Scopable.OR_NULL)?.toBool() == true
                val ignoreBOM = opts?.get("ignoreBOM", Scopable.OR_NULL)?.toBool() == true
                val inst = GenericObject(this, jsGlobal)
                inst.setHidden("encoding", jsGlobal.wrap(label))
                inst.setHidden("fatal", jsGlobal.wrap(fatal))
                inst.setHidden("ignoreBOM", jsGlobal.wrap(ignoreBOM))
                inst.setHidden("decode", fn("decode") { _, p ->
                    val input = p.getOrNull(0)
                    if (input == null || input === Undefined || input === Null) {
                        return@fn jsGlobal.wrap("")
                    }
                    val bytes = extractByteArrayFromJs(input)
                    val str = if (label == "latin1" || label == "iso-8859-1" || label == "ascii") {
                        buildString(bytes.size) { for (b in bytes) append((b.toInt() and 0xFF).toChar()) }
                    } else {
                        bytes.decodeToString()
                    }
                    jsGlobal.wrap(if (!ignoreBOM && str.startsWith("\uFEFF")) str.substring(1) else str)
                })
                return inst
            }
            override fun name(): String = "TextDecoder"
        }

        windowObj.setHidden("TextEncoder", textEncoderCtor)
        windowObj.setHidden("TextDecoder", textDecoderCtor)
        jsGlobal.setHidden("TextEncoder", textEncoderCtor)
        jsGlobal.setHidden("TextDecoder", textDecoderCtor)
    }

    private fun installUrlAndSearchParams() {
        urlSearchParamsCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                return buildSearchParams(params.getOrNull(0), onMutate = null)
            }
            override fun name(): String = "URLSearchParams"
        }

        urlCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val rawInput = params.getOrNull(0)?.toString().orEmpty()
                val rawBase = params.getOrNull(1)?.takeIf { it !== Undefined && it !== Null }?.toString()
                val resolved = engine.resourceLoader.resolveUrl(rawInput, rawBase)
                if (!resolved.contains(':') && !resolved.startsWith("/")) {
                    throw JSError.JavaException("TypeError", "Invalid URL: $rawInput")
                }
                val parts = parseUriParts(resolved)
                val inst = GenericObject(this, jsGlobal)

                fun refreshUrlStrings() {
                    inst.setProperty("href", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.href)))
                    inst.setProperty("origin", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.origin)))
                    inst.setProperty("protocol", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.protocol)))
                    inst.setProperty("host", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.host)))
                    inst.setProperty("hostname", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.hostname)))
                    inst.setProperty("port", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.port)))
                    inst.setProperty("pathname", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.pathname)))
                    inst.setProperty("search", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.search)))
                    inst.setProperty("hash", com.nexustools.njs.runtime.BaseObject.BasicProperty(jsGlobal.wrap(parts.hash)))
                }

                val liveParams = buildSearchParams(jsGlobal.wrap(parts.search)) { serialized ->
                    parts.search = if (serialized.isEmpty()) "" else "?$serialized"
                    refreshUrlStrings()
                }
                inst.setHidden("searchParams", liveParams)
                refreshUrlStrings()

                inst.defineProperty("href", fn("get_href") { _, _ -> jsGlobal.wrap(parts.href) }, fn("set_href") { _, a ->
                    val next = parseUriParts(a.getOrNull(0)?.toString().orEmpty())
                    parts.protocol = next.protocol
                    parts.username = next.username
                    parts.password = next.password
                    parts.hostname = next.hostname
                    parts.port = next.port
                    parts.pathname = next.pathname
                    parts.search = next.search
                    parts.hash = next.hash
                    refreshUrlStrings()
                    Undefined
                })
                inst.defineProperty("pathname", fn("get_pathname") { _, _ -> jsGlobal.wrap(parts.pathname) }, fn("set_pathname") { _, a ->
                    val p = a.getOrNull(0)?.toString().orEmpty()
                    parts.pathname = if (p.startsWith("/")) p else "/$p"
                    refreshUrlStrings()
                    Undefined
                })
                inst.defineProperty("search", fn("get_search") { _, _ -> jsGlobal.wrap(parts.search) }, fn("set_search") { _, a ->
                    val s = a.getOrNull(0)?.toString().orEmpty()
                    parts.search = if (s.isEmpty() || s == "?") "" else if (s.startsWith("?")) s else "?$s"
                    refreshUrlStrings()
                    Undefined
                })
                inst.defineProperty("hash", fn("get_hash") { _, _ -> jsGlobal.wrap(parts.hash) }, fn("set_hash") { _, a ->
                    val h = a.getOrNull(0)?.toString().orEmpty()
                    parts.hash = if (h.isEmpty() || h == "#") "" else if (h.startsWith("#")) h else "#$h"
                    refreshUrlStrings()
                    Undefined
                })
                inst.setHidden("toString", fn("toString") { _, _ -> jsGlobal.wrap(parts.href) })
                inst.setHidden("toJSON", fn("toJSON") { _, _ -> jsGlobal.wrap(parts.href) })
                return inst
            }
            override fun name(): String = "URL"
        }

        urlCtor.setHidden("createObjectURL", fn("createObjectURL") { _, params ->
            val blob = params.getOrNull(0) ?: return@fn jsGlobal.wrap("")
            val mime = blob.get("type", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toString().orEmpty()
            val bytes = extractByteArrayFromJs(blob)
            jsGlobal.wrap(engine.resourceLoader.registerBlob(mime, bytes))
        })
        urlCtor.setHidden("revokeObjectURL", fn("revokeObjectURL") { _, params ->
            val u = params.getOrNull(0)?.toString().orEmpty()
            if (u.isNotEmpty()) engine.resourceLoader.revokeBlob(u)
            Undefined
        })

        windowObj.setHidden("URLSearchParams", urlSearchParamsCtor)
        windowObj.setHidden("URL", urlCtor)
        jsGlobal.setHidden("URLSearchParams", urlSearchParamsCtor)
        jsGlobal.setHidden("URL", urlCtor)
    }

    private fun buildSearchParams(
        initArg: BaseObject?,
        onMutate: ((String) -> Unit)?
    ): GenericObject {
        val pairs = ArrayList<Pair<String, String>>()

        fun parseQueryString(raw: String) {
            val clean = raw.removePrefix("?")
            if (clean.isEmpty()) return
            for (part in clean.split('&')) {
                if (part.isEmpty()) continue
                val eq = part.indexOf('=')
                if (eq < 0) {
                    pairs.add(decodeFormComponent(part) to "")
                } else {
                    pairs.add(decodeFormComponent(part.substring(0, eq)) to decodeFormComponent(part.substring(eq + 1)))
                }
            }
        }

        when {
            initArg == null || initArg === Undefined || initArg === Null -> Unit
            initArg is GenericArray -> {
                for (i in 0 until initArg.length()) {
                    val item = initArg.get(i)
                    pairs.add(item.get(0).toString() to item.get(1).toString())
                }
            }
            initArg is GenericObject && initArg !is com.nexustools.njs.runtime.JSString.Instance -> {
                for (k in initArg.keys()) {
                    pairs.add(k to initArg.get(k).toString())
                }
            }
            else -> parseQueryString(initArg.toString())
        }

        fun serialize(): String =
            pairs.joinToString("&") { "${encodeFormComponent(it.first)}=${encodeFormComponent(it.second)}" }

        val inst = GenericObject(urlSearchParamsCtor, jsGlobal)
        inst.setHidden("get", fn("get") { _, args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            val match = pairs.firstOrNull { it.first == key }?.second
            if (match != null) jsGlobal.wrap(match) else Null
        })
        inst.setHidden("getAll", fn("getAll") { _, args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            val arr = pairs.filter { it.first == key }.map { jsGlobal.wrap(it.second) as BaseObject? }.toTypedArray()
            GenericArray(jsGlobal, jsGlobal.Array, arr)
        })
        inst.setHidden("has", fn("has") { _, args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            jsGlobal.wrap(pairs.any { it.first == key })
        })
        inst.setHidden("append", fn("append") { _, args ->
            pairs.add(args.getOrNull(0)?.toString().orEmpty() to args.getOrNull(1)?.toString().orEmpty())
            onMutate?.invoke(serialize())
            Undefined
        })
        inst.setHidden("set", fn("set") { _, args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            val value = args.getOrNull(1)?.toString().orEmpty()
            val firstIdx = pairs.indexOfFirst { it.first == key }
            pairs.removeAll { it.first == key }
            if (firstIdx >= 0) pairs.add(firstIdx.coerceAtMost(pairs.size), key to value)
            else pairs.add(key to value)
            onMutate?.invoke(serialize())
            Undefined
        })
        inst.setHidden("delete", fn("delete") { _, args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            pairs.removeAll { it.first == key }
            onMutate?.invoke(serialize())
            Undefined
        })
        inst.setHidden("forEach", fn("forEach") { _, args ->
            val cb = args.getOrNull(0) as? BaseFunction ?: return@fn Undefined
            val thisArg = args.getOrNull(1) ?: Undefined
            for ((k, v) in pairs.toList()) {
                cb.call(thisArg, jsGlobal.wrap(v), jsGlobal.wrap(k), inst)
            }
            Undefined
        })
        inst.setHidden("keys", fn("keys") { _, _ ->
            GenericArray(jsGlobal, jsGlobal.Array, pairs.map { jsGlobal.wrap(it.first) as BaseObject? }.toTypedArray())
        })
        inst.setHidden("values", fn("values") { _, _ ->
            GenericArray(jsGlobal, jsGlobal.Array, pairs.map { jsGlobal.wrap(it.second) as BaseObject? }.toTypedArray())
        })
        inst.setHidden("entries", fn("entries") { _, _ ->
            GenericArray(jsGlobal, jsGlobal.Array, pairs.map {
                GenericArray(jsGlobal, jsGlobal.Array, arrayOf(jsGlobal.wrap(it.first), jsGlobal.wrap(it.second))) as BaseObject?
            }.toTypedArray())
        })
        inst.defineProperty("size", fn("get_size") { _, _ -> jsGlobal.wrap(pairs.size.toDouble()) }, jsGlobal.NOOP)
        inst.setHidden("toString", fn("toString") { _, _ -> jsGlobal.wrap(serialize()) })
        return inst
    }

    private fun installLocationAndHistory() {
        locationObj = GenericObject(jsGlobal)
        locationObj.defineProperty("href",
            fn("get_href") { _, _ -> jsGlobal.wrap(engine.document.url) },
            fn("set_href") { _, params ->
                val target = params.getOrNull(0)?.toString().orEmpty()
                if (!suppressingNavReload && target.isNotEmpty()) {
                    val resolved = resolveNavigationTarget(target)
                    val curNoHash = engine.document.url.substringBefore('#')
                    val nextNoHash = resolved.substringBefore('#')
                    if (curNoHash == nextNoHash) {
                        syncLocationFieldsFromUrl(resolved)
                    } else {
                        onNavigateRequest(resolved)
                    }
                }
                Undefined
            }
        )
        locationObj.defineProperty("hash",
            fn("get_hash") { _, _ -> jsGlobal.wrap(parseUriParts(engine.document.url).hash) },
            fn("set_hash") { _, params ->
                val h = params.getOrNull(0)?.toString().orEmpty()
                val cleanHash = if (h.isEmpty() || h == "#") "" else if (h.startsWith("#")) h else "#$h"
                val base = engine.document.url.substringBefore('#')
                val nextUrl = base + cleanHash
                syncLocationFieldsFromUrl(nextUrl)
                events.dispatchSimpleEvent(windowObj, "hashchange", bubbles = false, cancelable = false)
                Undefined
            }
        )
        locationObj.setHidden("assign", fn("assign") { _, params ->
            val target = params.getOrNull(0)?.toString().orEmpty()
            if (target.isNotEmpty()) onNavigateRequest(resolveNavigationTarget(target))
            Undefined
        })
        locationObj.setHidden("replace", fn("replace") { _, params ->
            val target = params.getOrNull(0)?.toString().orEmpty()
            if (target.isNotEmpty()) {
                val resolved = resolveNavigationTarget(target)
                if (historyIndex in historyStack.indices) {
                    historyStack[historyIndex] = historyStack[historyIndex].copy(url = resolved)
                }
                onNavigateRequest(resolved)
            }
            Undefined
        })
        locationObj.setHidden("reload", fn("reload") { _, _ ->
            onReloadRequest()
            Undefined
        })
        locationObj.setHidden("toString", fn("toString") { _, _ -> jsGlobal.wrap(engine.document.url) })
        syncLocationFieldsFromUrl(engine.document.url)

        historyObj = GenericObject(jsGlobal)
        historyObj.defineProperty("length", fn("get_length") { _, _ -> jsGlobal.wrap(historyStack.size.toDouble()) }, jsGlobal.NOOP)
        historyObj.defineProperty("state", fn("get_state") { _, _ ->
            historyStack.getOrNull(historyIndex)?.state ?: Null
        }, jsGlobal.NOOP)
        historyObj.setHidden("pushState", fn("pushState") { _, params ->
            val state = params.getOrNull(0) ?: Null
            val urlArg = params.getOrNull(2)?.takeIf { it !== Undefined && it !== Null }?.toString()
            val newUrl = if (urlArg != null) resolveNavigationTarget(urlArg) else engine.document.url
            while (historyStack.size > historyIndex + 1) {
                historyStack.removeAt(historyStack.size - 1)
            }
            historyStack.add(HistoryEntry(newUrl, state))
            historyIndex = historyStack.size - 1
            syncLocationFieldsFromUrl(newUrl)
            Undefined
        })
        historyObj.setHidden("replaceState", fn("replaceState") { _, params ->
            val state = params.getOrNull(0) ?: Null
            val urlArg = params.getOrNull(2)?.takeIf { it !== Undefined && it !== Null }?.toString()
            val newUrl = if (urlArg != null) resolveNavigationTarget(urlArg) else engine.document.url
            if (historyIndex in historyStack.indices) {
                historyStack[historyIndex] = HistoryEntry(newUrl, state)
            } else {
                historyStack.add(HistoryEntry(newUrl, state))
                historyIndex = historyStack.size - 1
            }
            syncLocationFieldsFromUrl(newUrl)
            Undefined
        })
        historyObj.setHidden("go", fn("go") { _, params ->
            val delta = params.getOrNull(0)?.toInt() ?: 0
            if (delta != 0) {
                val nextIdx = historyIndex + delta
                if (nextIdx in historyStack.indices) {
                    historyIndex = nextIdx
                    val target = historyStack[historyIndex]
                    syncLocationFieldsFromUrl(target.url)
                    events.dispatchSimpleEvent(
                        windowObj,
                        "popstate",
                        bubbles = false,
                        cancelable = false,
                        extraProps = { evt -> evt.set("state", target.state) }
                    )
                }
            }
            Undefined
        })
        historyObj.setHidden("back", fn("back") { _, _ ->
            (historyObj.get("go") as BaseFunction).call(historyObj, jsGlobal.wrap(-1.0))
        })
        historyObj.setHidden("forward", fn("forward") { _, _ ->
            (historyObj.get("go") as BaseFunction).call(historyObj, jsGlobal.wrap(1.0))
        })

        windowObj.setHidden("location", locationObj)
        jsGlobal.setHidden("location", locationObj)
        windowObj.setHidden("history", historyObj)
        jsGlobal.setHidden("history", historyObj)
    }

    private fun resolveNavigationTarget(target: String): String {
        val trimmed = target.trim()
        if (trimmed.startsWith("#")) {
            return engine.document.url.substringBefore('#') + trimmed
        }
        return engine.resourceLoader.resolveUrl(trimmed, engine.document.url)
    }

    private fun syncLocationFieldsFromUrl(url: String) {
        engine.document.url = url
        val p = parseUriParts(url)
        locationObj.setHidden("protocol", jsGlobal.wrap(p.protocol))
        locationObj.setHidden("host", jsGlobal.wrap(p.host))
        locationObj.setHidden("hostname", jsGlobal.wrap(p.hostname))
        locationObj.setHidden("port", jsGlobal.wrap(p.port))
        locationObj.setHidden("pathname", jsGlobal.wrap(p.pathname))
        locationObj.setHidden("search", jsGlobal.wrap(p.search))
        locationObj.setHidden("origin", jsGlobal.wrap(p.origin))
    }

    private fun installNavigatorScreenAndPerf() {
        val ua = userAgentOverride?.takeIf { it.isNotBlank() }
            ?: "Mozilla/5.0 (GenericUI Alien/1.0; NJS) AppleWebKit/537.36 (KHTML, like Gecko) GenericUI-WebView/1.0"
        val navObj = GenericObject(jsGlobal).apply {
            setHidden("userAgent", jsGlobal.wrap(ua))
            setHidden("language", jsGlobal.wrap("en-US"))
            setHidden("languages", GenericArray(jsGlobal, jsGlobal.Array, arrayOf(jsGlobal.wrap("en-US"), jsGlobal.wrap("en"))))
            setHidden("platform", jsGlobal.wrap("GenericUI-Desktop"))
            setHidden("vendor", jsGlobal.wrap("NexusTools"))
            setHidden("onLine", jsGlobal.wrap(true))
            setHidden("cookieEnabled", jsGlobal.wrap(engine.capabilities.storage))
            setHidden("hardwareConcurrency", jsGlobal.wrap(4.0))
        }
        windowObj.setHidden("navigator", navObj)
        jsGlobal.setHidden("navigator", navObj)

        val screenObj = GenericObject(jsGlobal).apply {
            setHidden("width", jsGlobal.wrap(engine.session.viewportWidth.toDouble()))
            setHidden("height", jsGlobal.wrap(engine.session.viewportHeight.toDouble()))
            setHidden("availWidth", jsGlobal.wrap(engine.session.viewportWidth.toDouble()))
            setHidden("availHeight", jsGlobal.wrap(engine.session.viewportHeight.toDouble()))
            setHidden("colorDepth", jsGlobal.wrap(24.0))
            setHidden("pixelDepth", jsGlobal.wrap(24.0))
        }
        windowObj.setHidden("screen", screenObj)
        jsGlobal.setHidden("screen", screenObj)
        windowObj.defineProperty("innerWidth", fn("get_iw") { _, _ -> jsGlobal.wrap(engine.session.viewportWidth.toDouble()) }, jsGlobal.NOOP)
        windowObj.defineProperty("innerHeight", fn("get_ih") { _, _ -> jsGlobal.wrap(engine.session.viewportHeight.toDouble()) }, jsGlobal.NOOP)
        windowObj.defineProperty("outerWidth", fn("get_ow") { _, _ -> jsGlobal.wrap(engine.session.viewportWidth.toDouble()) }, jsGlobal.NOOP)
        windowObj.defineProperty("outerHeight", fn("get_oh") { _, _ -> jsGlobal.wrap(engine.session.viewportHeight.toDouble()) }, jsGlobal.NOOP)
        windowObj.setHidden("devicePixelRatio", jsGlobal.wrap(1.0))
        windowObj.defineProperty("scrollX", fn("get_sx") { _, _ ->
            jsGlobal.wrap((engine.document.documentElement?.scrollLeftPx ?: 0.0))
        }, jsGlobal.NOOP)
        windowObj.defineProperty("scrollY", fn("get_sy") { _, _ ->
            jsGlobal.wrap((engine.document.documentElement?.scrollTopPx ?: 0.0))
        }, jsGlobal.NOOP)
        windowObj.setHidden("pageXOffset", windowObj.get("scrollX"))
        windowObj.setHidden("pageYOffset", windowObj.get("scrollY"))
        windowObj.setHidden("scrollTo", fn("scrollTo") { _, params ->
            val el = engine.document.documentElement ?: return@fn Undefined
            val arg0 = params.getOrNull(0)
            val isOptsDict = arg0 is GenericObject &&
                arg0 !is JSNumber.Instance &&
                arg0 !is JSBoolean.Instance &&
                arg0 !is JSString.Instance
            val x = if (isOptsDict) {
                arg0.get("left", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toDouble() ?: el.scrollLeftPx
            } else {
                arg0?.takeIf { it !== Undefined && it !== Null }?.toDouble() ?: 0.0
            }
            val y = if (isOptsDict) {
                arg0.get("top", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toDouble() ?: el.scrollTopPx
            } else {
                params.getOrNull(1)?.takeIf { it !== Undefined && it !== Null }?.toDouble() ?: 0.0
            }
            el.scrollLeftPx = x.coerceAtLeast(0.0)
            el.scrollTopPx = y.coerceAtLeast(0.0)
            events.dispatchSimpleEvent(windowObj, "scroll", bubbles = false, cancelable = false)
            Undefined
        })

        val marks = LinkedHashMap<String, Double>()
        val perfObj = GenericObject(jsGlobal).apply {
            setHidden("timeOrigin", jsGlobal.wrap(1700000000000.0))
            setHidden("now", fn("now") { _, _ -> jsGlobal.wrap(scheduler.nowMs()) })
            setHidden("mark", fn("mark") { _, p ->
                val n = p.getOrNull(0)?.toString().orEmpty()
                if (n.isNotEmpty()) marks[n] = scheduler.nowMs()
                Undefined
            })
            setHidden("measure", fn("measure") { _, p ->
                val startName = p.getOrNull(1)?.takeIf { it !== Undefined }?.toString()
                val endName = p.getOrNull(2)?.takeIf { it !== Undefined }?.toString()
                val sTime = startName?.let { marks[it] } ?: 0.0
                val eTime = endName?.let { marks[it] } ?: scheduler.nowMs()
                GenericObject(jsGlobal).apply {
                    set("name", jsGlobal.wrap(p.getOrNull(0)?.toString().orEmpty()))
                    set("startTime", jsGlobal.wrap(sTime))
                    set("duration", jsGlobal.wrap((eTime - sTime).coerceAtLeast(0.0)))
                }
            })
        }
        windowObj.setHidden("performance", perfObj)
        jsGlobal.setHidden("performance", perfObj)
    }

    private fun installStorageApis() {
        fun makeStorageObject(backing: MutableMap<String, String>): GenericObject {
            fun ensureAllowed() {
                if (!engine.capabilities.storage) {
                    throwDomException("Access to WebStorage is disabled by WebView capabilities", "SecurityError")
                }
            }
            val storage = GenericObject(jsGlobal)
            storage.defineProperty("length", fn("get_length") { _, _ ->
                ensureAllowed()
                jsGlobal.wrap(backing.size.toDouble())
            }, jsGlobal.NOOP)
            storage.setHidden("getItem", fn("getItem") { _, p ->
                ensureAllowed()
                val k = p.getOrNull(0)?.toString().orEmpty()
                val v = backing[k]
                if (v != null) jsGlobal.wrap(v) else Null
            })
            storage.setHidden("setItem", fn("setItem") { _, p ->
                ensureAllowed()
                val k = p.getOrNull(0)?.toString().orEmpty()
                val v = p.getOrNull(1)?.toString().orEmpty()
                backing[k] = v
                Undefined
            })
            storage.setHidden("removeItem", fn("removeItem") { _, p ->
                ensureAllowed()
                backing.remove(p.getOrNull(0)?.toString().orEmpty())
                Undefined
            })
            storage.setHidden("clear", fn("clear") { _, _ ->
                ensureAllowed()
                backing.clear()
                Undefined
            })
            storage.setHidden("key", fn("key") { _, p ->
                ensureAllowed()
                val idx = p.getOrNull(0)?.toInt() ?: -1
                val k = backing.keys.elementAtOrNull(idx)
                if (k != null) jsGlobal.wrap(k) else Null
            })
            return storage
        }

        val local = makeStorageObject(localStorageMap)
        val session = makeStorageObject(sessionStorageMap)
        windowObj.setHidden("localStorage", local)
        windowObj.setHidden("sessionStorage", session)
        jsGlobal.setHidden("localStorage", local)
        jsGlobal.setHidden("sessionStorage", session)
    }

    private fun installCrypto() {
        val cryptoObj = GenericObject(jsGlobal)
        cryptoObj.setHidden("getRandomValues", fn("getRandomValues") { _, params ->
            val target = params.getOrNull(0)
                ?: throwDomException("TypeMismatchError: TypedArray expected", "TypeMismatchError")
            when (target) {
                is Uint8Array.Instance -> {
                    val buf = target.arrayStorage ?: ByteArray(0)
                    Random.Default.nextBytes(buf)
                }
                is Uint8ClampedArray.Instance -> {
                    val buf = target.arrayStorage ?: ByteArray(0)
                    Random.Default.nextBytes(buf)
                }
                else -> {
                    val len = target.get("length").toInt().coerceAtLeast(0)
                    for (i in 0 until len) {
                        val rnd = when (target) {
                            is Int8Array.Instance -> Random.Default.nextInt(-128, 128).toDouble()
                            is Uint16Array.Instance -> Random.Default.nextInt(0, 65536).toDouble()
                            is Int16Array.Instance -> Random.Default.nextInt(-32768, 32768).toDouble()
                            is Uint32Array.Instance -> (Random.Default.nextLong() and 0xFFFFFFFFL).toDouble()
                            is Int32Array.Instance -> Random.Default.nextInt().toDouble()
                            else -> Random.Default.nextInt(1, 256).toDouble()
                        }
                        target.set(i, jsGlobal.wrap(rnd))
                    }
                }
            }
            target
        })
        cryptoObj.setHidden("randomUUID", fn("randomUUID") { _, _ ->
            val bytes = Random.Default.nextBytes(16)
            bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
            val uuid = buildString(36) {
                for (i in bytes.indices) {
                    if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
                    val v = bytes[i].toInt() and 0xFF
                    append("0123456789abcdef"[v ushr 4])
                    append("0123456789abcdef"[v and 0x0F])
                }
            }
            jsGlobal.wrap(uuid)
        })
        windowObj.setHidden("crypto", cryptoObj)
        jsGlobal.setHidden("crypto", cryptoObj)
    }

    private fun installDomParser() {
        val ctor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val inst = GenericObject(this, jsGlobal)
                inst.setHidden("parseFromString", fn("parseFromString") { _, p ->
                    val markup = p.getOrNull(0)?.toString().orEmpty()
                    val subDoc = WebHtml5Parser.parseDocument(markup, engine.document.url, engine.capabilities.storage)
                    registry.wrapNode(subDoc)
                })
                return inst
            }
            override fun name(): String = "DOMParser"
        }
        windowObj.setHidden("DOMParser", ctor)
        jsGlobal.setHidden("DOMParser", ctor)
    }

    private fun installAbortController() {
        val abortSignalCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject = createAbortSignal(this)
            override fun name(): String = "AbortSignal"
        }
        abortSignalCtor.setHidden("abort", fn("abort") { _, p ->
            val sig = createAbortSignal(abortSignalCtor)
            triggerAbortSignal(sig, p.getOrNull(0) ?: jsGlobal.wrap("AbortError"))
            sig
        })

        val abortControllerCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val sig = createAbortSignal(abortSignalCtor)
                val ctrl = GenericObject(this, jsGlobal)
                ctrl.setHidden("signal", sig)
                ctrl.setHidden("abort", fn("abort") { _, p ->
                    val reason = p.getOrNull(0)?.takeIf { it !== Undefined }
                        ?: ((jsGlobal.get("DOMException", Scopable.OR_NULL) as? BaseFunction)
                            ?.construct(jsGlobal.wrap("The operation was aborted."), jsGlobal.wrap("AbortError"))
                            ?: jsGlobal.wrap("AbortError"))
                    triggerAbortSignal(sig, reason)
                    Undefined
                })
                return ctrl
            }
            override fun name(): String = "AbortController"
        }

        windowObj.setHidden("AbortSignal", abortSignalCtor)
        windowObj.setHidden("AbortController", abortControllerCtor)
        jsGlobal.setHidden("AbortSignal", abortSignalCtor)
        jsGlobal.setHidden("AbortController", abortControllerCtor)
    }

    private fun createAbortSignal(signalCtor: AbstractFunction): GenericObject {
        val sig = GenericObject(signalCtor, jsGlobal)
        sig.set("aborted", jsGlobal.wrap(false))
        sig.set("reason", Undefined)
        sig.setHidden("onabort", Null)
        events.attachEventTargetMethods(sig, sig)
        return sig
    }

    private fun triggerAbortSignal(signal: GenericObject, reason: BaseObject) {
        if (signal.get("aborted").toBool()) return
        signal.set("aborted", jsGlobal.wrap(true))
        signal.set("reason", reason)
        events.dispatchSimpleEvent(signal, "abort", bubbles = false, cancelable = false)
    }

    private fun installObservers() {
        val moCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val cb = params.getOrNull(0) as? BaseFunction
                    ?: throw JSError.JavaException("TypeError", "MutationObserver callback must be a function")
                val inst = GenericObject(this, jsGlobal)
                val binding = MutationObserverBinding(inst, cb)
                mutationObservers.add(binding)

                inst.setHidden("observe", fn("observe") { _, args ->
                    val node = registry.unwrapNode(args.getOrNull(0))
                        ?: throw JSError.JavaException("TypeError", "MutationObserver.observe requires a DOM Node")
                    val opts = args.getOrNull(1)
                    val childList = opts?.get("childList", Scopable.OR_NULL)?.toBool() == true
                    val attrFilterObj = opts?.get("attributeFilter", Scopable.OR_NULL)
                    val attrFilter: Set<String>? = if (attrFilterObj is GenericArray) {
                        val s = LinkedHashSet<String>()
                        for (i in 0 until attrFilterObj.length()) s.add(attrFilterObj.get(i).toString().lowercase())
                        s
                    } else null
                    val attrOld = opts?.get("attributeOldValue", Scopable.OR_NULL)?.toBool() == true
                    val charOld = opts?.get("characterDataOldValue", Scopable.OR_NULL)?.toBool() == true
                    val attributes = opts?.get("attributes", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool()
                        ?: (attrFilter != null || attrOld)
                    val charData = opts?.get("characterData", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool()
                        ?: charOld
                    val subtree = opts?.get("subtree", Scopable.OR_NULL)?.toBool() == true
                    if (!childList && !attributes && !charData) {
                        throw JSError.JavaException("TypeError", "MutationObserver options must enable childList, attributes, or characterData")
                    }
                    binding.observations.removeAll { it.target === node }
                    binding.observations.add(
                        MutationObservation(node, childList, attributes, charData, subtree, attrOld, charOld, attrFilter)
                    )
                    Undefined
                })
                inst.setHidden("disconnect", fn("disconnect") { _, _ ->
                    binding.observations.clear()
                    binding.pendingRecords.clear()
                    Undefined
                })
                inst.setHidden("takeRecords", fn("takeRecords") { _, _ ->
                    val arr = GenericArray(jsGlobal, jsGlobal.Array, binding.pendingRecords.toTypedArray<BaseObject?>())
                    binding.pendingRecords.clear()
                    arr
                })
                return inst
            }
            override fun name(): String = "MutationObserver"
        }

        val roCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val cb = params.getOrNull(0) as? BaseFunction
                    ?: throw JSError.JavaException("TypeError", "ResizeObserver callback must be a function")
                val inst = GenericObject(this, jsGlobal)
                val binding = ResizeObserverBinding(inst, cb)
                resizeObservers.add(binding)

                inst.setHidden("observe", fn("observe") { _, args ->
                    val el = registry.unwrapElement(args.getOrNull(0)) ?: return@fn Undefined
                    if (!binding.lastSizes.containsKey(el)) {
                        binding.lastSizes[el] = -1.0 to -1.0
                        binding.needsInitialDelivery = true
                    }
                    scheduler.queueMicrotask { evaluateResizeObservers() }
                    Undefined
                })
                inst.setHidden("unobserve", fn("unobserve") { _, args ->
                    val el = registry.unwrapElement(args.getOrNull(0)) ?: return@fn Undefined
                    binding.lastSizes.remove(el)
                    Undefined
                })
                inst.setHidden("disconnect", fn("disconnect") { _, _ ->
                    binding.lastSizes.clear()
                    Undefined
                })
                return inst
            }
            override fun name(): String = "ResizeObserver"
        }

        val ioCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val cb = params.getOrNull(0) as? BaseFunction
                    ?: throw JSError.JavaException("TypeError", "IntersectionObserver callback must be a function")
                val opts = params.getOrNull(1)
                val rootEl = opts?.get("root", Scopable.OR_NULL)?.let { registry.unwrapElement(it) }
                val inst = GenericObject(this, jsGlobal)
                val binding = IntersectionObserverBinding(inst, cb, rootEl)
                intersectionObservers.add(binding)

                inst.setHidden("root", if (rootEl != null) registry.wrapNode(rootEl) else Null)
                inst.setHidden("rootMargin", opts?.get("rootMargin", Scopable.OR_NULL)?.takeIf { it !== Undefined } ?: jsGlobal.wrap("0px"))
                inst.setHidden("observe", fn("observe") { _, args ->
                    val el = registry.unwrapElement(args.getOrNull(0)) ?: return@fn Undefined
                    if (!binding.lastStates.containsKey(el)) {
                        binding.lastStates[el] = false
                        binding.needsInitialDelivery = true
                    }
                    scheduler.queueMicrotask { evaluateIntersectionObservers() }
                    Undefined
                })
                inst.setHidden("unobserve", fn("unobserve") { _, args ->
                    val el = registry.unwrapElement(args.getOrNull(0)) ?: return@fn Undefined
                    binding.lastStates.remove(el)
                    Undefined
                })
                inst.setHidden("disconnect", fn("disconnect") { _, _ ->
                    binding.lastStates.clear()
                    Undefined
                })
                inst.setHidden("takeRecords", fn("takeRecords") { _, _ -> GenericArray(jsGlobal) })
                return inst
            }
            override fun name(): String = "IntersectionObserver"
        }

        windowObj.setHidden("MutationObserver", moCtor)
        windowObj.setHidden("ResizeObserver", roCtor)
        windowObj.setHidden("IntersectionObserver", ioCtor)
        jsGlobal.setHidden("MutationObserver", moCtor)
        jsGlobal.setHidden("ResizeObserver", roCtor)
        jsGlobal.setHidden("IntersectionObserver", ioCtor)
    }

    private fun onDomMutation(record: WebDomMutationRecord) {
        if (mutationObservers.isEmpty()) return
        for (binding in mutationObservers.toList()) {
            var matchedObs: MutationObservation? = null
            for (obs in binding.observations) {
                val targetMatches = if (obs.subtree) {
                    isSelfOrDescendant(obs.target, record.target)
                } else {
                    obs.target === record.target
                }
                if (!targetMatches) continue
                val typeMatches = when (record.type) {
                    "childList" -> obs.childList
                    "attributes" -> obs.attributes && (obs.attributeFilter == null || record.attributeName?.lowercase() in obs.attributeFilter)
                    "characterData" -> obs.characterData
                    else -> false
                }
                if (typeMatches) {
                    matchedObs = obs
                    break
                }
            }
            if (matchedObs != null) {
                val jsRec = GenericObject(jsGlobal).apply {
                    set("type", jsGlobal.wrap(record.type))
                    set("target", registry.wrapNode(record.target))
                    set("addedNodes", GenericArray(jsGlobal, jsGlobal.Array, record.addedNodes.map { registry.wrapNode(it) as BaseObject? }.toTypedArray()))
                    set("removedNodes", GenericArray(jsGlobal, jsGlobal.Array, record.removedNodes.map { registry.wrapNode(it) as BaseObject? }.toTypedArray()))
                    set("previousSibling", registry.wrapNodeOrNull(record.previousSibling))
                    set("nextSibling", registry.wrapNodeOrNull(record.nextSibling))
                    set("attributeName", if (record.attributeName != null) jsGlobal.wrap(record.attributeName) else Null)
                    val oldVal = when (record.type) {
                        "attributes" -> if (matchedObs.attributeOldValue) record.oldValue else null
                        "characterData" -> if (matchedObs.characterDataOldValue) record.oldValue else null
                        else -> null
                    }
                    set("oldValue", if (oldVal != null) jsGlobal.wrap(oldVal) else Null)
                }
                binding.pendingRecords.add(jsRec)
                if (!binding.microtaskScheduled) {
                    binding.microtaskScheduled = true
                    scheduler.queueMicrotask {
                        binding.microtaskScheduled = false
                        if (binding.pendingRecords.isEmpty()) return@queueMicrotask
                        val batch = binding.pendingRecords.toTypedArray<BaseObject?>()
                        binding.pendingRecords.clear()
                        scheduler.invokeJsCallbackSafely(
                            binding.callback,
                            binding.jsObserver,
                            GenericArray(jsGlobal, jsGlobal.Array, batch),
                            binding.jsObserver
                        )
                    }
                }
            }
        }
    }

    private fun evaluateResizeObservers() {
        for (binding in resizeObservers.toList()) {
            if (binding.lastSizes.isEmpty()) continue
            val changed = ArrayList<GenericObject>()
            for (el in binding.lastSizes.keys.toList()) {
                val rect = engine.computeDomRect(el)
                val prev = binding.lastSizes[el]
                if (prev == null || prev.first != rect.width || prev.second != rect.height || binding.needsInitialDelivery) {
                    binding.lastSizes[el] = rect.width to rect.height
                    val contentRect = GenericObject(jsGlobal).apply {
                        set("x", jsGlobal.wrap(0.0))
                        set("y", jsGlobal.wrap(0.0))
                        set("top", jsGlobal.wrap(0.0))
                        set("left", jsGlobal.wrap(0.0))
                        set("width", jsGlobal.wrap(rect.width))
                        set("height", jsGlobal.wrap(rect.height))
                        set("right", jsGlobal.wrap(rect.width))
                        set("bottom", jsGlobal.wrap(rect.height))
                    }
                    val boxSize = GenericObject(jsGlobal).apply {
                        set("inlineSize", jsGlobal.wrap(rect.width))
                        set("blockSize", jsGlobal.wrap(rect.height))
                    }
                    changed.add(GenericObject(jsGlobal).apply {
                        set("target", registry.wrapNode(el))
                        set("contentRect", contentRect)
                        set("contentBoxSize", GenericArray(jsGlobal, jsGlobal.Array, arrayOf(boxSize as BaseObject?)))
                        set("borderBoxSize", GenericArray(jsGlobal, jsGlobal.Array, arrayOf(boxSize as BaseObject?)))
                    })
                }
            }
            binding.needsInitialDelivery = false
            if (changed.isNotEmpty()) {
                val arr = GenericArray(jsGlobal, jsGlobal.Array, changed.toTypedArray<BaseObject?>())
                scheduler.invokeJsCallbackSafely(binding.callback, binding.jsObserver, arr, binding.jsObserver)
            }
        }
    }

    private fun evaluateIntersectionObservers() {
        for (binding in intersectionObservers.toList()) {
            if (binding.lastStates.isEmpty()) continue
            val rootRect = if (binding.rootElement != null) {
                engine.computeDomRect(binding.rootElement)
            } else {
                WebAlienDocumentEngine.DomRect(0.0, 0.0, engine.session.viewportWidth.toDouble(), engine.session.viewportHeight.toDouble())
            }
            val entries = ArrayList<GenericObject>()
            for (el in binding.lastStates.keys.toList()) {
                val rect = engine.computeDomRect(el)
                val ix1 = maxOf(rect.left, rootRect.left)
                val iy1 = maxOf(rect.top, rootRect.top)
                val ix2 = minOf(rect.right, rootRect.right)
                val iy2 = minOf(rect.bottom, rootRect.bottom)
                val iw = (ix2 - ix1).coerceAtLeast(0.0)
                val ih = (iy2 - iy1).coerceAtLeast(0.0)
                val connected = el.isConnected && rect.width > 0.0 && rect.height > 0.0
                val isIntersecting = connected && iw > 0.0 && ih > 0.0
                val area = rect.width * rect.height
                val ratio = if (!isIntersecting || area <= 0.0) 0.0 else ((iw * ih) / area).coerceIn(0.0, 1.0)
                val prevState = binding.lastStates[el]
                if (binding.needsInitialDelivery || prevState != isIntersecting) {
                    binding.lastStates[el] = isIntersecting
                    entries.add(GenericObject(jsGlobal).apply {
                        set("target", registry.wrapNode(el))
                        set("time", jsGlobal.wrap(scheduler.nowMs()))
                        set("isIntersecting", jsGlobal.wrap(isIntersecting))
                        set("intersectionRatio", jsGlobal.wrap(ratio))
                        set("boundingClientRect", rectToJs(rect))
                        set("rootBounds", rectToJs(rootRect))
                        set("intersectionRect", rectToJs(WebAlienDocumentEngine.DomRect(ix1, iy1, iw, ih)))
                    })
                }
            }
            binding.needsInitialDelivery = false
            if (entries.isNotEmpty()) {
                val arr = GenericArray(jsGlobal, jsGlobal.Array, entries.toTypedArray<BaseObject?>())
                scheduler.invokeJsCallbackSafely(binding.callback, binding.jsObserver, arr, binding.jsObserver)
            }
        }
    }

    private fun rectToJs(r: WebAlienDocumentEngine.DomRect): GenericObject =
        GenericObject(jsGlobal).apply {
            set("x", jsGlobal.wrap(r.x))
            set("y", jsGlobal.wrap(r.y))
            set("width", jsGlobal.wrap(r.width))
            set("height", jsGlobal.wrap(r.height))
            set("top", jsGlobal.wrap(r.top))
            set("left", jsGlobal.wrap(r.left))
            set("right", jsGlobal.wrap(r.right))
            set("bottom", jsGlobal.wrap(r.bottom))
        }

    private fun isSelfOrDescendant(ancestor: WebDomNode, target: WebDomNode): Boolean {
        var cur: WebDomNode? = target
        while (cur != null) {
            if (cur === ancestor) return true
            cur = cur.parentNode
        }
        return false
    }

    private fun installWorkerConstructor() {
        val workerCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                if (!engine.capabilities.workers) {
                    throwDomException("Web Workers are disabled by WebView capabilities", "SecurityError")
                }
                val rawUrl = params.getOrNull(0)?.toString().orEmpty()
                val res = engine.resourceLoader.loadSync(rawUrl, engine.document.url)
                val workerInst = GenericObject(this, jsGlobal)
                workerInst.setHidden("onmessage", Null)
                workerInst.setHidden("onerror", Null)
                events.attachEventTargetMethods(workerInst, workerInst)

                if (!res.ok) {
                    scheduler.queueMicrotask {
                        events.dispatchSimpleEvent(workerInst, "error", bubbles = false, cancelable = false)
                    }
                    return workerInst
                }

                val subGlobal = Global(RuntimeCompiler()).apply { initStandards() }
                val subScheduler = NjsEventScheduler().also { subSched ->
                    subSched.onError = { t -> onConsoleLog("error", "Worker error: ${t.message}") }
                }
                NjsEs6Polyfills.install(subGlobal, subScheduler)
                val workerScopeObj = GenericObject(subGlobal)
                subGlobal.setHidden("self", subGlobal)
                subGlobal.setHidden("onmessage", Null)
                var terminated = false

                subGlobal.setHidden("postMessage", object : AbstractFunction(subGlobal) {
                    override fun call(_this: BaseObject, vararg args: BaseObject): BaseObject {
                        if (terminated) return Undefined
                        val msg = args.getOrNull(0) ?: Undefined
                        val cloned = marshalBetweenGlobals(msg, jsGlobal)
                        scheduler.queueMicrotask {
                            if (terminated) return@queueMicrotask
                            events.dispatchSimpleEvent(
                                workerInst,
                                "message",
                                bubbles = false,
                                cancelable = false,
                                extraProps = { ev -> ev.set("data", cloned) }
                            )
                        }
                        return Undefined
                    }
                    override fun name(): String = "Worker_self_postMessage"
                })

                workerInst.setHidden("postMessage", fn("postMessage") { _, args ->
                    if (terminated) return@fn Undefined
                    val msg = args.getOrNull(0) ?: Undefined
                    val cloned = marshalBetweenGlobals(msg, subGlobal)
                    val handler = subGlobal.get("onmessage", Scopable.OR_NULL) as? BaseFunction
                    if (handler != null) {
                        val ev = GenericObject(subGlobal).apply { set("data", cloned) }
                        subScheduler.queueMicrotask { handler.call(subGlobal, ev) }
                        subScheduler.flushReadyTimersAndPump()
                    }
                    Undefined
                })
                workerInst.setHidden("terminate", fn("terminate") { _, _ ->
                    terminated = true
                    subScheduler.clearAll()
                    Undefined
                })
                workerScopeObj.toString()

                try {
                    val code = NjsEs6Preprocessor.preprocess(res.decodeUtf8Text())
                    val s = Scope(subGlobal)
                    subGlobal.compiler?.compile(code, res.url, false)?.exec(subGlobal, s)
                    subScheduler.flushReadyTimersAndPump()
                } catch (t: Throwable) {
                    onConsoleLog("error", "Worker execution failed: ${t.message}")
                    scheduler.queueMicrotask {
                        events.dispatchSimpleEvent(workerInst, "error", bubbles = false, cancelable = false)
                    }
                }
                return workerInst
            }
            override fun name(): String = "Worker"
        }
        windowObj.setHidden("Worker", workerCtor)
        jsGlobal.setHidden("Worker", workerCtor)
    }

    private fun marshalBetweenGlobals(value: BaseObject, dstGlobal: Global): BaseObject {
        return when (value) {
            Undefined -> Undefined
            Null -> Null
            is com.nexustools.njs.runtime.JSBoolean.Instance -> dstGlobal.wrap(value.value)
            is com.nexustools.njs.runtime.JSNumber.Instance -> dstGlobal.wrap(value.value)
            is com.nexustools.njs.runtime.JSString.Instance -> dstGlobal.wrap(value.string)
            is GenericArray -> GenericArray(
                dstGlobal,
                dstGlobal.Array,
                Array(value.length()) { idx -> marshalBetweenGlobals(value.get(idx), dstGlobal) }
            )
            is Uint8Array.Instance -> {
                val copy = value.arrayStorage?.copyOf() ?: ByteArray(0)
                Uint8Array.Instance(dstGlobal, dstGlobal.get("Uint8Array", Scopable.OR_NULL) as? Uint8Array, copy)
            }
            is GenericObject -> GenericObject(dstGlobal).apply {
                for (k in value.keys()) set(k, marshalBetweenGlobals(value.get(k), dstGlobal))
            }
            else -> dstGlobal.wrap(value.toString())
        }
    }

    internal fun extractByteArrayFromJs(input: BaseObject): ByteArray {
        val rawBytes = input.get("__bytes", Scopable.OR_NULL) as? Uint8Array.Instance
        if (rawBytes?.arrayStorage != null) return rawBytes.arrayStorage!!
        if (input is Uint8Array.Instance && input.arrayStorage != null) return input.arrayStorage!!
        if (input is Uint8ClampedArray.Instance && input.arrayStorage != null) return input.arrayStorage!!
        val len = input.get("length", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toInt()?.coerceAtLeast(0)
            ?: input.get("size", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toInt()?.coerceAtLeast(0)
            ?: return input.toString().encodeToByteArray()
        return ByteArray(len) { i -> input.get(i).toInt().toByte() }
    }

    private fun parseUriParts(url: String): ParsedUriParts {
        val parts = ParsedUriParts()
        var rem = url.trim()
        if (rem.isEmpty()) return parts
        val hashIdx = rem.indexOf('#')
        if (hashIdx >= 0) {
            parts.hash = rem.substring(hashIdx)
            rem = rem.substring(0, hashIdx)
        }
        val queryIdx = rem.indexOf('?')
        if (queryIdx >= 0) {
            parts.search = rem.substring(queryIdx)
            rem = rem.substring(0, queryIdx)
        }
        val colonIdx = rem.indexOf(':')
        if (colonIdx > 0) {
            parts.protocol = rem.substring(0, colonIdx + 1).lowercase()
            rem = rem.substring(colonIdx + 1)
        }
        if (rem.startsWith("//")) {
            rem = rem.substring(2)
            if (parts.protocol == "file:") {
                parts.pathname = if (rem.startsWith("/")) rem else "/$rem"
                return parts
            }
            val slashIdx = rem.indexOf('/')
            val authority = if (slashIdx >= 0) rem.substring(0, slashIdx) else rem
            parts.pathname = if (slashIdx >= 0) rem.substring(slashIdx) else "/"
            val atIdx = authority.lastIndexOf('@')
            val hostPort = if (atIdx >= 0) {
                val userInfo = authority.substring(0, atIdx)
                val upColon = userInfo.indexOf(':')
                if (upColon >= 0) {
                    parts.username = userInfo.substring(0, upColon)
                    parts.password = userInfo.substring(upColon + 1)
                } else {
                    parts.username = userInfo
                }
                authority.substring(atIdx + 1)
            } else authority
            val portColon = hostPort.lastIndexOf(':')
            if (portColon > 0 && !hostPort.endsWith(']')) {
                parts.hostname = hostPort.substring(0, portColon)
                parts.port = hostPort.substring(portColon + 1)
            } else {
                parts.hostname = hostPort
            }
        } else {
            parts.pathname = rem
        }
        return parts
    }

    private fun encodeFormComponent(raw: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~*"
        val bytes = raw.encodeToByteArray()
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            val ch = code.toChar()
            if (ch == ' ') sb.append('+')
            else if (code < 128 && ch in unreserved) sb.append(ch)
            else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[code ushr 4])
                sb.append("0123456789ABCDEF"[code and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun decodeFormComponent(raw: String): String {
        val bytes = ArrayList<Byte>(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '+') {
                bytes.add(' '.code.toByte())
                i++
            } else if (ch == '%' && i + 2 < raw.length) {
                val hex = raw.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.add(hex.toByte())
                    i += 3
                    continue
                }
                bytes.add('%'.code.toByte())
                i++
            } else {
                for (b in ch.toString().encodeToByteArray()) bytes.add(b)
                i++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun base64Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i++].toInt() and 0xFF
            val b1 = if (i < data.size) data[i++].toInt() and 0xFF else -1
            val b2 = if (i < data.size) data[i++].toInt() and 0xFF else -1
            sb.append(alphabet[b0 ushr 2])
            sb.append(alphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
            sb.append(if (b1 >= 0) alphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)] else '=')
            sb.append(if (b2 >= 0) alphabet[b2 and 0x3F] else '=')
        }
        return sb.toString()
    }

    private fun base64Decode(input: String): ByteArray? {
        val clean = buildString {
            for (c in input) if (!c.isWhitespace()) append(c)
        }
        if (clean.length % 4 != 0) return null
        val rev = IntArray(256) { -1 }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in alphabet.indices) rev[alphabet[i].code] = i
        val out = ArrayList<Byte>((clean.length / 4) * 3)
        var i = 0
        while (i < clean.length) {
            val c0 = clean[i].code
            val c1 = clean[i + 1].code
            val c2 = clean[i + 2]
            val c3 = clean[i + 3]
            if (c0 >= 256 || c1 >= 256 || rev[c0] < 0 || rev[c1] < 0) return null
            val v0 = rev[c0]
            val v1 = rev[c1]
            out.add(((v0 shl 2) or (v1 ushr 4)).toByte())
            if (c2 != '=') {
                if (c2.code >= 256 || rev[c2.code] < 0) return null
                val v2 = rev[c2.code]
                out.add((((v1 and 0x0F) shl 4) or (v2 ushr 2)).toByte())
                if (c3 != '=') {
                    if (c3.code >= 256 || rev[c3.code] < 0) return null
                    val v3 = rev[c3.code]
                    out.add((((v2 and 0x03) shl 6) or v3).toByte())
                }
            } else if (c3 != '=') {
                return null
            }
            i += 4
        }
        return out.toByteArray()
    }

    private fun throwDomException(message: String, name: String): Nothing {
        val ctor = jsGlobal.get("DOMException", Scopable.OR_NULL) as? BaseFunction
        val errObj = ctor?.construct(jsGlobal.wrap(message), jsGlobal.wrap(name)) ?: jsGlobal.wrap("$name: $message")
        throw JSError.Thrown(errObj)
    }

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
