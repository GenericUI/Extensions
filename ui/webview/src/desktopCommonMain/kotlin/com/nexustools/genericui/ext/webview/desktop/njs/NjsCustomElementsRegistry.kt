package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomNode
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.JSError
import com.nexustools.njs.runtime.JSPromise
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Undefined

/**
 * Implements W3C Custom Elements v1 (`window.customElements`):
 * - `define(name, constructor, options?)`
 * - `get(name)`
 * - `whenDefined(name)`
 * - `upgrade(root)`
 *
 * Supports automatic upgrading of parsed and dynamically created elements, `new MyElement()`,
 * `connectedCallback`, `disconnectedCallback`, and `observedAttributes` / `attributeChangedCallback`.
 */
class NjsCustomElementsRegistry(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val registry: NjsDomNodeRegistry,
    private val windowObj: GenericObject,
    private val onError: (Throwable) -> Unit
) {
    private companion object {
        private val RESERVED_NAMES = setOf(
            "annotation-xml",
            "color-profile",
            "font-face",
            "font-face-src",
            "font-face-uri",
            "font-face-format",
            "font-face-name",
            "missing-glyph"
        )
    }

    internal data class Definition(
        val name: String,
        val ctor: BaseFunction,
        val observedAttributes: Set<String>
    )

    private val definitionsByName = LinkedHashMap<String, Definition>()
    private val definitionsByCtor = LinkedHashMap<BaseFunction, Definition>()
    private val upgradedElements = HashSet<WebDomElement>()
    private val currentlyUpgrading = HashSet<WebDomElement>()
    private val pendingWhenDefined = LinkedHashMap<String, ArrayList<(BaseFunction) -> Unit>>()

    fun install() {
        val registryCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = Undefined
            override fun name(): String = "CustomElementRegistry"
        }
        val ceObj = GenericObject(registryCtor, jsGlobal)

        ceObj.setHidden("define", fn("define") { _, params ->
            val rawName = params.getOrNull(0)?.toString().orEmpty()
            validateCustomElementName(rawName)
            val ctor = params.getOrNull(1) as? BaseFunction
                ?: throw JSError.JavaException("TypeError", "Failed to execute 'define' on 'CustomElementRegistry': parameter 2 is not of type 'Function'.")
            if (definitionsByName.containsKey(rawName)) {
                throw JSError.JavaException("NotSupportedError", "The name '$rawName' has already been used with this registry.")
            }
            if (definitionsByCtor.containsKey(ctor)) {
                throw JSError.JavaException("NotSupportedError", "This constructor has already been used with this registry.")
            }
            val observed = extractObservedAttributes(ctor)
            val def = Definition(rawName, ctor, observed)
            definitionsByName[rawName] = def
            definitionsByCtor[ctor] = def

            engine.document.documentElement?.let { root ->
                upgradeMatchingSubtree(root, rawName, def)
            }
            val waiters = pendingWhenDefined.remove(rawName)
            if (waiters != null) {
                for (resolveWaiter in waiters) {
                    resolveWaiter(ctor)
                }
            }
            Undefined
        })

        ceObj.setHidden("get", fn("get") { _, params ->
            val name = params.getOrNull(0)?.toString().orEmpty()
            definitionsByName[name]?.ctor ?: Undefined
        })

        ceObj.setHidden("whenDefined", fn("whenDefined") { _, params ->
            val name = params.getOrNull(0)?.toString().orEmpty()
            val promise = JSPromise.Instance(jsGlobal, jsGlobal.Promise)
            if (!isValidCustomElementName(name)) {
                val err = (jsGlobal.get("DOMException", Scopable.OR_NULL) as? BaseFunction)
                    ?.construct(jsGlobal.wrap("'$name' is not a valid custom element name"), jsGlobal.wrap("SyntaxError"))
                    ?: jsGlobal.wrap("SyntaxError: Invalid custom element name '$name'")
                promise.reject(err)
                return@fn promise
            }
            val existing = definitionsByName[name]
            if (existing != null) {
                promise.resolve(existing.ctor)
            } else {
                pendingWhenDefined.getOrPut(name) { ArrayList() }.add { resolvedCtor ->
                    promise.resolve(resolvedCtor)
                }
            }
            promise
        })

        ceObj.setHidden("upgrade", fn("upgrade") { _, params ->
            val targetNode = registry.unwrapNode(params.getOrNull(0)) ?: return@fn Undefined
            upgradeAnyDefinedSubtree(targetNode)
            Undefined
        })

        windowObj.setHidden("CustomElementRegistry", registryCtor)
        jsGlobal.setHidden("CustomElementRegistry", registryCtor)
        windowObj.setHidden("customElements", ceObj)
        jsGlobal.setHidden("customElements", ceObj)
    }

    fun clearForNewDocument() {
        definitionsByName.clear()
        definitionsByCtor.clear()
        upgradedElements.clear()
        currentlyUpgrading.clear()
        pendingWhenDefined.clear()
    }

    internal fun getDefinitionForElement(el: WebDomElement): Definition? =
        if ('-' in el.lowerTagName) definitionsByName[el.lowerTagName] else null

    fun isUpgradedOrUpgrading(el: WebDomElement): Boolean =
        el in upgradedElements || el in currentlyUpgrading

    internal fun getConstructorForTag(tagName: String): BaseFunction? =
        if ('-' in tagName) definitionsByName[tagName.lowercase()]?.ctor else null

    internal fun handleHtmlElementSuperConstruct(targetJs: GenericObject): Boolean {
        val ctor = targetJs.get("constructor", Scopable.OR_NULL) as? BaseFunction ?: return false
        val def = definitionsByCtor[ctor] ?: return false
        val el = engine.document.createElement(def.name)
        upgradedElements.add(el)
        registry.bindCustomElementWrapper(el, targetJs)
        return true
    }

    internal fun upgradeElementIfNeeded(el: WebDomElement, existingJs: GenericObject? = null) {
        val def = getDefinitionForElement(el) ?: return
        upgradeElement(el, def, existingJs)
    }

    internal fun onElementConnected(el: WebDomElement) {
        val def = getDefinitionForElement(el) ?: return
        if (el !in upgradedElements) {
            upgradeElement(el, def, null)
        } else if (el !in currentlyUpgrading) {
            registry.findWrappedNode(el)?.let { invokeLifecycleCallback(it, "connectedCallback") }
        }
    }

    internal fun onElementDisconnected(el: WebDomElement) {
        if (el !in upgradedElements) return
        registry.findWrappedNode(el)?.let { invokeLifecycleCallback(it, "disconnectedCallback") }
    }

    internal fun onAttributeChanged(el: WebDomElement, attrName: String, oldVal: String?, newVal: String?) {
        if (oldVal == newVal || el !in upgradedElements || el in currentlyUpgrading) return
        val def = definitionsByName[el.lowerTagName] ?: return
        if (attrName !in def.observedAttributes) return
        val js = registry.findWrappedNode(el) ?: return
        invokeLifecycleCallback(
            js,
            "attributeChangedCallback",
            jsGlobal.wrap(attrName),
            oldVal?.let { jsGlobal.wrap(it) } ?: Null,
            newVal?.let { jsGlobal.wrap(it) } ?: Null
        )
    }

    private fun upgradeMatchingSubtree(node: WebDomNode, targetTag: String, def: Definition) {
        if (node is WebDomElement) {
            if (node.lowerTagName == targetTag) {
                upgradeElement(node, def, null)
            }
            node.shadowRoot?.let { sr ->
                for (sc in sr.childNodes.toList()) {
                    upgradeMatchingSubtree(sc, targetTag, def)
                }
            }
        }
        for (c in node.childNodes.toList()) {
            upgradeMatchingSubtree(c, targetTag, def)
        }
    }

    private fun upgradeAnyDefinedSubtree(node: WebDomNode) {
        if (node is WebDomElement) {
            val def = getDefinitionForElement(node)
            if (def != null) {
                upgradeElement(node, def, null)
            }
            node.shadowRoot?.let { sr ->
                for (sc in sr.childNodes.toList()) {
                    upgradeAnyDefinedSubtree(sc)
                }
            }
        }
        for (c in node.childNodes.toList()) {
            upgradeAnyDefinedSubtree(c)
        }
    }

    private fun upgradeElement(el: WebDomElement, def: Definition, providedJs: GenericObject?) {
        if (el in upgradedElements || el in currentlyUpgrading) return
        currentlyUpgrading.add(el)
        try {
            val js = providedJs ?: registry.ensureElementWrapperWithoutUpgrade(el, def.ctor)
            val proto = def.ctor.get("prototype", Scopable.OR_NULL) as? GenericObject
            if (proto != null) {
                js.setPrototypeOf(proto)
            }
            js.setHidden("constructor", def.ctor)
            upgradedElements.add(el)
            try {
                def.ctor.call(js)
            } catch (t: Throwable) {
                onError(t)
            }
            if (def.observedAttributes.isNotEmpty()) {
                for ((attrKey, attrVal) in el.attributesMap.toList()) {
                    if (attrKey in def.observedAttributes) {
                        invokeLifecycleCallback(
                            js,
                            "attributeChangedCallback",
                            jsGlobal.wrap(attrKey),
                            Null,
                            jsGlobal.wrap(attrVal)
                        )
                    }
                }
            }
            if (el.isConnected) {
                invokeLifecycleCallback(js, "connectedCallback")
            }
        } finally {
            currentlyUpgrading.remove(el)
        }
    }

    private fun invokeLifecycleCallback(js: GenericObject, methodName: String, vararg args: BaseObject) {
        val fnObj = js.get(methodName, Scopable.OR_NULL) as? BaseFunction ?: return
        try {
            fnObj.call(js, *args)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    private fun extractObservedAttributes(ctor: BaseFunction): Set<String> {
        val raw = try {
            ctor.get("observedAttributes", Scopable.OR_NULL)
        } catch (_: Throwable) {
            null
        } ?: return emptySet()
        if (raw === Null || raw === Undefined) return emptySet()
        val len = raw.get("length", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toInt() ?: 0
        if (len <= 0) return emptySet()
        val out = LinkedHashSet<String>(len)
        for (i in 0 until len) {
            val item = raw.get(i).takeIf { it !== Undefined && it !== Null }?.toString()?.lowercase()?.trim()
            if (!item.isNullOrEmpty()) out.add(item)
        }
        return out
    }

    private fun validateCustomElementName(name: String) {
        if (!isValidCustomElementName(name)) {
            throw JSError.JavaException("SyntaxError", "'$name' is not a valid custom element name")
        }
    }

    private fun isValidCustomElementName(name: String): Boolean {
        if (name.isEmpty() || name in RESERVED_NAMES) return false
        val first = name[0]
        if (first !in 'a'..'z') return false
        if ('-' !in name) return false
        for (c in name) {
            if (c in 'A'..'Z') return false
        }
        return true
    }

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
