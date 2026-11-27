package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.webview.desktop.dom.WebDomComment
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocumentFragment
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocumentType
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomNode
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomShadowRoot
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomText
import com.nexustools.genericui.ext.webview.desktop.dom.WebHtml5Parser
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.JSError
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Or
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Undefined

/**
 * Identity-preserving NJS wrapper cache (`WebDomNode <-> GenericObject`) providing W3C `Node`,
 * `Element`, `HTMLElement`, `Text`, `Comment`, `DocumentFragment`, `ShadowRoot`, `Document`,
 * `classList`, `dataset`, inline `style`, HTML5 form controls & validation, `<template>`, tables,
 * and dynamic `<canvas>` / `<video>` / `<audio>` / `<img>` / `<iframe>` hooks.
 */
class NjsDomNodeRegistry(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val events: NjsDomEventSystem
) {
    private val domToJs = LinkedHashMap<WebDomNode, GenericObject>()
    private val jsToDom = LinkedHashMap<BaseObject, WebDomNode>()

    var canvasContextFactory: ((WebDomElement, String, BaseObject?) -> BaseObject)? = null
    var mediaBindHook: ((WebDomElement, GenericObject) -> Unit)? = null
    var imageBindHook: ((WebDomElement, GenericObject) -> Unit)? = null
    var iframeBindHook: ((WebDomElement, GenericObject) -> Unit)? = null
    var geometryBindHook: ((WebDomElement, GenericObject) -> Unit)? = null
    var documentCssomBindHook: ((WebDomDocument, GenericObject) -> Unit)? = null
    internal var customElementsRegistry: NjsCustomElementsRegistry? = null

    init {
        events.nodeResolver = { node -> wrapNode(node) }
        events.reverseNodeResolver = { js -> jsToDom[js] }
        events.onFormSubmit = { formEl ->
            val allowed = events.dispatchSimpleEvent(formEl, "submit", bubbles = true, cancelable = true)
            @Suppress("UNUSED_VARIABLE")
            val unused = allowed
        }
    }

    fun installConstructors() {
        val nodeCtor = markerCtor("Node")
        val elemCtor = markerCtor("Element", nodeCtor)
        val htmlElemCtor = object : AbstractFunction(jsGlobal) {
            init {
                (prototype() as? GenericObject)?.setPrototypeOf(elemCtor.prototype())
            }
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                if (_this is GenericObject && jsToDom[_this] == null) {
                    customElementsRegistry?.handleHtmlElementSuperConstruct(_this)
                }
                return _this
            }
            override fun construct(vararg params: BaseObject): BaseObject {
                throw JSError.JavaException("TypeError", "Illegal constructor")
            }
            override fun name(): String = "HTMLElement"
        }
        val docCtor = markerCtor("Document", nodeCtor)
        val fragCtor = markerCtor("DocumentFragment", nodeCtor)
        val shadowCtor = markerCtor("ShadowRoot", fragCtor)
        jsGlobal.setHidden("Node", nodeCtor)
        jsGlobal.setHidden("Element", elemCtor)
        jsGlobal.setHidden("HTMLElement", htmlElemCtor)
        jsGlobal.setHidden("Document", docCtor)
        jsGlobal.setHidden("DocumentFragment", fragCtor)
        jsGlobal.setHidden("ShadowRoot", shadowCtor)
    }

    fun clearCache() {
        domToJs.clear()
        jsToDom.clear()
    }

    internal fun findWrappedNode(node: WebDomNode): GenericObject? = domToJs[node]

    internal fun bindCustomElementWrapper(el: WebDomElement, js: GenericObject) {
        domToJs[el] = js
        jsToDom[js] = el
        attachCommonNodeApis(el, js)
        events.attachEventTargetMethods(el, js)
        attachElementApis(el, js)
    }

    internal fun ensureElementWrapperWithoutUpgrade(
        el: WebDomElement,
        overrideCtor: BaseFunction? = null
    ): GenericObject {
        domToJs[el]?.let { return it }
        val ctor = overrideCtor
            ?: (jsGlobal.get("HTMLElement", Scopable.OR_NULL) as? BaseFunction)
        val js = GenericObject(ctor, jsGlobal)
        bindCustomElementWrapper(el, js)
        return js
    }

    fun unwrapNode(jsObj: BaseObject?): WebDomNode? =
        if (jsObj == null) null else jsToDom[jsObj]

    fun unwrapElement(jsObj: BaseObject?): WebDomElement? =
        unwrapNode(jsObj) as? WebDomElement

    fun wrapNodeOrNull(node: WebDomNode?): BaseObject =
        if (node == null) Null else wrapNode(node)

    fun wrapNode(node: WebDomNode): GenericObject {
        domToJs[node]?.let { return it }

        if (node is WebDomElement) {
            val ce = customElementsRegistry
            val customCtor = ce?.getConstructorForTag(node.lowerTagName)
            val js = ensureElementWrapperWithoutUpgrade(node, customCtor)
            if (ce != null && !ce.isUpgradedOrUpgrading(node)) {
                ce.upgradeElementIfNeeded(node, js)
            }
            return js
        }

        val ctor = when (node) {
            is WebDomShadowRoot -> jsGlobal.get("ShadowRoot", Scopable.OR_NULL) as? BaseFunction
            is WebDomDocumentFragment -> jsGlobal.get("DocumentFragment", Scopable.OR_NULL) as? BaseFunction
            is WebDomDocument -> jsGlobal.get("Document", Scopable.OR_NULL) as? BaseFunction
            else -> jsGlobal.get("Node", Scopable.OR_NULL) as? BaseFunction
        }

        val js = GenericObject(ctor, jsGlobal)
        domToJs[node] = js
        jsToDom[js] = node

        attachCommonNodeApis(node, js)
        events.attachEventTargetMethods(node, js)

        when (node) {
            is WebDomDocument -> attachDocumentApis(node, js)
            is WebDomDocumentType -> {
                js.setHidden("name", jsGlobal.wrap(node.name))
                js.setHidden("publicId", jsGlobal.wrap(node.publicId))
                js.setHidden("systemId", jsGlobal.wrap(node.systemId))
            }
            is WebDomShadowRoot -> attachShadowRootApis(node, js)
            is WebDomDocumentFragment -> attachFragmentApis(node, js)
            is WebDomText, is WebDomComment -> attachCharacterDataApis(node, js)
        }
        return js
    }

    fun wrapElementList(elements: List<WebDomNode>): GenericArray =
        GenericArray(jsGlobal, jsGlobal.Array, elements.map { wrapNode(it) as BaseObject? }.toTypedArray()).also { listObj ->
            listObj.setHidden("item", fn("item") { _this, params ->
                val idx = params.getOrNull(0)?.toInt() ?: -1
                if (idx in 0 until _this.get("length").toInt()) _this.get(idx) else Null
            })
        }

    private fun attachCommonNodeApis(node: WebDomNode, js: GenericObject) {
        js.setHidden("nodeType", jsGlobal.wrap(node.nodeType.toDouble()))
        js.setHidden("nodeName", jsGlobal.wrap(node.nodeName))
        js.defineProperty("nodeValue", fn("get_nodeValue") { _, _ ->
            node.nodeValue?.let { jsGlobal.wrap(it) } ?: Null
        }, fn("set_nodeValue") { _, args ->
            node.nodeValue = args.getOrNull(0)?.takeIf { it !== Null && it !== Undefined }?.toString().orEmpty()
            Undefined
        })
        js.defineProperty("textContent", fn("get_textContent") { _, _ ->
            if (node is WebDomDocument || node is WebDomDocumentType) Null else jsGlobal.wrap(node.textContent)
        }, fn("set_textContent") { _, args ->
            node.textContent = args.getOrNull(0)?.takeIf { it !== Null && it !== Undefined }?.toString().orEmpty()
            Undefined
        })
        js.defineProperty("ownerDocument", fn("get_ownerDocument") { _, _ ->
            wrapNodeOrNull(node.ownerDocument)
        }, jsGlobal.NOOP)
        js.defineProperty("baseURI", fn("get_baseURI") { _, _ ->
            val rawDocUrl = node.ownerDocument?.url ?: engine.document.url
            jsGlobal.wrap(engine.resolveDocumentBaseUrl(rawDocUrl))
        }, jsGlobal.NOOP)
        js.defineProperty("parentNode", fn("get_parentNode") { _, _ -> wrapNodeOrNull(node.parentNode) }, jsGlobal.NOOP)
        js.defineProperty("parentElement", fn("get_parentElement") { _, _ -> wrapNodeOrNull(node.parentElement) }, jsGlobal.NOOP)
        js.defineProperty("firstChild", fn("get_firstChild") { _, _ -> wrapNodeOrNull(node.firstChild) }, jsGlobal.NOOP)
        js.defineProperty("lastChild", fn("get_lastChild") { _, _ -> wrapNodeOrNull(node.lastChild) }, jsGlobal.NOOP)
        js.defineProperty("previousSibling", fn("get_previousSibling") { _, _ -> wrapNodeOrNull(node.previousSibling) }, jsGlobal.NOOP)
        js.defineProperty("nextSibling", fn("get_nextSibling") { _, _ -> wrapNodeOrNull(node.nextSibling) }, jsGlobal.NOOP)
        js.defineProperty("isConnected", fn("get_isConnected") { _, _ -> jsGlobal.wrap(node.isConnected) }, jsGlobal.NOOP)
        js.defineProperty("childNodes", fn("get_childNodes") { _, _ -> wrapElementList(node.childNodes) }, jsGlobal.NOOP)
        js.defineProperty("children", fn("get_children") { _, _ -> wrapElementList(node.children) }, jsGlobal.NOOP)
        js.defineProperty("childElementCount", fn("get_childElementCount") { _, _ -> jsGlobal.wrap(node.childElementCount.toDouble()) }, jsGlobal.NOOP)
        js.defineProperty("firstElementChild", fn("get_firstElementChild") { _, _ -> wrapNodeOrNull(node.firstElementChild) }, jsGlobal.NOOP)
        js.defineProperty("lastElementChild", fn("get_lastElementChild") { _, _ -> wrapNodeOrNull(node.lastElementChild) }, jsGlobal.NOOP)
        js.defineProperty("previousElementSibling", fn("get_previousElementSibling") { _, _ -> wrapNodeOrNull(node.previousElementSibling) }, jsGlobal.NOOP)
        js.defineProperty("nextElementSibling", fn("get_nextElementSibling") { _, _ -> wrapNodeOrNull(node.nextElementSibling) }, jsGlobal.NOOP)

        js.setHidden("hasChildNodes", fn("hasChildNodes") { _, _ -> jsGlobal.wrap(node.hasChildNodes()) })
        js.setHidden("contains", fn("contains") { _, p -> jsGlobal.wrap(node.contains(unwrapNode(p.getOrNull(0)))) })
        js.setHidden("appendChild", fn("appendChild") { _, p ->
            val child = requireDomNode(p.getOrNull(0))
            node.appendChild(child)
            wrapNode(child)
        })
        js.setHidden("insertBefore", fn("insertBefore") { _, p ->
            val newChild = requireDomNode(p.getOrNull(0))
            val ref = p.getOrNull(1)?.takeIf { it !== Null && it !== Undefined }?.let { requireDomNode(it) }
            node.insertBefore(newChild, ref)
            wrapNode(newChild)
        })
        js.setHidden("removeChild", fn("removeChild") { _, p ->
            val child = requireDomNode(p.getOrNull(0))
            node.removeChild(child)
            wrapNode(child)
        })
        js.setHidden("replaceChild", fn("replaceChild") { _, p ->
            val newC = requireDomNode(p.getOrNull(0))
            val oldC = requireDomNode(p.getOrNull(1))
            node.replaceChild(newC, oldC)
            wrapNode(oldC)
        })
        js.setHidden("normalize", fn("normalize") { _, _ -> node.normalize(); Undefined })
        js.setHidden("cloneNode", fn("cloneNode") { _, p ->
            val deep = p.getOrNull(0)?.toBool() ?: false
            wrapNode(node.cloneNode(deep))
        })
        js.setHidden("compareDocumentPosition", fn("compareDocumentPosition") { _, p ->
            val other = unwrapNode(p.getOrNull(0)) ?: return@fn jsGlobal.wrap(1.0)
            jsGlobal.wrap(node.compareDocumentPosition(other).toDouble())
        })
        js.setHidden("getRootNode", fn("getRootNode") { _, p ->
            val arg = p.getOrNull(0)
            val composed = (arg as? GenericObject)?.get("composed", Scopable.OR_NULL)?.toBool() ?: false
            wrapNode(node.getRootNode(composed))
        })
    }

    private fun attachCharacterDataApis(node: WebDomNode, js: GenericObject) {
        js.defineProperty("data", fn("get_data") { _, _ ->
            jsGlobal.wrap(when (node) {
                is WebDomText -> node.data
                is WebDomComment -> node.data
                else -> ""
            })
        }, fn("set_data") { _, p ->
            val s = p.getOrNull(0)?.toString().orEmpty()
            node.textContent = s
            Undefined
        })
        js.defineProperty("length", fn("get_length") { _, _ ->
            jsGlobal.wrap(node.textContent.length.toDouble())
        }, jsGlobal.NOOP)
        if (node is WebDomText) {
            js.setHidden("splitText", fn("splitText") { _, p ->
                wrapNode(node.splitText(p.getOrNull(0)?.toInt() ?: 0))
            })
        }
    }

    private fun attachFragmentApis(frag: WebDomDocumentFragment, js: GenericObject) {
        js.setHidden("getElementById", fn("getElementById") { _, p ->
            wrapNodeOrNull(frag.getElementById(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("querySelector", fn("querySelector") { _, p ->
            wrapNodeOrNull(frag.querySelector(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("querySelectorAll", fn("querySelectorAll") { _, p ->
            wrapElementList(frag.querySelectorAll(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("append", fn("append") { _, params ->
            for (p in params) appendNodeOrText(frag, p)
            Undefined
        })
        js.setHidden("prepend", fn("prepend") { _, params ->
            val first = frag.firstChild
            for (p in params) insertNodeOrTextBefore(frag, p, first)
            Undefined
        })
    }

    private fun attachShadowRootApis(sr: WebDomShadowRoot, js: GenericObject) {
        attachFragmentApis(sr, js)
        js.setHidden("mode", jsGlobal.wrap(sr.mode))
        js.setHidden("delegatesFocus", jsGlobal.wrap(sr.delegatesFocus))
        js.defineProperty("host", fn("get_host") { _, _ -> wrapNode(sr.host) }, jsGlobal.NOOP)
        js.defineProperty("innerHTML", fn("get_innerHTML") { _, _ -> jsGlobal.wrap(sr.innerHTML) },
            fn("set_innerHTML") { _, p -> sr.innerHTML = p.getOrNull(0)?.toString().orEmpty(); Undefined })
    }

    private fun attachDocumentApis(doc: WebDomDocument, js: GenericObject) {
        js.defineProperty("doctype", fn("get_doctype") { _, _ -> wrapNodeOrNull(doc.doctype) }, jsGlobal.NOOP)
        js.defineProperty("documentElement", fn("get_documentElement") { _, _ -> wrapNodeOrNull(doc.documentElement) }, jsGlobal.NOOP)
        js.defineProperty("head", fn("get_head") { _, _ -> wrapNodeOrNull(doc.head) }, jsGlobal.NOOP)
        js.defineProperty("body", fn("get_body") { _, _ -> wrapNodeOrNull(doc.body) }, jsGlobal.NOOP)
        js.defineProperty("scrollingElement", fn("get_scrollingElement") { _, _ -> wrapNodeOrNull(doc.scrollingElement) }, jsGlobal.NOOP)
        js.defineProperty("activeElement", fn("get_activeElement") { _, _ ->
            wrapNodeOrNull(doc.activeElement ?: doc.body)
        }, jsGlobal.NOOP)
        js.defineProperty("readyState", fn("get_readyState") { _, _ -> jsGlobal.wrap(doc.readyState) }, jsGlobal.NOOP)
        js.defineProperty("title", fn("get_title") { _, _ -> jsGlobal.wrap(doc.title) },
            fn("set_title") { _, p -> doc.title = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("URL", fn("get_URL") { _, _ -> jsGlobal.wrap(doc.url) }, jsGlobal.NOOP)
        js.defineProperty("documentURI", fn("get_documentURI") { _, _ -> jsGlobal.wrap(doc.url) }, jsGlobal.NOOP)
        js.defineProperty("baseURI", fn("get_baseURI") { _, _ -> jsGlobal.wrap(engine.resolveDocumentBaseUrl(doc.url)) }, jsGlobal.NOOP)
        js.defineProperty("currentScript", fn("get_currentScript") { _, _ -> wrapNodeOrNull(doc.currentScript) }, jsGlobal.NOOP)
        js.defineProperty("domain", fn("get_domain") { _, _ -> jsGlobal.wrap(doc.getDomain()) }, jsGlobal.NOOP)
        js.defineProperty("characterSet", fn("get_charset") { _, _ -> jsGlobal.wrap(doc.characterSet) }, jsGlobal.NOOP)
        js.defineProperty("compatMode", fn("get_compatMode") { _, _ -> jsGlobal.wrap(doc.compatMode) }, jsGlobal.NOOP)
        js.defineProperty("hidden", fn("get_hidden") { _, _ -> jsGlobal.wrap(doc.hidden) }, jsGlobal.NOOP)
        js.defineProperty("visibilityState", fn("get_visibilityState") { _, _ -> jsGlobal.wrap(doc.visibilityState) }, jsGlobal.NOOP)
        js.defineProperty("cookie", fn("get_cookie") { _, _ -> jsGlobal.wrap(doc.cookieString) },
            fn("set_cookie") { _, p -> doc.cookieString = p.getOrNull(0)?.toString().orEmpty(); Undefined })

        js.setHidden("createElement", fn("createElement") { _, p ->
            wrapNode(doc.createElement(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("createElementNS", fn("createElementNS") { _, p ->
            val ns = p.getOrNull(0)?.takeIf { it !== Null && it !== Undefined }?.toString()
            val qName = p.getOrNull(1)?.toString().orEmpty()
            wrapNode(doc.createElementNS(ns, qName))
        })
        js.setHidden("createTextNode", fn("createTextNode") { _, p ->
            wrapNode(doc.createTextNode(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("createComment", fn("createComment") { _, p ->
            wrapNode(doc.createComment(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("createDocumentFragment", fn("createDocumentFragment") { _, _ ->
            wrapNode(doc.createDocumentFragment())
        })
        js.setHidden("importNode", fn("importNode") { _, p ->
            val n = requireDomNode(p.getOrNull(0))
            val deep = p.getOrNull(1)?.toBool() ?: false
            wrapNode(doc.importNode(n, deep))
        })
        js.setHidden("adoptNode", fn("adoptNode") { _, p ->
            val n = requireDomNode(p.getOrNull(0))
            wrapNode(doc.adoptNode(n))
        })
        js.setHidden("getElementById", fn("getElementById") { _, p ->
            wrapNodeOrNull(doc.getElementById(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("getElementsByTagName", fn("getElementsByTagName") { _, p ->
            wrapElementList(doc.getElementsByTagName(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("getElementsByClassName", fn("getElementsByClassName") { _, p ->
            wrapElementList(doc.getElementsByClassName(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("getElementsByName", fn("getElementsByName") { _, p ->
            wrapElementList(doc.getElementsByName(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("querySelector", fn("querySelector") { _, p ->
            wrapNodeOrNull(doc.querySelector(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("querySelectorAll", fn("querySelectorAll") { _, p ->
            wrapElementList(doc.querySelectorAll(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("elementFromPoint", fn("elementFromPoint") { _, p ->
            val x = p.getOrNull(0)?.toDouble() ?: 0.0
            val y = p.getOrNull(1)?.toDouble() ?: 0.0
            wrapNodeOrNull(doc.elementFromPoint(x, y))
        })
        js.setHidden("elementsFromPoint", fn("elementsFromPoint") { _, p ->
            val x = p.getOrNull(0)?.toDouble() ?: 0.0
            val y = p.getOrNull(1)?.toDouble() ?: 0.0
            wrapElementList(doc.elementsFromPoint(x, y))
        })

        val implObj = GenericObject(jsGlobal).apply {
            setHidden("createHTMLDocument", fn("createHTMLDocument") { _, p ->
                val newDoc = WebDomDocument(doc.url, doc.allowStorage)
                WebHtml5Parser.parseDocument("<!DOCTYPE html><html><head><title></title></head><body></body></html>", newDoc)
                p.getOrNull(0)?.takeIf { it !== Undefined && it !== Null }?.toString()?.let { newDoc.title = it }
                wrapNode(newDoc)
            })
        }
        js.setHidden("implementation", implObj)
        documentCssomBindHook?.invoke(doc, js)
    }

    private fun attachElementApis(el: WebDomElement, js: GenericObject) {
        js.setHidden("tagName", jsGlobal.wrap(el.tagName))
        js.setHidden("localName", jsGlobal.wrap(el.localName))
        js.setHidden("namespaceURI", el.namespaceURI?.let { jsGlobal.wrap(it) } ?: Null)

        js.defineProperty("id", fn("get_id") { _, _ -> jsGlobal.wrap(el.id) },
            fn("set_id") { _, p -> el.id = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("className", fn("get_className") { _, _ -> jsGlobal.wrap(el.className) },
            fn("set_className") { _, p -> el.className = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("innerHTML", fn("get_innerHTML") { _, _ -> jsGlobal.wrap(el.innerHTML) },
            fn("set_innerHTML") { _, p -> el.innerHTML = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("outerHTML", fn("get_outerHTML") { _, _ -> jsGlobal.wrap(el.outerHTML) },
            fn("set_outerHTML") { _, p -> el.outerHTML = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("hidden", fn("get_hidden") { _, _ -> jsGlobal.wrap(el.hasAttribute("hidden")) },
            fn("set_hidden") { _, p -> if (p.getOrNull(0)?.toBool() == true) el.setAttribute("hidden", "") else el.removeAttribute("hidden"); Undefined })

        js.setHidden("getAttribute", fn("getAttribute") { _, p ->
            el.getAttribute(p.getOrNull(0)?.toString().orEmpty())?.let { jsGlobal.wrap(it) } ?: Null
        })
        js.setHidden("setAttribute", fn("setAttribute") { _, p ->
            el.setAttribute(p.getOrNull(0)?.toString().orEmpty(), p.getOrNull(1)?.toString().orEmpty())
            Undefined
        })
        js.setHidden("removeAttribute", fn("removeAttribute") { _, p ->
            el.removeAttribute(p.getOrNull(0)?.toString().orEmpty())
            Undefined
        })
        js.setHidden("hasAttribute", fn("hasAttribute") { _, p ->
            jsGlobal.wrap(el.hasAttribute(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("toggleAttribute", fn("toggleAttribute") { _, p ->
            val name = p.getOrNull(0)?.toString().orEmpty()
            val force = p.getOrNull(1)?.takeIf { it !== Undefined }?.toBool()
            jsGlobal.wrap(el.toggleAttribute(name, force))
        })
        js.setHidden("getAttributeNames", fn("getAttributeNames") { _, _ ->
            GenericArray(jsGlobal, jsGlobal.Array, el.getAttributeNames().map { jsGlobal.wrap(it) as BaseObject? }.toTypedArray())
        })
        js.setHidden("setAttributeNS", fn("setAttributeNS") { _, p ->
            el.setAttribute(p.getOrNull(1)?.toString()?.substringAfter(':') ?: "", p.getOrNull(2)?.toString().orEmpty())
            Undefined
        })
        js.setHidden("getAttributeNS", fn("getAttributeNS") { _, p ->
            el.getAttribute(p.getOrNull(1)?.toString()?.substringAfter(':') ?: "")?.let { jsGlobal.wrap(it) } ?: Null
        })

        // NamedNodeMap-like live attributes object
        val attrsProxy = object : GenericObject(jsGlobal) {
            override fun get(key: String, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? {
                if (key == "length") return jsGlobal.wrap(el.attributesMap.size.toDouble())
                val idx = key.toIntOrNull()
                if (idx != null) {
                    val entry = el.attributesMap.entries.elementAtOrNull(idx) ?: return Null
                    return attrNodeWrapper(entry.key, entry.value)
                }
                el.attributesMap[key.lowercase()]?.let { return attrNodeWrapper(key.lowercase(), it) }
                return super.get(key, _this, or)
            }
            override fun get(index: Int, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? {
                val entry = el.attributesMap.entries.elementAtOrNull(index) ?: return Null
                return attrNodeWrapper(entry.key, entry.value)
            }
        }
        js.setHidden("attributes", attrsProxy)

        // classList (DOMTokenList)
        js.setHidden("classList", buildClassListObject(el))
        // dataset (DOMStringMap)
        js.setHidden("dataset", buildDatasetObject(el))
        // style (CSSStyleDeclaration)
        js.setHidden("style", buildInlineStyleObject(el))

        // Selector & traversal methods
        js.setHidden("querySelector", fn("querySelector") { _, p ->
            wrapNodeOrNull(el.querySelector(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("querySelectorAll", fn("querySelectorAll") { _, p ->
            wrapElementList(el.querySelectorAll(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("getElementsByTagName", fn("getElementsByTagName") { _, p ->
            val out = ArrayList<WebDomElement>()
            el.collectDescendantsByTagName(p.getOrNull(0)?.toString().orEmpty(), out)
            wrapElementList(out)
        })
        js.setHidden("getElementsByClassName", fn("getElementsByClassName") { _, p ->
            val tokens = p.getOrNull(0)?.toString().orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val out = ArrayList<WebDomElement>()
            el.collectDescendantsByClassName(tokens, out)
            wrapElementList(out)
        })
        js.setHidden("matches", fn("matches") { _, p ->
            jsGlobal.wrap(el.matches(p.getOrNull(0)?.toString().orEmpty()))
        })
        js.setHidden("closest", fn("closest") { _, p ->
            wrapNodeOrNull(el.closest(p.getOrNull(0)?.toString().orEmpty()))
        })

        // DOM modern convenience mutations
        js.setHidden("remove", fn("remove") { _, _ -> el.remove(); Undefined })
        js.setHidden("append", fn("append") { _, params -> for (p in params) appendNodeOrText(el, p); Undefined })
        js.setHidden("prepend", fn("prepend") { _, params ->
            val first = el.firstChild
            for (p in params) insertNodeOrTextBefore(el, p, first)
            Undefined
        })
        js.setHidden("before", fn("before") { _, params ->
            val pNode = el.parentNode ?: return@fn Undefined
            for (p in params) insertNodeOrTextBefore(pNode, p, el)
            Undefined
        })
        js.setHidden("after", fn("after") { _, params ->
            val pNode = el.parentNode ?: return@fn Undefined
            val next = el.nextSibling
            for (p in params) insertNodeOrTextBefore(pNode, p, next)
            Undefined
        })
        js.setHidden("replaceWith", fn("replaceWith") { _, params ->
            val pNode = el.parentNode ?: return@fn Undefined
            val next = el.nextSibling
            pNode.removeChild(el)
            for (p in params) insertNodeOrTextBefore(pNode, p, next)
            Undefined
        })
        js.setHidden("insertAdjacentHTML", fn("insertAdjacentHTML") { _, p ->
            val position = p.getOrNull(0)?.toString()?.lowercase().orEmpty()
            val html = p.getOrNull(1)?.toString().orEmpty()
            val doc = el.ownerDocument ?: engine.document
            val ctx = if (position == "beforebegin" || position == "afterend") el.parentElement else el
            val parsed = WebHtml5Parser.parseFragment(html, ctx, doc)
            when (position) {
                "beforebegin" -> el.parentNode?.let { par -> for (n in parsed) par.insertBefore(n, el) }
                "afterbegin" -> {
                    val first = el.firstChild
                    for (n in parsed) el.insertBefore(n, first)
                }
                "beforeend" -> for (n in parsed) el.appendChild(n)
                "afterend" -> el.parentNode?.let { par ->
                    val nxt = el.nextSibling
                    for (n in parsed) par.insertBefore(n, nxt)
                }
            }
            Undefined
        })

        // Focus & click
        js.setHidden("focus", fn("focus") { _, _ ->
            val doc = el.ownerDocument
            val prev = doc?.activeElement
            if (prev != null && prev !== el) {
                prev.isFocusedState = false
                events.dispatchSimpleEvent(prev, "blur", bubbles = false, composed = true)
                events.dispatchSimpleEvent(prev, "focusout", bubbles = true, composed = true)
            }
            el.isFocusedState = true
            doc?.activeElement = el
            events.dispatchSimpleEvent(el, "focus", bubbles = false, composed = true)
            events.dispatchSimpleEvent(el, "focusin", bubbles = true, composed = true)
            Undefined
        })
        js.setHidden("blur", fn("blur") { _, _ ->
            if (el.isFocusedState || el.ownerDocument?.activeElement === el) {
                el.isFocusedState = false
                if (el.ownerDocument?.activeElement === el) el.ownerDocument?.activeElement = null
                events.dispatchSimpleEvent(el, "blur", bubbles = false, composed = true)
                events.dispatchSimpleEvent(el, "focusout", bubbles = true, composed = true)
            }
            Undefined
        })
        js.setHidden("click", fn("click") { _, _ ->
            events.dispatchSimpleEvent(el, "click", bubbles = true, cancelable = true, composed = true)
            Undefined
        })

        // Shadow DOM
        js.setHidden("attachShadow", fn("attachShadow") { _, p ->
            if (!engine.capabilities.shadowDom) {
                throw JSError.JavaException("NotSupportedError", "Shadow DOM is disabled by runtime capabilities")
            }
            val initObj = p.getOrNull(0)
            val mode = initObj?.get("mode", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toString() ?: "open"
            val delegatesFocus = initObj?.get("delegatesFocus", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
            val sr = el.attachShadow(mode, delegatesFocus)
            wrapNode(sr)
        })
        js.defineProperty("shadowRoot", fn("get_shadowRoot") { _, _ ->
            val sr = el.shadowRoot
            if (sr != null && sr.mode == "open") wrapNode(sr) else Null
        }, jsGlobal.NOOP)

        if (el.lowerTagName == "slot") {
            js.setHidden("assignedNodes", fn("assignedNodes") { _, p ->
                val flatten = (p.getOrNull(0) as? GenericObject)?.get("flatten", Scopable.OR_NULL)?.toBool() ?: false
                val sr = el.getRootNode(false) as? WebDomShadowRoot
                val nodes = sr?.assignedNodesForSlot(el, flatten).orEmpty()
                wrapElementList(nodes)
            })
            js.setHidden("assignedElements", fn("assignedElements") { _, p ->
                val flatten = (p.getOrNull(0) as? GenericObject)?.get("flatten", Scopable.OR_NULL)?.toBool() ?: false
                val sr = el.getRootNode(false) as? WebDomShadowRoot
                val nodes = sr?.assignedNodesForSlot(el, flatten).orEmpty().filterIsInstance<WebDomElement>()
                wrapElementList(nodes)
            })
        }

        // <template>
        if (el.isTemplateTag) {
            js.defineProperty("content", fn("get_content") { _, _ -> wrapNode(el.templateContent) }, jsGlobal.NOOP)
        }

        // Geometry & Box properties
        geometryBindHook?.invoke(el, js)

        // Specialized element APIs
        when (el.lowerTagName) {
            "form" -> attachFormApis(el, js)
            "input", "textarea", "select", "option", "button", "fieldset" -> attachFormControlApis(el, js)
            "label" -> attachLabelApis(el, js)
            "table", "thead", "tbody", "tfoot", "tr", "td", "th" -> attachTableApis(el, js)
            "canvas" -> attachCanvasApis(el, js)
            "img" -> imageBindHook?.invoke(el, js)
            "video", "audio" -> mediaBindHook?.invoke(el, js)
            "iframe" -> iframeBindHook?.invoke(el, js)
            "style" -> attachStyleElementApis(el, js)
        }
    }

    private fun attachStyleElementApis(el: WebDomElement, js: GenericObject) {
        js.defineProperty("sheet", fn("get_sheet") { _, _ ->
            engine.ensureLayoutSynced()
            val model = el.ownerDocument?.styleSheets?.firstOrNull { it.ownerNode === el }
            if (model != null) {
                // Delegate via document styleSheet cache if bound
                val docJs = el.ownerDocument?.let { domToJs[it] }
                val sheets = docJs?.get("styleSheets", Scopable.OR_NULL)
                val len = sheets?.get("length")?.toInt() ?: 0
                for (i in 0 until len) {
                    val s = sheets?.get(i)
                    if (s?.get("ownerNode", Scopable.OR_NULL) === js) return@fn s
                }
            }
            Null
        }, jsGlobal.NOOP)
    }

    private fun attachCanvasApis(el: WebDomElement, js: GenericObject) {
        js.defineProperty("width", fn("get_width") { _, _ ->
            jsGlobal.wrap((el.getAttribute("width")?.toIntOrNull() ?: 300).toDouble())
        }, fn("set_width") { _, p ->
            el.setAttribute("width", (p.getOrNull(0)?.toInt() ?: 300).coerceAtLeast(1).toString())
            Undefined
        })
        js.defineProperty("height", fn("get_height") { _, _ ->
            jsGlobal.wrap((el.getAttribute("height")?.toIntOrNull() ?: 150).toDouble())
        }, fn("set_height") { _, p ->
            el.setAttribute("height", (p.getOrNull(0)?.toInt() ?: 150).coerceAtLeast(1).toString())
            Undefined
        })
        js.setHidden("getContext", fn("getContext") { _, p ->
            val kind = p.getOrNull(0)?.toString()?.lowercase().orEmpty()
            val attrs = p.getOrNull(1)
            canvasContextFactory?.invoke(el, kind, attrs) ?: Null
        })
        js.setHidden("toDataURL", fn("toDataURL") { _, p ->
            val mime = p.getOrNull(0)?.takeIf { it !== Undefined }?.toString() ?: "image/png"
            val q = p.getOrNull(1)?.takeIf { it !== Undefined }?.toDouble()?.toFloat() ?: 0.92f
            jsGlobal.wrap(engine.canvasToDataUrl(el, mime, q))
        })
    }

    private fun attachFormApis(formEl: WebDomElement, js: GenericObject) {
        js.defineProperty("elements", fn("get_elements") { _, _ ->
            val list = ArrayList<WebDomElement>()
            fun walk(n: WebDomNode) {
                for (c in n.childNodes) {
                    if (c is WebDomElement) {
                        if (c.lowerTagName in setOf("input", "select", "textarea", "button", "fieldset")) list.add(c)
                        walk(c)
                    }
                }
            }
            walk(formEl)
            wrapElementList(list)
        }, jsGlobal.NOOP)
        js.setHidden("checkValidity", fn("checkValidity") { _, _ ->
            var allOk = true
            fun walk(n: WebDomNode) {
                for (c in n.childNodes) {
                    if (c is WebDomElement) {
                        if (c.willValidate() && !c.isValidityValid()) {
                            events.dispatchSimpleEvent(c, "invalid", bubbles = false, cancelable = true)
                            allOk = false
                        }
                        walk(c)
                    }
                }
            }
            walk(formEl)
            jsGlobal.wrap(allOk)
        })
        js.setHidden("reportValidity", js.get("checkValidity"))
        js.setHidden("submit", fn("submit") { _, _ ->
            events.dispatchSimpleEvent(formEl, "submit", bubbles = true, cancelable = true)
            Undefined
        })
        js.setHidden("reset", fn("reset") { _, _ ->
            val allowed = events.dispatchSimpleEvent(formEl, "reset", bubbles = true, cancelable = true)
            if (allowed) {
                fun walk(n: WebDomNode) {
                    for (c in n.childNodes) {
                        if (c is WebDomElement) {
                            if (c.lowerTagName in setOf("input", "select", "textarea")) c.resetFormControl()
                            walk(c)
                        }
                    }
                }
                walk(formEl)
            }
            Undefined
        })
    }

    private fun attachFormControlApis(el: WebDomElement, js: GenericObject) {
        js.defineProperty("type", fn("get_type") { _, _ -> jsGlobal.wrap(el.inputType) },
            fn("set_type") { _, p -> el.inputType = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("name", fn("get_name") { _, _ -> jsGlobal.wrap(el.getAttribute("name").orEmpty()) },
            fn("set_name") { _, p -> el.setAttribute("name", p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("value", fn("get_value") { _, _ -> jsGlobal.wrap(el.value) },
            fn("set_value") { _, p -> el.value = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("defaultValue", fn("get_defaultValue") { _, _ -> jsGlobal.wrap(el.defaultValue) },
            fn("set_defaultValue") { _, p -> el.defaultValue = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        js.defineProperty("checked", fn("get_checked") { _, _ -> jsGlobal.wrap(el.checked) },
            fn("set_checked") { _, p -> el.checked = p.getOrNull(0)?.toBool() ?: false; Undefined })
        js.defineProperty("defaultChecked", fn("get_defaultChecked") { _, _ -> jsGlobal.wrap(el.defaultChecked) },
            fn("set_defaultChecked") { _, p -> el.defaultChecked = p.getOrNull(0)?.toBool() ?: false; Undefined })
        js.defineProperty("indeterminate", fn("get_indeterminate") { _, _ -> jsGlobal.wrap(el.isIndeterminateState) },
            fn("set_indeterminate") { _, p -> el.isIndeterminateState = p.getOrNull(0)?.toBool() ?: false; Undefined })
        js.defineProperty("disabled", fn("get_disabled") { _, _ -> jsGlobal.wrap(el.isEffectivelyDisabled()) },
            fn("set_disabled") { _, p -> if (p.getOrNull(0)?.toBool() == true) el.setAttribute("disabled", "") else el.removeAttribute("disabled"); Undefined })
        js.defineProperty("readOnly", fn("get_readOnly") { _, _ -> jsGlobal.wrap(el.hasAttribute("readonly")) },
            fn("set_readOnly") { _, p -> if (p.getOrNull(0)?.toBool() == true) el.setAttribute("readonly", "") else el.removeAttribute("readonly"); Undefined })
        js.defineProperty("required", fn("get_required") { _, _ -> jsGlobal.wrap(el.hasAttribute("required")) },
            fn("set_required") { _, p -> if (p.getOrNull(0)?.toBool() == true) el.setAttribute("required", "") else el.removeAttribute("required"); Undefined })
        js.defineProperty("form", fn("get_form") { _, _ -> wrapNodeOrNull(el.findOwningForm()) }, jsGlobal.NOOP)

        if (el.lowerTagName == "select") {
            js.defineProperty("multiple", fn("get_multiple") { _, _ -> jsGlobal.wrap(el.hasAttribute("multiple")) },
                fn("set_multiple") { _, p -> if (p.getOrNull(0)?.toBool() == true) el.setAttribute("multiple", "") else el.removeAttribute("multiple"); Undefined })
            js.defineProperty("options", fn("get_options") { _, _ -> wrapElementList(el.collectOptions()) }, jsGlobal.NOOP)
            js.defineProperty("selectedOptions", fn("get_selectedOptions") { _, _ ->
                val opts = el.collectOptions()
                val sel = opts.filter { it.checked }.ifEmpty {
                    if (!el.hasAttribute("multiple") && opts.isNotEmpty()) listOf(opts.first()) else emptyList()
                }
                wrapElementList(sel)
            }, jsGlobal.NOOP)
            js.defineProperty("selectedIndex", fn("get_selectedIndex") { _, _ ->
                val opts = el.collectOptions()
                val idx = opts.indexOfFirst { it.checked }.let { if (it < 0 && !el.hasAttribute("multiple") && opts.isNotEmpty()) 0 else it }
                jsGlobal.wrap(idx.toDouble())
            }, fn("set_selectedIndex") { _, p ->
                val targetIdx = p.getOrNull(0)?.toInt() ?: -1
                val opts = el.collectOptions()
                opts.forEachIndexed { idx, opt -> opt.checked = (idx == targetIdx) }
                Undefined
            })
        }

        if (el.lowerTagName == "textarea" || el.lowerTagName == "input") {
            js.defineProperty("selectionStart", fn("get_selStart") { _, _ ->
                val len = el.value.length
                jsGlobal.wrap(el.selectionStartPos.coerceIn(0, len).toDouble())
            }, fn("set_selStart") { _, p ->
                val len = el.value.length
                el.selectionStartPos = (p.getOrNull(0)?.toInt() ?: 0).coerceIn(0, len)
                if (el.selectionEndPos < el.selectionStartPos) el.selectionEndPos = el.selectionStartPos
                Undefined
            })
            js.defineProperty("selectionEnd", fn("get_selEnd") { _, _ ->
                val len = el.value.length
                jsGlobal.wrap(el.selectionEndPos.coerceIn(0, len).toDouble())
            }, fn("set_selEnd") { _, p ->
                val len = el.value.length
                el.selectionEndPos = (p.getOrNull(0)?.toInt() ?: 0).coerceIn(0, len)
                if (el.selectionStartPos > el.selectionEndPos) el.selectionStartPos = el.selectionEndPos
                Undefined
            })
            js.setHidden("setSelectionRange", fn("setSelectionRange") { _, p ->
                val len = el.value.length
                val start = (p.getOrNull(0)?.toInt() ?: 0).coerceIn(0, len)
                val end = (p.getOrNull(1)?.toInt() ?: start).coerceIn(start, len)
                el.selectionStartPos = start
                el.selectionEndPos = end
                Undefined
            })
        }

        if (el.lowerTagName == "option") {
            js.defineProperty("selected", fn("get_selected") { _, _ -> jsGlobal.wrap(el.checked) },
                fn("set_selected") { _, p -> el.checked = p.getOrNull(0)?.toBool() ?: false; Undefined })
            js.defineProperty("text", fn("get_text") { _, _ -> jsGlobal.wrap(el.textContent) },
                fn("set_text") { _, p -> el.textContent = p.getOrNull(0)?.toString().orEmpty(); Undefined })
        }

        // Constraint validation
        val validityProxy = object : GenericObject(jsGlobal) {
            override fun get(key: String, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? {
                val flags = el.computeValidityFlags()
                flags[key]?.let { return jsGlobal.wrap(it) }
                return super.get(key, _this, or)
            }
        }
        js.setHidden("validity", validityProxy)
        js.defineProperty("willValidate", fn("get_willValidate") { _, _ -> jsGlobal.wrap(el.willValidate()) }, jsGlobal.NOOP)
        js.defineProperty("validationMessage", fn("get_validationMessage") { _, _ -> jsGlobal.wrap(el.computeValidationMessage()) }, jsGlobal.NOOP)
        js.setHidden("setCustomValidity", fn("setCustomValidity") { _, p ->
            el.setCustomValidity(p.getOrNull(0)?.toString().orEmpty())
            Undefined
        })
        js.setHidden("checkValidity", fn("checkValidity") { _, _ ->
            val ok = !el.willValidate() || el.isValidityValid()
            if (!ok) {
                events.dispatchSimpleEvent(el, "invalid", bubbles = false, cancelable = true)
            }
            jsGlobal.wrap(ok)
        })
        js.setHidden("reportValidity", js.get("checkValidity"))
    }

    private fun attachLabelApis(el: WebDomElement, js: GenericObject) {
        js.defineProperty("htmlFor", fn("get_htmlFor") { _, _ -> jsGlobal.wrap(el.getAttribute("for").orEmpty()) },
            fn("set_htmlFor") { _, p -> el.setAttribute("for", p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("control", fn("get_control") { _, _ ->
            val forId = el.getAttribute("for")
            val ctrl = if (!forId.isNullOrEmpty()) el.ownerDocument?.getElementById(forId) else {
                fun findCtrl(n: WebDomNode): WebDomElement? {
                    for (c in n.childNodes) {
                        if (c is WebDomElement) {
                            if (c.lowerTagName in setOf("input", "select", "textarea", "button")) return c
                            findCtrl(c)?.let { return it }
                        }
                    }
                    return null
                }
                findCtrl(el)
            }
            wrapNodeOrNull(ctrl)
        }, jsGlobal.NOOP)
    }

    private fun attachTableApis(el: WebDomElement, js: GenericObject) {
        when (el.lowerTagName) {
            "table" -> {
                js.defineProperty("tHead", fn("get_tHead") { _, _ -> wrapNodeOrNull(el.children.firstOrNull { it.lowerTagName == "thead" }) }, jsGlobal.NOOP)
                js.defineProperty("tFoot", fn("get_tFoot") { _, _ -> wrapNodeOrNull(el.children.firstOrNull { it.lowerTagName == "tfoot" }) }, jsGlobal.NOOP)
                js.defineProperty("tBodies", fn("get_tBodies") { _, _ -> wrapElementList(el.children.filter { it.lowerTagName == "tbody" }) }, jsGlobal.NOOP)
                js.defineProperty("rows", fn("get_rows") { _, _ -> wrapElementList(el.tableRows()) }, jsGlobal.NOOP)
                js.setHidden("insertRow", fn("insertRow") { _, p ->
                    val idx = p.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: -1
                    val rows = el.tableRows()
                    val doc = el.ownerDocument ?: engine.document
                    val tr = doc.createElement("tr")
                    val tbody = el.children.firstOrNull { it.lowerTagName == "tbody" }
                        ?: doc.createElement("tbody").also { el.appendChild(it) }
                    if (idx < 0 || idx >= rows.size) {
                        tbody.appendChild(tr)
                    } else {
                        val ref = rows[idx]
                        ref.parentNode?.insertBefore(tr, ref)
                    }
                    wrapNode(tr)
                })
            }
            "thead", "tbody", "tfoot" -> {
                js.defineProperty("rows", fn("get_rows") { _, _ ->
                    wrapElementList(el.children.filter { it.lowerTagName == "tr" })
                }, jsGlobal.NOOP)
                js.setHidden("insertRow", fn("insertRow") { _, p ->
                    val idx = p.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: -1
                    val rows = el.children.filter { it.lowerTagName == "tr" }
                    val doc = el.ownerDocument ?: engine.document
                    val tr = doc.createElement("tr")
                    if (idx < 0 || idx >= rows.size) {
                        el.appendChild(tr)
                    } else {
                        el.insertBefore(tr, rows[idx])
                    }
                    wrapNode(tr)
                })
            }
            "tr" -> {
                js.defineProperty("cells", fn("get_cells") { _, _ ->
                    wrapElementList(el.children.filter { it.lowerTagName == "td" || it.lowerTagName == "th" })
                }, jsGlobal.NOOP)
                js.setHidden("insertCell", fn("insertCell") { _, p ->
                    val idx = p.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: -1
                    val cells = el.children.filter { it.lowerTagName == "td" || it.lowerTagName == "th" }
                    val td = (el.ownerDocument ?: engine.document).createElement("td")
                    if (idx < 0 || idx >= cells.size) el.appendChild(td) else el.insertBefore(td, cells[idx])
                    wrapNode(td)
                })
            }
            "td", "th" -> {
                js.defineProperty("colSpan", fn("get_colSpan") { _, _ -> jsGlobal.wrap((el.getAttribute("colspan")?.toIntOrNull() ?: 1).coerceAtLeast(1).toDouble()) },
                    fn("set_colSpan") { _, p -> el.setAttribute("colspan", (p.getOrNull(0)?.toInt() ?: 1).coerceAtLeast(1).toString()); Undefined })
                js.defineProperty("rowSpan", fn("get_rowSpan") { _, _ -> jsGlobal.wrap((el.getAttribute("rowspan")?.toIntOrNull() ?: 1).coerceAtLeast(1).toDouble()) },
                    fn("set_rowSpan") { _, p -> el.setAttribute("rowspan", (p.getOrNull(0)?.toInt() ?: 1).coerceAtLeast(1).toString()); Undefined })
            }
        }
    }

    private fun buildClassListObject(el: WebDomElement): GenericObject = object : GenericObject(jsGlobal) {
        init {
            setHidden("add", fn("add") { _, p -> el.addClass(*p.map { it.toString() }.toTypedArray()); Undefined })
            setHidden("remove", fn("remove") { _, p -> el.removeClass(*p.map { it.toString() }.toTypedArray()); Undefined })
            setHidden("toggle", fn("toggle") { _, p ->
                val tok = p.getOrNull(0)?.toString().orEmpty()
                val force = p.getOrNull(1)?.takeIf { it !== Undefined }?.toBool()
                jsGlobal.wrap(el.toggleClass(tok, force))
            })
            setHidden("contains", fn("contains") { _, p ->
                jsGlobal.wrap(el.classOrder.contains(p.getOrNull(0)?.toString().orEmpty()))
            })
            setHidden("replace", fn("replace") { _, p ->
                jsGlobal.wrap(el.replaceClass(p.getOrNull(0)?.toString().orEmpty(), p.getOrNull(1)?.toString().orEmpty()))
            })
            setHidden("item", fn("item") { _, p ->
                val idx = p.getOrNull(0)?.toInt() ?: -1
                if (idx in el.classOrder.indices) jsGlobal.wrap(el.classOrder[idx]) else Null
            })
            setHidden("toString", fn("toString") { _, _ -> jsGlobal.wrap(el.classOrder.joinToString(" ")) })
        }
        override fun get(key: String, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? {
            if (key == "length") return jsGlobal.wrap(el.classOrder.size.toDouble())
            if (key == "value") return jsGlobal.wrap(el.classOrder.joinToString(" "))
            val idx = key.toIntOrNull()
            if (idx != null) return if (idx in el.classOrder.indices) jsGlobal.wrap(el.classOrder[idx]) else Undefined
            return super.get(key, _this, or)
        }
        override fun get(index: Int, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? =
            if (index in el.classOrder.indices) jsGlobal.wrap(el.classOrder[index]) else Undefined
    }

    private fun buildDatasetObject(el: WebDomElement): GenericObject = object : GenericObject(jsGlobal) {
        override fun keys(): Set<String> =
            el.attributesMap.keys.filter { it.startsWith("data-") }.map { toCamelCase(it.removePrefix("data-")) }.toSet()

        override fun get(key: String, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? {
            val attrName = "data-" + toKebabCase(key)
            return el.getAttribute(attrName)?.let { jsGlobal.wrap(it) } ?: super.get(key, _this, or)
        }

        override fun set(key: String, value: BaseObject, _this: BaseObject, or: Or<Unit>) {
            el.setAttribute("data-" + toKebabCase(key), value.toString())
        }

        override fun deleteProperty(key: String): Boolean {
            el.removeAttribute("data-" + toKebabCase(key))
            return true
        }
    }

    private fun buildInlineStyleObject(el: WebDomElement): GenericObject = object : GenericObject(jsGlobal) {
        init {
            setHidden("setProperty", fn("setProperty") { _, p ->
                val prop = p.getOrNull(0)?.toString().orEmpty()
                val valStr = p.getOrNull(1)?.toString().orEmpty()
                val prio = p.getOrNull(2)?.takeIf { it !== Undefined }?.toString().orEmpty()
                el.setInlineStyleProperty(prop, valStr, prio)
                Undefined
            })
            setHidden("getPropertyValue", fn("getPropertyValue") { _, p ->
                jsGlobal.wrap(el.getInlineStyleProperty(p.getOrNull(0)?.toString().orEmpty()))
            })
            setHidden("getPropertyPriority", fn("getPropertyPriority") { _, p ->
                jsGlobal.wrap(el.getInlineStylePriority(p.getOrNull(0)?.toString().orEmpty()))
            })
            setHidden("removeProperty", fn("removeProperty") { _, p ->
                jsGlobal.wrap(el.removeInlineStyleProperty(p.getOrNull(0)?.toString().orEmpty()))
            })
        }

        override fun get(key: String, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? {
            if (key == "cssText") return jsGlobal.wrap(el.cssText())
            super.get(key, _this, Scopable.OR_NULL)?.let { return it }
            return jsGlobal.wrap(el.getInlineStyleProperty(key))
        }

        override fun set(key: String, value: BaseObject, _this: BaseObject, or: Or<Unit>) {
            if (key == "cssText") {
                el.setCssText(value.toString())
            } else {
                val v = if (value === Null || value === Undefined) "" else value.toString()
                el.setInlineStyleProperty(key, v)
            }
        }
    }

    private fun attrNodeWrapper(name: String, value: String): GenericObject = GenericObject(jsGlobal).apply {
        set("name", jsGlobal.wrap(name))
        set("value", jsGlobal.wrap(value))
    }

    private fun appendNodeOrText(parent: WebDomNode, arg: BaseObject) {
        val n = unwrapNode(arg)
        if (n != null) parent.appendChild(n)
        else parent.appendChild((parent.ownerDocument ?: engine.document).createTextNode(arg.toString()))
    }

    private fun insertNodeOrTextBefore(parent: WebDomNode, arg: BaseObject, ref: WebDomNode?) {
        val n = unwrapNode(arg)
        if (n != null) parent.insertBefore(n, ref)
        else parent.insertBefore((parent.ownerDocument ?: engine.document).createTextNode(arg.toString()), ref)
    }

    private fun requireDomNode(jsObj: BaseObject?): WebDomNode =
        unwrapNode(jsObj) ?: throw JSError.JavaException("TypeError", "Expected a DOM Node instance")

    private fun markerCtor(name: String, parent: BaseFunction? = null): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            init {
                if (parent != null) (prototype() as? GenericObject)?.setPrototypeOf(parent.prototype())
            }
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = Undefined
            override fun name(): String = name
        }

    private fun toKebabCase(camel: String): String = buildString {
        for (c in camel) {
            if (c.isUpperCase()) append('-').append(c.lowercaseChar()) else append(c)
        }
    }

    private fun toCamelCase(kebab: String): String = buildString {
        var upperNext = false
        for (c in kebab) {
            if (c == '-') upperNext = true
            else if (upperNext) { append(c.uppercaseChar()); upperNext = false }
            else append(c)
        }
    }

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
