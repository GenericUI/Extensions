package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.system.SystemWebViewBridgeDispatcher
import com.nexustools.njs.compiler.RuntimeCompiler
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.Undefined
import com.nexustools.njs.runtime.Utilities

/**
 * Desktop-only JavaScript execution engine for `:extensions:webview:desktop` powered by
 * `NJS` (`lib/NJS`).
 *
 * - On **Desktop Native builds** (`mingwX64`, `linuxX64`, `linuxArm64`, `macosArm64`, `macosX64`),
 *   [RuntimeCompiler] compiles and executes scripts via the NJS AsmJit/SLJIT Native JIT Engine
 *   (`com.nexustools.njs.jit.*` in `nativeMain`).
 * - On **Desktop JVM builds**, [RuntimeCompiler] delegates to NJS's own Java compiler system
 *   (`JavaSourceTranspiler` + `JavaTranspiler`).
 *
 * **Strictly Desktop Only**: Never used on iOS, Android, or Web.
 */
class NjsDesktopWebRuntime(
    val blinkEngine: ChromiumBlinkEngine,
    private val onPostMessage: (String) -> Unit,
    var onConsoleMessage: ((WebViewConsoleMessage) -> Unit)? = null,
    var onJavaScriptError: ((String) -> Unit)? = null,
    var resourceTextLoader: ((String) -> String?)? = null,
    var onBridgeCall: ((String, String) -> String?)? = null,
    var onDomMutation: (() -> Unit)? = null
) {
    val compiler: RuntimeCompiler = run {
        NjsPlatformCompilerBootstrap.ensureInitialized()
        RuntimeCompiler()
    }
    val global: Global = Utilities.createExtendedGlobal(compiler)
    val activeEngineName: String
        get() = NjsPlatformCompilerBootstrap.activeEngineName()

    private val bridgeDispatcher = SystemWebViewBridgeDispatcher()
    private val hostCommunicationFunctions = LinkedHashMap<String, (String) -> String>()
    private val guestCommunicationFunctions = LinkedHashMap<String, BaseFunction>()
    private val windowEventListeners = LinkedHashMap<String, MutableList<BaseFunction>>()
    private val documentEventListeners = LinkedHashMap<String, MutableList<BaseFunction>>()
    private val nodeEventListeners = LinkedHashMap<ChromiumBlinkDomNode, MutableMap<String, MutableList<BaseFunction>>>()
    private val pendingTimers = LinkedHashMap<Int, Pair<BaseFunction, Int>>()
    private var nextTimerId: Int = 1

    private lateinit var genericUiBridge: GenericObject
    private lateinit var documentObj: GenericObject

    init {
        installDomBindings()
        installGuestTestHarness()
    }

    fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        hostCommunicationFunctions[clean] = handler
        bridgeDispatcher.registerHandler(clean, handler)
        val hostGlobal = this.global
        val fn = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val payload = params.getOrNull(0)?.let { if (it === Undefined) "" else it.toString() } ?: ""
                val response = try {
                    handler(payload)
                } catch (t: Throwable) {
                    val errMsg = t.message ?: t::class.simpleName ?: "CommunicationFunctionError"
                    onJavaScriptError?.invoke(errMsg)
                    "{\"error\":${quoteJsonString(errMsg)}}"
                }
                return hostGlobal.wrap(response)
            }
            override fun name(): String = clean
        }
        genericUiBridge.setHidden(clean, fn)
        hostGlobal.setHidden(clean, fn)
    }

    fun unregisterCommunicationFunction(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        hostCommunicationFunctions.remove(clean)
        bridgeDispatcher.unregisterHandler(clean)
        genericUiBridge.deleteProperty(clean)
        global.deleteProperty(clean)
    }

    fun callCommunicationFunction(name: String, payloadJson: String = ""): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val guestFn = guestCommunicationFunctions[clean]
            ?: ((genericUiBridge.get("__guestFunctions") as? GenericObject)?.get(clean) as? BaseFunction)
            ?: (global.get(clean) as? BaseFunction)?.takeIf { clean !in hostCommunicationFunctions }
        if (guestFn != null) {
            return try {
                val arg = global.wrap(payloadJson)
                val res = guestFn.call(global, arg)
                flushImmediateTimers()
                if (res === Undefined) null else res.toString()
            } catch (t: Throwable) {
                val msg = t.message ?: t::class.simpleName ?: "GuestFunctionError"
                onJavaScriptError?.invoke(msg)
                null
            }
        }
        return hostCommunicationFunctions[clean]?.invoke(payloadJson)
    }

    fun deliverHostMessageToGuest(message: String): Boolean {
        val hostGlobal = this.global
        val wrappedMsg = hostGlobal.wrap(message)
        var deliveredToGuest = false
        val onMessageFn = genericUiBridge.get("onMessage") as? BaseFunction
        if (onMessageFn != null) {
            deliveredToGuest = true
            runCatching { onMessageFn.call(genericUiBridge, wrappedMsg) }
                .onFailure { onJavaScriptError?.invoke(it.message ?: "onMessage error") }
        }
        val msgEvent = GenericObject(hostGlobal).apply {
            set("type", hostGlobal.wrap("message"))
            set("data", wrappedMsg)
        }
        val listeners = windowEventListeners["message"]?.toList().orEmpty()
        for (listener in listeners) {
            deliveredToGuest = true
            runCatching { listener.call(hostGlobal, msgEvent) }
                .onFailure { onJavaScriptError?.invoke(it.message ?: "window message listener error") }
        }
        return deliveredToGuest
    }

    fun flushImmediateTimers(maxDelayMs: Int = 50) {
        var safety = 0
        while (pendingTimers.isNotEmpty() && safety < 128) {
            val readyEntry = pendingTimers.entries.firstOrNull { it.value.second <= maxDelayMs } ?: break
            val timerId = readyEntry.key
            val callback = readyEntry.value.first
            pendingTimers.remove(timerId)
            safety++
            runCatching {
                callback.call(global)
            }.onFailure {
                onJavaScriptError?.invoke(it.message ?: "Timer callback error")
            }
        }
    }

    private fun installDomBindings() {
        val hostGlobal = this.global

        // Bind `window`, `globalThis`, `self`, `Boolean`
        hostGlobal.setHidden("window", hostGlobal)
        hostGlobal.setHidden("globalThis", hostGlobal)
        hostGlobal.setHidden("self", hostGlobal)
        hostGlobal.setHidden("Boolean", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                hostGlobal.wrap(params.getOrNull(0)?.toBool() ?: false)
            override fun name(): String = "Boolean"
        })

        val strTargets = listOfNotNull(
            hostGlobal.String,
            hostGlobal.String?.prototype() as? GenericObject
        )
        for (strTarget in strTargets) {
            strTarget.setHidden("indexOf", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val needle = params.getOrNull(0)?.toString() ?: return hostGlobal.NegativeOne
                    val fromIdx = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 0
                    return hostGlobal.wrap(_this.toString().indexOf(needle, fromIdx).toDouble())
                }
                override fun name(): String = "indexOf"
            })
            strTarget.setHidden("includes", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val needle = params.getOrNull(0)?.toString() ?: return hostGlobal.wrap(false)
                    return hostGlobal.wrap(_this.toString().contains(needle))
                }
                override fun name(): String = "includes"
            })
            strTarget.setHidden("toLowerCase", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(_this.toString().lowercase())
                override fun name(): String = "toLowerCase"
            })
            strTarget.setHidden("toUpperCase", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(_this.toString().uppercase())
                override fun name(): String = "toUpperCase"
            })
            strTarget.setHidden("trim", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(_this.toString().trim())
                override fun name(): String = "trim"
            })
            strTarget.setHidden("startsWith", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val prefix = params.getOrNull(0)?.toString() ?: return hostGlobal.wrap(false)
                    return hostGlobal.wrap(_this.toString().startsWith(prefix))
                }
                override fun name(): String = "startsWith"
            })
            strTarget.setHidden("endsWith", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val suffix = params.getOrNull(0)?.toString() ?: return hostGlobal.wrap(false)
                    return hostGlobal.wrap(_this.toString().endsWith(suffix))
                }
                override fun name(): String = "endsWith"
            })
            strTarget.setHidden("substring", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val s = _this.toString()
                    val start = (params.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: 0).coerceIn(0, s.length)
                    val end = (params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: s.length).coerceIn(start, s.length)
                    return hostGlobal.wrap(s.substring(start, end))
                }
                override fun name(): String = "substring"
            })
            strTarget.setHidden("slice", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val s = _this.toString()
                    val rawStart = params.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: 0
                    val start = (if (rawStart < 0) s.length + rawStart else rawStart).coerceIn(0, s.length)
                    val rawEnd = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: s.length
                    val end = (if (rawEnd < 0) s.length + rawEnd else rawEnd).coerceIn(start, s.length)
                    return hostGlobal.wrap(s.substring(start, end))
                }
                override fun name(): String = "slice"
            })
            strTarget.setHidden("replace", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val s = _this.toString()
                    val target = params.getOrNull(0)?.toString() ?: return hostGlobal.wrap(s)
                    val rep = params.getOrNull(1)?.toString() ?: ""
                    return hostGlobal.wrap(s.replaceFirst(target, rep))
                }
                override fun name(): String = "replace"
            })
        }

        // Bind `console.log`, `console.info`, `console.debug`, `console.warn`, `console.error`
        val consoleObj = GenericObject(hostGlobal)
        fun makeConsoleFn(level: String): AbstractFunction = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val msg = params.joinToString(" ") { if (it === Undefined) "undefined" else it.toString() }
                println("[Chromium+NJS WebView][$level] $msg")
                onConsoleMessage?.invoke(WebViewConsoleMessage(level = level, message = msg))
                return Undefined
            }
            override fun name(): String = level
        }
        consoleObj.setHidden("log", makeConsoleFn("log"))
        consoleObj.setHidden("info", makeConsoleFn("info"))
        consoleObj.setHidden("debug", makeConsoleFn("debug"))
        consoleObj.setHidden("warn", makeConsoleFn("warn"))
        consoleObj.setHidden("error", makeConsoleFn("error"))
        hostGlobal.setHidden("console", consoleObj)

        // Bind `document`
        documentObj = GenericObject(hostGlobal)
        documentObj.defineProperty(
            "title",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(blinkEngine.documentTitle)
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    blinkEngine.documentTitle = params.getOrNull(0)?.toString() ?: ""
                    return Undefined
                }
            }
        )
        documentObj.defineProperty(
            "readyState",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(blinkEngine.readyState)
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    blinkEngine.readyState = params.getOrNull(0)?.toString() ?: "complete"
                    return Undefined
                }
            }
        )
        documentObj.defineGetter("documentElement", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                wrapDomNode(blinkEngine.rootNode)
        })
        documentObj.defineGetter("head", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                wrapDomNode(blinkEngine.headNode)
        })
        documentObj.defineGetter("body", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                wrapDomNode(blinkEngine.bodyNode)
        })
        documentObj.setHidden("getElementById", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val id = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return Undefined
                val domNode = blinkEngine.rootNode.findById(id) ?: return Undefined
                return wrapDomNode(domNode)
            }
            override fun name(): String = "getElementById"
        })
        documentObj.setHidden("querySelector", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val sel = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return Undefined
                val domNode = blinkEngine.rootNode.findBySelector(sel) ?: return Undefined
                return wrapDomNode(domNode)
            }
            override fun name(): String = "querySelector"
        })
        documentObj.setHidden("querySelectorAll", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val sel = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return toJsNodeArray(emptyList())
                return toJsNodeArray(blinkEngine.rootNode.findAllBySelector(sel))
            }
            override fun name(): String = "querySelectorAll"
        })
        documentObj.setHidden("getElementsByTagName", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val tag = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return toJsNodeArray(emptyList())
                return toJsNodeArray(blinkEngine.rootNode.findAllByTagName(tag))
            }
            override fun name(): String = "getElementsByTagName"
        })
        documentObj.setHidden("createElement", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val tag = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString()?.trim()?.lowercase()
                    ?.ifEmpty { "div" } ?: "div"
                return wrapDomNode(ChromiumBlinkDomNode(tag))
            }
            override fun name(): String = "createElement"
        })
        documentObj.setHidden("createElementNS", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val tag = params.getOrNull(1)?.takeIf { it !== Undefined }?.toString()?.trim()?.lowercase()
                    ?.ifEmpty { "div" } ?: "div"
                return wrapDomNode(ChromiumBlinkDomNode(tag))
            }
            override fun name(): String = "createElementNS"
        })
        documentObj.setHidden("createTextNode", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val text = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: ""
                return wrapDomNode(ChromiumBlinkDomNode("#text", textContent = text))
            }
            override fun name(): String = "createTextNode"
        })
        documentObj.setHidden("addEventListener", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val type = params.getOrNull(0)?.toString()?.lowercase() ?: return Undefined
                val fn = params.getOrNull(1) as? BaseFunction ?: return Undefined
                documentEventListeners.getOrPut(type) { ArrayList() }.add(fn)
                if (type == "domcontentloaded" && blinkEngine.readyState == "complete") {
                    runCatching { fn.call(documentObj) }
                }
                return Undefined
            }
            override fun name(): String = "addEventListener"
        })
        documentObj.setHidden("removeEventListener", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val type = params.getOrNull(0)?.toString()?.lowercase() ?: return Undefined
                val fn = params.getOrNull(1) as? BaseFunction ?: return Undefined
                documentEventListeners[type]?.remove(fn)
                return Undefined
            }
            override fun name(): String = "removeEventListener"
        })
        documentObj.setHidden("dispatchEvent", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val ev = params.getOrNull(0) ?: return hostGlobal.wrap(true)
                val type = ev.get("type").takeIf { it !== Undefined }?.toString()?.lowercase() ?: return hostGlobal.wrap(true)
                documentEventListeners[type]?.toList()?.forEach { fn ->
                    runCatching { fn.call(documentObj, ev) }
                }
                return hostGlobal.wrap(true)
            }
            override fun name(): String = "dispatchEvent"
        })
        hostGlobal.setHidden("document", documentObj)

        // Bind `window.addEventListener`, `window.removeEventListener`, `window.dispatchEvent`
        hostGlobal.setHidden("addEventListener", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val type = params.getOrNull(0)?.toString()?.lowercase() ?: return Undefined
                val fn = params.getOrNull(1) as? BaseFunction ?: return Undefined
                windowEventListeners.getOrPut(type) { ArrayList() }.add(fn)
                return Undefined
            }
            override fun name(): String = "addEventListener"
        })
        hostGlobal.setHidden("removeEventListener", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val type = params.getOrNull(0)?.toString()?.lowercase() ?: return Undefined
                val fn = params.getOrNull(1) as? BaseFunction ?: return Undefined
                windowEventListeners[type]?.remove(fn)
                return Undefined
            }
            override fun name(): String = "removeEventListener"
        })
        hostGlobal.setHidden("dispatchEvent", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val ev = params.getOrNull(0) ?: return hostGlobal.wrap(true)
                val type = ev.get("type").takeIf { it !== Undefined }?.toString()?.lowercase() ?: return hostGlobal.wrap(true)
                windowEventListeners[type]?.toList()?.forEach { fn ->
                    runCatching { fn.call(hostGlobal, ev) }
                }
                return hostGlobal.wrap(true)
            }
            override fun name(): String = "dispatchEvent"
        })

        // Bind timers & animation frame
        hostGlobal.setHidden("setTimeout", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val fn = params.getOrNull(0) as? BaseFunction ?: return hostGlobal.Zero
                val delay = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 0
                val id = nextTimerId++
                if (delay <= 0) {
                    runCatching { fn.call(hostGlobal) }
                        .onFailure { onJavaScriptError?.invoke(it.message ?: "setTimeout error") }
                } else {
                    pendingTimers[id] = fn to delay
                }
                return hostGlobal.wrap(id.toDouble())
            }
            override fun name(): String = "setTimeout"
        })
        hostGlobal.setHidden("clearTimeout", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val id = params.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: return Undefined
                pendingTimers.remove(id)
                return Undefined
            }
            override fun name(): String = "clearTimeout"
        })
        hostGlobal.setHidden("setInterval", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val fn = params.getOrNull(0) as? BaseFunction ?: return hostGlobal.Zero
                val delay = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 16
                val id = nextTimerId++
                pendingTimers[id] = fn to delay
                return hostGlobal.wrap(id.toDouble())
            }
            override fun name(): String = "setInterval"
        })
        hostGlobal.setHidden("clearInterval", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val id = params.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: return Undefined
                pendingTimers.remove(id)
                return Undefined
            }
            override fun name(): String = "clearInterval"
        })
        hostGlobal.setHidden("requestAnimationFrame", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val fn = params.getOrNull(0) as? BaseFunction ?: return hostGlobal.Zero
                val id = nextTimerId++
                pendingTimers[id] = fn to 16
                return hostGlobal.wrap(id.toDouble())
            }
            override fun name(): String = "requestAnimationFrame"
        })
        hostGlobal.setHidden("cancelAnimationFrame", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val id = params.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: return Undefined
                pendingTimers.remove(id)
                return Undefined
            }
            override fun name(): String = "cancelAnimationFrame"
        })
        hostGlobal.setHidden("queueMicrotask", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val fn = params.getOrNull(0) as? BaseFunction ?: return Undefined
                runCatching { fn.call(hostGlobal) }
                    .onFailure { onJavaScriptError?.invoke(it.message ?: "queueMicrotask error") }
                return Undefined
            }
            override fun name(): String = "queueMicrotask"
        })

        // Bind `location`, `navigator`, `performance`, `fetch`
        val locationObj = GenericObject(hostGlobal).apply {
            defineGetter("href", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(blinkEngine.currentUrl ?: "about:blank")
            })
            set("search", hostGlobal.wrap(""))
            set("hash", hostGlobal.wrap(""))
            set("pathname", hostGlobal.wrap("/"))
            set("origin", hostGlobal.wrap("file://"))
        }
        hostGlobal.setHidden("location", locationObj)

        val navigatorObj = GenericObject(hostGlobal).apply {
            set("userAgent", hostGlobal.wrap("GenericUI-ChromiumBlink-NJS/1.0"))
            set("language", hostGlobal.wrap("en-US"))
            set("platform", hostGlobal.wrap("Desktop"))
        }
        hostGlobal.setHidden("navigator", navigatorObj)

        val performanceObj = GenericObject(hostGlobal).apply {
            var tick = 1.0
            setHidden("now", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    tick += 1.0
                    return hostGlobal.wrap(tick)
                }
                override fun name(): String = "now"
            })
        }
        hostGlobal.setHidden("performance", performanceObj)

        hostGlobal.setHidden("fetch", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val url = params.getOrNull(0)?.toString() ?: ""
                val text = resourceTextLoader?.invoke(url)
                val ok = text != null
                val responseObj = GenericObject(hostGlobal).apply {
                    set("ok", hostGlobal.wrap(ok))
                    set("status", hostGlobal.wrap(if (ok) 200.0 else 404.0))
                    setHidden("text", object : AbstractFunction(hostGlobal) {
                        override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                            createResolvedThenable(hostGlobal.wrap(text ?: ""))
                        override fun name(): String = "text"
                    })
                    setHidden("arrayBuffer", object : AbstractFunction(hostGlobal) {
                        override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                            createResolvedThenable(GenericObject(hostGlobal))
                        override fun name(): String = "arrayBuffer"
                    })
                }
                return createResolvedThenable(responseObj)
            }
            override fun name(): String = "fetch"
        })

        // Bind `genericUi` communication bridge (`postMessage`, `call`, `invokeBridge`, `registerFunction`)
        val postMessageFn = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val msg = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: ""
                onPostMessage(msg)
                return Undefined
            }
            override fun name(): String = "postMessage"
        }
        val callBridgeFn = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val channel = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return hostGlobal.wrap("{}")
                val payload = params.getOrNull(1)?.takeIf { it !== Undefined }?.toString() ?: ""
                val response = try {
                    if (channel in hostCommunicationFunctions) {
                        bridgeDispatcher.dispatch(channel, payload)
                    } else {
                        onBridgeCall?.invoke(channel, payload) ?: bridgeDispatcher.dispatch(channel, payload)
                    }
                } catch (t: Throwable) {
                    val errMsg = t.message ?: t::class.simpleName ?: "BridgeError"
                    onJavaScriptError?.invoke(errMsg)
                    "{\"error\":${quoteJsonString(errMsg)}}"
                }
                return hostGlobal.wrap(response)
            }
            override fun name(): String = "call"
        }
        val registerGuestFn = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val fnName = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString()?.trim() ?: return Undefined
                val fnObj = params.getOrNull(1) as? BaseFunction ?: return Undefined
                if (fnName.isNotEmpty()) {
                    guestCommunicationFunctions[fnName] = fnObj
                }
                return Undefined
            }
            override fun name(): String = "registerCommunicationFunction"
        }
        val unregisterGuestFn = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val fnName = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString()?.trim() ?: return Undefined
                guestCommunicationFunctions.remove(fnName)
                return Undefined
            }
            override fun name(): String = "unregisterCommunicationFunction"
        }
        genericUiBridge = GenericObject(hostGlobal)
        genericUiBridge.setHidden("postMessage", postMessageFn)
        genericUiBridge.setHidden("call", callBridgeFn)
        genericUiBridge.setHidden("callCommunicationFunction", callBridgeFn)
        genericUiBridge.setHidden("invokeBridge", callBridgeFn)
        genericUiBridge.setHidden("registerFunction", registerGuestFn)
        genericUiBridge.setHidden("registerCommunicationFunction", registerGuestFn)
        genericUiBridge.setHidden("unregisterFunction", unregisterGuestFn)
        genericUiBridge.setHidden("unregisterCommunicationFunction", unregisterGuestFn)
        genericUiBridge.setHidden("__guestFunctions", GenericObject(hostGlobal))
        hostGlobal.setHidden("genericUi", genericUiBridge)
        hostGlobal.setHidden("GenericUIWebView", genericUiBridge)
        hostGlobal.setHidden("postMessage", postMessageFn)
    }

    private fun createResolvedThenable(value: BaseObject): GenericObject {
        val hostGlobal = this.global
        val thenable = GenericObject(hostGlobal)
        thenable.setHidden("then", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val onFulfilled = params.getOrNull(0) as? BaseFunction ?: return thenable
                val nextVal = runCatching { onFulfilled.call(hostGlobal, value) }.getOrDefault(Undefined)
                return if (nextVal is GenericObject && nextVal.get("then") is BaseFunction) {
                    nextVal
                } else {
                    createResolvedThenable(nextVal)
                }
            }
            override fun name(): String = "then"
        })
        thenable.setHidden("catch", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = thenable
            override fun name(): String = "catch"
        })
        return thenable
    }

    private fun toJsNodeArray(nodes: List<ChromiumBlinkDomNode>): GenericArray {
        val wrapped = arrayOfNulls<BaseObject>(nodes.size)
        for (i in nodes.indices) {
            wrapped[i] = wrapDomNode(nodes[i])
        }
        val arr = GenericArray(global, global.Array, wrapped)
        arr.setHidden("item", object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val idx = params.getOrNull(0)?.toInt() ?: -1
                return if (idx in nodes.indices) wrapDomNode(nodes[idx]) else Undefined
            }
            override fun name(): String = "item"
        })
        return arr
    }

    internal fun wrapDomNode(node: ChromiumBlinkDomNode): GenericObject {
        (node.jsPeer as? GenericObject)?.let { return it }
        val hostGlobal = this.global
        val elemObj = GenericObject(hostGlobal)
        node.jsPeer = elemObj
        elemObj.metaObject = node

        elemObj.defineProperty(
            "id",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(node.id)
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    node.setAttribute("id", params.getOrNull(0)?.toString() ?: "")
                    return Undefined
                }
            }
        )
        elemObj.defineGetter("tagName", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                hostGlobal.wrap(node.tagName.uppercase())
        })
        elemObj.defineGetter("nodeName", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                hostGlobal.wrap(node.tagName.uppercase())
        })
        val textGetter = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                hostGlobal.wrap(node.textContent)
        }
        val textSetter = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                node.textContent = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: ""
                onDomMutation?.invoke()
                return Undefined
            }
        }
        elemObj.defineProperty("textContent", textGetter, textSetter)
        elemObj.defineProperty("innerText", textGetter, textSetter)
        elemObj.defineProperty(
            "innerHTML",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(node.innerHTML.ifEmpty { node.textContent })
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val html = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: ""
                    node.innerHTML = html
                    node.textContent = html.replace(Regex("<[^>]+>"), "").trim()
                    node.children.clear()
                    if (html.contains('<')) {
                        blinkEngine.populateContainerFromHtmlFragment(node, html)
                    }
                    onDomMutation?.invoke()
                    return Undefined
                }
            }
        )
        elemObj.defineProperty(
            "value",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(node.value)
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    node.value = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: ""
                    return Undefined
                }
            }
        )
        elemObj.defineProperty(
            "checked",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(node.checked)
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    node.checked = params.getOrNull(0)?.toBool() ?: false
                    return Undefined
                }
            }
        )
        elemObj.defineProperty(
            "disabled",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(node.disabled)
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    node.disabled = params.getOrNull(0)?.toBool() ?: false
                    return Undefined
                }
            }
        )
        elemObj.defineProperty(
            "className",
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                    hostGlobal.wrap(node.className)
            },
            object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    node.className = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: ""
                    return Undefined
                }
            }
        )

        for (attrProp in listOf("src", "href", "type", "rel")) {
            elemObj.defineProperty(
                attrProp,
                object : AbstractFunction(hostGlobal) {
                    override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                        hostGlobal.wrap(node.getAttribute(attrProp) ?: "")
                },
                object : AbstractFunction(hostGlobal) {
                    override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                        val v = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: ""
                        node.setAttribute(attrProp, v)
                        if (attrProp == "src" && node.parent != null && node.tagName.equals("script", ignoreCase = true)) {
                            handleDynamicNodeInserted(node, elemObj)
                        }
                        return Undefined
                    }
                }
            )
        }

        val parentGetter = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                node.parent?.let { wrapDomNode(it) } ?: Undefined
        }
        elemObj.defineGetter("parentNode", parentGetter)
        elemObj.defineGetter("parentElement", parentGetter)
        elemObj.defineGetter("firstChild", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                node.children.firstOrNull()?.let { wrapDomNode(it) } ?: Undefined
        })
        val childrenGetter = object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                toJsNodeArray(node.children)
        }
        elemObj.defineGetter("children", childrenGetter)
        elemObj.defineGetter("childNodes", childrenGetter)

        val styleObj = GenericObject(hostGlobal).apply {
            setHidden("setProperty", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val k = params.getOrNull(0)?.toString() ?: return Undefined
                    val v = params.getOrNull(1)?.toString() ?: ""
                    node.style[k] = v
                    set(k, hostGlobal.wrap(v))
                    return Undefined
                }
                override fun name(): String = "setProperty"
            })
            setHidden("getPropertyValue", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val k = params.getOrNull(0)?.toString() ?: return hostGlobal.wrap("")
                    return hostGlobal.wrap(node.style[k] ?: "")
                }
                override fun name(): String = "getPropertyValue"
            })
            setHidden("removeProperty", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val k = params.getOrNull(0)?.toString() ?: return hostGlobal.wrap("")
                    val prev = node.style.remove(k) ?: ""
                    return hostGlobal.wrap(prev)
                }
                override fun name(): String = "removeProperty"
            })
        }
        elemObj.setHidden("style", styleObj)

        val classListObj = GenericObject(hostGlobal).apply {
            setHidden("add", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    for (p in params) {
                        val tok = p.takeIf { it !== Undefined }?.toString()?.trim().orEmpty()
                        if (tok.isNotEmpty()) node.classList.add(tok)
                    }
                    return Undefined
                }
                override fun name(): String = "add"
            })
            setHidden("remove", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    for (p in params) {
                        val tok = p.takeIf { it !== Undefined }?.toString()?.trim().orEmpty()
                        if (tok.isNotEmpty()) node.classList.remove(tok)
                    }
                    return Undefined
                }
                override fun name(): String = "remove"
            })
            setHidden("contains", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val tok = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString()?.trim().orEmpty()
                    return hostGlobal.wrap(tok in node.classList)
                }
                override fun name(): String = "contains"
            })
            setHidden("toggle", object : AbstractFunction(hostGlobal) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                    val tok = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString()?.trim().orEmpty()
                    if (tok.isEmpty()) return hostGlobal.wrap(false)
                    val added = if (tok in node.classList) {
                        node.classList.remove(tok)
                        false
                    } else {
                        node.classList.add(tok)
                        true
                    }
                    return hostGlobal.wrap(added)
                }
                override fun name(): String = "toggle"
            })
        }
        elemObj.setHidden("classList", classListObj)

        elemObj.setHidden("getAttribute", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val name = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return Undefined
                val v = node.getAttribute(name) ?: return Undefined
                return hostGlobal.wrap(v)
            }
            override fun name(): String = "getAttribute"
        })
        elemObj.setHidden("setAttribute", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val name = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return Undefined
                val v = params.getOrNull(1)?.takeIf { it !== Undefined }?.toString() ?: ""
                node.setAttribute(name, v)
                onDomMutation?.invoke()
                return Undefined
            }
            override fun name(): String = "setAttribute"
        })
        elemObj.setHidden("removeAttribute", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val name = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return Undefined
                node.removeAttribute(name)
                onDomMutation?.invoke()
                return Undefined
            }
            override fun name(): String = "removeAttribute"
        })
        elemObj.setHidden("hasAttribute", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val name = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return hostGlobal.wrap(false)
                return hostGlobal.wrap(node.hasAttribute(name))
            }
            override fun name(): String = "hasAttribute"
        })

        elemObj.setHidden("appendChild", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val childObj = params.getOrNull(0) ?: return Undefined
                val childNode = (childObj.metaObject as? ChromiumBlinkDomNode) ?: return childObj
                node.appendChild(childNode)
                handleDynamicNodeInserted(childNode, childObj)
                onDomMutation?.invoke()
                return childObj
            }
            override fun name(): String = "appendChild"
        })
        elemObj.setHidden("insertBefore", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val newObj = params.getOrNull(0) ?: return Undefined
                val refObj = params.getOrNull(1)
                val newNode = (newObj.metaObject as? ChromiumBlinkDomNode) ?: return newObj
                val refNode = refObj?.metaObject as? ChromiumBlinkDomNode
                node.insertBefore(newNode, refNode)
                handleDynamicNodeInserted(newNode, newObj)
                onDomMutation?.invoke()
                return newObj
            }
            override fun name(): String = "insertBefore"
        })
        elemObj.setHidden("removeChild", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val childObj = params.getOrNull(0) ?: return Undefined
                val childNode = (childObj.metaObject as? ChromiumBlinkDomNode) ?: return childObj
                node.removeChild(childNode)
                onDomMutation?.invoke()
                return childObj
            }
            override fun name(): String = "removeChild"
        })
        elemObj.setHidden("remove", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                node.remove()
                onDomMutation?.invoke()
                return Undefined
            }
            override fun name(): String = "remove"
        })

        elemObj.setHidden("querySelector", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val sel = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return Undefined
                return node.findBySelector(sel)?.let { wrapDomNode(it) } ?: Undefined
            }
            override fun name(): String = "querySelector"
        })
        elemObj.setHidden("querySelectorAll", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val sel = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return toJsNodeArray(emptyList())
                return toJsNodeArray(node.findAllBySelector(sel))
            }
            override fun name(): String = "querySelectorAll"
        })
        elemObj.setHidden("getElementsByTagName", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val tag = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: return toJsNodeArray(emptyList())
                return toJsNodeArray(node.findAllByTagName(tag))
            }
            override fun name(): String = "getElementsByTagName"
        })

        elemObj.setHidden("addEventListener", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val type = params.getOrNull(0)?.toString()?.lowercase() ?: return Undefined
                val fn = params.getOrNull(1) as? BaseFunction ?: return Undefined
                nodeEventListeners.getOrPut(node) { LinkedHashMap() }.getOrPut(type) { ArrayList() }.add(fn)
                return Undefined
            }
            override fun name(): String = "addEventListener"
        })
        elemObj.setHidden("removeEventListener", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val type = params.getOrNull(0)?.toString()?.lowercase() ?: return Undefined
                val fn = params.getOrNull(1) as? BaseFunction ?: return Undefined
                nodeEventListeners[node]?.get(type)?.remove(fn)
                return Undefined
            }
            override fun name(): String = "removeEventListener"
        })
        elemObj.setHidden("dispatchEvent", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val ev = params.getOrNull(0) ?: return hostGlobal.wrap(true)
                val type = ev.get("type").takeIf { it !== Undefined }?.toString()?.lowercase() ?: return hostGlobal.wrap(true)
                dispatchDomEventOnNode(node, type, ev)
                return hostGlobal.wrap(true)
            }
            override fun name(): String = "dispatchEvent"
        })
        elemObj.setHidden("click", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                if (!node.disabled) {
                    val ev = GenericObject(hostGlobal).apply {
                        set("type", hostGlobal.wrap("click"))
                        set("target", elemObj)
                    }
                    dispatchDomEventOnNode(node, "click", ev)
                }
                return Undefined
            }
            override fun name(): String = "click"
        })
        elemObj.setHidden("focus", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val ev = GenericObject(hostGlobal).apply {
                    set("type", hostGlobal.wrap("focus"))
                    set("target", elemObj)
                }
                dispatchDomEventOnNode(node, "focus", ev, bubble = false)
                return Undefined
            }
            override fun name(): String = "focus"
        })
        elemObj.setHidden("blur", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val ev = GenericObject(hostGlobal).apply {
                    set("type", hostGlobal.wrap("blur"))
                    set("target", elemObj)
                }
                dispatchDomEventOnNode(node, "blur", ev, bubble = false)
                return Undefined
            }
            override fun name(): String = "blur"
        })
        elemObj.setHidden("getBoundingClientRect", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                GenericObject(hostGlobal).apply {
                    set("left", hostGlobal.Zero)
                    set("top", hostGlobal.Zero)
                    set("width", hostGlobal.wrap(800.0))
                    set("height", hostGlobal.wrap(600.0))
                    set("right", hostGlobal.wrap(800.0))
                    set("bottom", hostGlobal.wrap(600.0))
                }
            override fun name(): String = "getBoundingClientRect"
        })

        return elemObj
    }

    private fun dispatchDomEventOnNode(
        targetNode: ChromiumBlinkDomNode,
        type: String,
        eventObj: BaseObject,
        bubble: Boolean = true
    ) {
        var cur: ChromiumBlinkDomNode? = targetNode
        while (cur != null) {
            val peer = wrapDomNode(cur)
            val listeners = nodeEventListeners[cur]?.get(type)?.toList().orEmpty()
            for (fn in listeners) {
                runCatching { fn.call(peer, eventObj) }
                    .onFailure { onJavaScriptError?.invoke(it.message ?: "DOM listener error ($type)") }
            }
            val onProp = peer.get("on$type") as? BaseFunction
            if (onProp != null) {
                runCatching { onProp.call(peer, eventObj) }
                    .onFailure { onJavaScriptError?.invoke(it.message ?: "DOM on$type error") }
            }
            if (!bubble) break
            cur = cur.parent
        }
    }

    private fun handleDynamicNodeInserted(childNode: ChromiumBlinkDomNode, childObj: BaseObject) {
        if (!childNode.tagName.equals("script", ignoreCase = true)) return
        val scriptType = (childNode.getAttribute("type") ?: "").trim().lowercase()
        val isExec = scriptType.isEmpty() ||
            scriptType == "text/javascript" ||
            scriptType == "application/javascript" ||
            scriptType == "module"
        if (!isExec) return

        val src = childNode.getAttribute("src")?.trim()?.takeIf { it.isNotEmpty() }
        if (src != null) {
            val code = resourceTextLoader?.invoke(src)
            if (code != null) {
                evaluateScript(code, src)
                val onload = childObj.get("onload") as? BaseFunction
                if (onload != null) {
                    runCatching { onload.call(childObj) }
                        .onFailure { onJavaScriptError?.invoke(it.message ?: "script.onload error") }
                }
                dispatchDomEventOnNode(childNode, "load", GenericObject(global), bubble = false)
            } else {
                val onerror = childObj.get("onerror") as? BaseFunction
                if (onerror != null) {
                    runCatching { onerror.call(childObj) }
                        .onFailure { onJavaScriptError?.invoke(it.message ?: "script.onerror error") }
                }
                dispatchDomEventOnNode(childNode, "error", GenericObject(global), bubble = false)
            }
        } else if (childNode.textContent.isNotBlank()) {
            evaluateScript(childNode.textContent, "<dynamic-inline-script>")
        }
    }

    private fun installGuestTestHarness() {
        val harnessScript = """
            window.__testDone = false;
            window.__testErrors = [];
            window.__testStep = "init";
            window.__jestElectricSuites = [];
            window.__jestElectricActiveSuite = null;
            window.__jestElectricReports = [];

            window.runInMainWorld = function(jsBody) {
                var s = document.createElement("script");
                s.textContent = String(jsBody || "");
                (document.head || document.documentElement).appendChild(s);
                s.remove();
            };

            window.describe = function(suiteName, fn) {
                var prevSuite = window.__jestElectricActiveSuite;
                var fullSuite = prevSuite ? (prevSuite.name + " > " + suiteName) : String(suiteName);
                var suiteObj = {
                    name: fullSuite,
                    beforeAll: [],
                    afterAll: [],
                    beforeEach: [],
                    afterEach: [],
                    tests: []
                };
                window.__jestElectricSuites.push(suiteObj);
                window.__jestElectricActiveSuite = suiteObj;
                if (typeof fn === "function") fn();
                window.__jestElectricActiveSuite = prevSuite;
            };

            window.beforeAll = function(fn) {
                if (window.__jestElectricActiveSuite && typeof fn === "function") {
                    window.__jestElectricActiveSuite.beforeAll.push(fn);
                }
            };
            window.afterAll = function(fn) {
                if (window.__jestElectricActiveSuite && typeof fn === "function") {
                    window.__jestElectricActiveSuite.afterAll.push(fn);
                }
            };
            window.beforeEach = function(fn) {
                if (window.__jestElectricActiveSuite && typeof fn === "function") {
                    window.__jestElectricActiveSuite.beforeEach.push(fn);
                }
            };
            window.afterEach = function(fn) {
                if (window.__jestElectricActiveSuite && typeof fn === "function") {
                    window.__jestElectricActiveSuite.afterEach.push(fn);
                }
            };

            window.test = function(testName, fn) {
                if (!window.__jestElectricActiveSuite) {
                    window.describe("Default Web Suite", function() {});
                    window.__jestElectricActiveSuite = window.__jestElectricSuites[window.__jestElectricSuites.length - 1];
                }
                window.__jestElectricActiveSuite.tests.push({ name: String(testName), fn: fn });
            };
            window.it = window.test;

            window.expect = function(actual) {
                return {
                    toBe: function(expected) {
                        if (actual !== expected) {
                            throw new Error("Expected " + JSON.stringify(expected) + " but received " + JSON.stringify(actual));
                        }
                    },
                    toEqual: function(expected) {
                        if (JSON.stringify(actual) !== JSON.stringify(expected)) {
                            throw new Error("Expected " + JSON.stringify(expected) + " to equal " + JSON.stringify(actual));
                        }
                    },
                    toBeTruthy: function() {
                        if (!actual) {
                            throw new Error("Expected value to be truthy, but received " + JSON.stringify(actual));
                        }
                    },
                    toBeFalsy: function() {
                        if (actual) {
                            throw new Error("Expected value to be falsy, but received " + JSON.stringify(actual));
                        }
                    },
                    toBeNull: function() {
                        if (actual !== null && actual !== undefined) {
                            throw new Error("Expected null, but received " + JSON.stringify(actual));
                        }
                    },
                    toBeDefined: function() {
                        if (actual === undefined) {
                            throw new Error("Expected value to be defined, but received undefined");
                        }
                    },
                    toContain: function(item) {
                        if (actual == null || String(actual).indexOf(String(item)) === -1) {
                            throw new Error("Expected " + JSON.stringify(actual) + " to contain " + JSON.stringify(item));
                        }
                    },
                    toBeGreaterThan: function(n) {
                        if (!(actual > n)) {
                            throw new Error("Expected " + actual + " > " + n);
                        }
                    },
                    toBeGreaterThanOrEqual: function(n) {
                        if (!(actual >= n)) {
                            throw new Error("Expected " + actual + " >= " + n);
                        }
                    },
                    toBeLessThan: function(n) {
                        if (!(actual < n)) {
                            throw new Error("Expected " + actual + " < " + n);
                        }
                    }
                };
            };

            window.__runRegisteredWebTests = function(filterSubstring) {
                var filter = filterSubstring ? String(filterSubstring).toLowerCase() : "";
                var total = 0;
                var passed = 0;
                var failed = 0;
                var suites = window.__jestElectricSuites.slice();
                window.__jestElectricSuites = [];

                for (var s = 0; s < suites.length; s++) {
                    var suite = suites[s];
                    if (window.genericUi && typeof window.genericUi.call === "function") {
                        window.genericUi.call("__jestElectricSuiteStart", JSON.stringify({ suiteName: suite.name }));
                    }
                    for (var ba = 0; ba < suite.beforeAll.length; ba++) {
                        suite.beforeAll[ba]();
                    }
                    var suiteTotal = 0;
                    var suitePassed = 0;
                    var suiteFailed = 0;
                    for (var t = 0; t < suite.tests.length; t++) {
                        var tc = suite.tests[t];
                        var full = suite.name + " > " + tc.name;
                        if (filter && full.toLowerCase().indexOf(filter) === -1) continue;
                        total++;
                        suiteTotal++;
                        if (window.genericUi && typeof window.genericUi.call === "function") {
                            window.genericUi.call("__jestElectricTestStart", JSON.stringify({ suitePath: suite.name, testName: tc.name }));
                        }
                        var startMs = (window.performance && typeof window.performance.now === "function") ? window.performance.now() : 0;
                        var ok = true;
                        var errText = "";
                        try {
                            for (var be = 0; be < suite.beforeEach.length; be++) {
                                suite.beforeEach[be]();
                            }
                            if (typeof tc.fn === "function") {
                                tc.fn();
                            }
                            for (var ae = 0; ae < suite.afterEach.length; ae++) {
                                suite.afterEach[ae]();
                            }
                        } catch (err) {
                            ok = false;
                            errText = (err && err.message) ? String(err.message) : String(err);
                        }
                        var durMs = Math.max(1, Math.round(((window.performance && typeof window.performance.now === "function") ? window.performance.now() : 1) - startMs));
                        if (ok) {
                            passed++;
                            suitePassed++;
                        } else {
                            failed++;
                            suiteFailed++;
                        }
                        if (window.genericUi && typeof window.genericUi.call === "function") {
                            window.genericUi.call("__jestElectricTestFinish", JSON.stringify({
                                suitePath: suite.name,
                                testName: tc.name,
                                passed: ok,
                                durationMs: durMs,
                                failureMessage: errText
                            }));
                        }
                    }
                    for (var aa = 0; aa < suite.afterAll.length; aa++) {
                        try { suite.afterAll[aa](); } catch (_e) {}
                    }
                    if (window.genericUi && typeof window.genericUi.call === "function") {
                        window.genericUi.call("__jestElectricSuiteFinish", JSON.stringify({
                            suiteName: suite.name,
                            totalCount: suiteTotal,
                            passedCount: suitePassed,
                            failedCount: suiteFailed
                        }));
                    }
                }
                return JSON.stringify({ totalCount: total, passedCount: passed, failedCount: failed });
            };

            if (window.genericUi && typeof window.genericUi.registerFunction === "function") {
                window.genericUi.registerFunction("__runRegisteredWebTests", function(payload) {
                    return window.__runRegisteredWebTests(payload);
                });
            }
        """.trimIndent()
        evaluateScript(harnessScript, "<guest-test-harness>")
    }

    /**
     * Compiles and executes JavaScript/TypeScript code using NJS.
     */
    fun evaluateScript(script: String, sourceName: String = "<webview-script>"): String? {
        return try {
            val compiled = compiler.compile(script, sourceName, false)
            val result = compiled.exec(global, null)
            flushImmediateTimers()
            if (result == null || result === Undefined) null else result.toString()
        } catch (t: Throwable) {
            val msg = "${t::class.simpleName ?: "JavaScriptError"} in $sourceName: ${t.message ?: t.toString()}"
            onJavaScriptError?.invoke(msg)
            null
        }
    }

    private fun quoteJsonString(value: String): String {
        val sb = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
