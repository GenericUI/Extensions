@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.dom

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.ext.svg.Svg
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.css.CssSelectorNode
import kotlin.concurrent.atomics.AtomicLong
import kotlin.math.abs

/**
 * W3C DOM Element (`nodeType = 1`) implementing [CssSelectorNode] and encapsulating HTML/SVG
 * attributes, inline CSS declarations, form constraint validation state, tables, templates,
 * Shadow DOM attachments, and live references to Alien companion components (`Svg`, `Canvas2D`,
 * `Canvas3D`, `VideoPlayer`, decoded `ImageBuffer`).
 */
class WebDomElement(
    rawTagName: String,
    val namespaceURI: String? = HTML_NAMESPACE,
    ownerDoc: WebDomDocument? = null
) : WebDomNode(
    nodeType = WebDomNodeType.ELEMENT_NODE,
    nodeName = if (namespaceURI == HTML_NAMESPACE || namespaceURI == null) rawTagName.uppercase() else rawTagName,
    ownerDocument = ownerDoc
), CssSelectorNode {

    companion object {
        const val HTML_NAMESPACE: String = "http://www.w3.org/1999/xhtml"
        const val SVG_NAMESPACE: String = "http://www.w3.org/2000/svg"
        const val MATHML_NAMESPACE: String = "http://www.w3.org/1998/Math/MathML"
        private val nextUid = AtomicLong(1L)
    }

    val elementUid: Long = nextUid.fetchAndAdd(1L)
    val localName: String = if (namespaceURI == SVG_NAMESPACE) rawTagName else rawTagName.lowercase()
    val lowerTagName: String = rawTagName.lowercase()
    override val nodeName: String = if (namespaceURI == SVG_NAMESPACE) rawTagName else rawTagName.uppercase()
    val tagName: String get() = nodeName

    internal val attributesMap: LinkedHashMap<String, String> = LinkedHashMap()
    internal val classOrder: MutableList<String> = ArrayList()
    internal val inlineStyleMap: LinkedHashMap<String, String> = LinkedHashMap()
    internal val inlineImportantProps: LinkedHashSet<String> = LinkedHashSet()

    // Interactive / CSS pseudo-state
    var isHoveredState: Boolean = false
    var isFocusedState: Boolean = false
    var isActiveState: Boolean = false
    var isIndeterminateState: Boolean = false
    var scrollLeftPx: Double = 0.0
    var scrollTopPx: Double = 0.0

    // Script execution tracking
    var scriptAlreadyStarted: Boolean = false

    // Template & Shadow DOM
    val isTemplateTag: Boolean get() = lowerTagName == "template"
    val templateContent: WebDomDocumentFragment by lazy { WebDomDocumentFragment(ownerDocument) }
    var shadowRoot: WebDomShadowRoot? = null
        internal set

    // Form control state
    private var valueState: String? = null
    private var checkedState: Boolean? = null
    private var customValidityMessage: String = ""
    var selectionStartPos: Int = 0
    var selectionEndPos: Int = 0

    // Companion extension attachments (genuine Alien backing instances)
    internal var backingCanvas2D: Canvas2D? = null
    internal var backingCanvas3D: Canvas3D? = null
    internal var backingSvg: Svg? = null
    internal var backingVideoPlayer: VideoPlayer? = null
    internal var backingImageBuffer: ImageBuffer? = null
    internal var imageLoadedComplete: Boolean = false
    internal var imageNaturalWidth: Int = 0
    internal var imageNaturalHeight: Int = 0
    internal var activeContextKind: String? = null
    internal var iframeSubWindowObj: Any? = null
    internal var iframeSubDocument: WebDomDocument? = null
    internal val computedStyleSnapshot: LinkedHashMap<String, String> = LinkedHashMap()
    internal val authorStyleSnapshot: LinkedHashMap<String, String> = LinkedHashMap()
    internal val authorImportantSnapshot: LinkedHashSet<String> = LinkedHashSet()
    internal val animatedStyleOverrides: LinkedHashMap<String, String> = LinkedHashMap()
    internal var resolvedFontSizePx: Float = 16f

    // CssSelectorNode implementation
    override val selectorTagName: String get() = lowerTagName
    override val selectorId: String? get() = attributesMap["id"]?.takeIf { it.isNotEmpty() }
    override val selectorClasses: Set<String> get() = classOrder.toSet()
    override val selectorAttributes: Map<String, String> get() = attributesMap
    override val selectorParent: CssSelectorNode? get() = parentNode as? CssSelectorNode
    override val selectorChildren: List<CssSelectorNode> get() = children
    override val selectorHasTextContent: Boolean get() = textContent.isNotBlank()
    override val selectorIsHovered: Boolean get() = isHoveredState
    override val selectorIsFocused: Boolean get() = isFocusedState
    override val selectorIsActive: Boolean get() = isActiveState
    override val selectorIsEnabled: Boolean get() = !isEffectivelyDisabled()
    override val selectorIsChecked: Boolean get() = checked
    override val selectorIsValid: Boolean get() = isValidityValid()
    override val selectorIsPlaceholderShown: Boolean
        get() = hasAttribute("placeholder") && value.isEmpty()
    override val selectorInheritedCustomProperties: Map<String, String>
        get() = collectInheritedCssVars()

    override fun getSelectorAttribute(name: String): String? {
        val lower = name.lowercase()
        return attributesMap[lower] ?: attributesMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    var id: String
        get() = getAttribute("id").orEmpty()
        set(value) {
            setAttribute("id", value)
        }

    var className: String
        get() = getAttribute("class").orEmpty()
        set(value) {
            setAttribute("class", value)
        }

    var innerHTML: String
        get() = if (isTemplateTag) templateContent.serializeInnerHtml() else serializeInnerHtml()
        set(value) {
            val doc = ownerDocument
            val targetContainer: WebDomNode = if (isTemplateTag) templateContent else this
            for (c in targetContainer.childNodes.toList()) {
                targetContainer.removeChild(c)
            }
            if (doc != null && value.isNotEmpty()) {
                val parsed = WebHtml5Parser.parseFragment(value, this, doc)
                for (n in parsed) {
                    targetContainer.appendChild(n)
                }
            } else if (value.isNotEmpty()) {
                val standaloneDoc = WebDomDocument()
                val parsed = WebHtml5Parser.parseFragment(value, this, standaloneDoc)
                for (n in parsed) {
                    n.ownerDocument = ownerDocument
                    targetContainer.appendChild(n)
                }
            }
            if (isConnected) {
                ownerDocument?.notifyDomChanged()
            }
        }

    var outerHTML: String
        get() = serializeOuterHtml()
        set(value) {
            val parent = parentNode ?: return
            val doc = ownerDocument ?: WebDomDocument()
            val fragment = WebHtml5Parser.parseFragment(value, parent as? WebDomElement, doc)
            val ref = nextSibling
            parent.removeChild(this)
            for (node in fragment) {
                parent.insertBefore(node, ref)
            }
        }

    fun getAttribute(name: String): String? {
        val key = normalizeAttributeKey(name)
        return attributesMap[key] ?: attributesMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    fun setAttribute(name: String, value: String) {
        val key = normalizeAttributeKey(name)
        val oldVal = attributesMap[key]
        attributesMap[key] = value
        when (key) {
            "class" -> syncClassesFromAttribute(value)
            "style" -> parseInlineStyleString(value)
            "value" -> if (valueState == null && (lowerTagName == "input" || lowerTagName == "textarea" || lowerTagName == "option")) {
                // Default value updated cleanly while pristine
            }
        }
        ownerDocument?.recordMutation(
            WebDomMutationRecord(
                type = "attributes",
                target = this,
                attributeName = key,
                oldValue = oldVal
            )
        )
        ownerDocument?.onAttributeChanged?.invoke(this, key, oldVal, value)
        if (isConnected) {
            ownerDocument?.notifyDomChanged()
        }
    }

    fun removeAttribute(name: String) {
        val key = normalizeAttributeKey(name)
        if (!attributesMap.containsKey(key)) return
        val oldVal = attributesMap.remove(key)
        when (key) {
            "class" -> classOrder.clear()
            "style" -> {
                inlineStyleMap.clear()
                inlineImportantProps.clear()
            }
        }
        ownerDocument?.recordMutation(
            WebDomMutationRecord(
                type = "attributes",
                target = this,
                attributeName = key,
                oldValue = oldVal
            )
        )
        ownerDocument?.onAttributeChanged?.invoke(this, key, oldVal, null)
        if (isConnected) {
            ownerDocument?.notifyDomChanged()
        }
    }

    fun hasAttribute(name: String): Boolean = getAttribute(name) != null

    fun toggleAttribute(name: String, force: Boolean? = null): Boolean {
        val exists = hasAttribute(name)
        val shouldSet = force ?: !exists
        return if (shouldSet) {
            if (!exists) setAttribute(name, "")
            true
        } else {
            if (exists) removeAttribute(name)
            false
        }
    }

    fun getAttributeNames(): List<String> = attributesMap.keys.toList()

    fun addClass(vararg tokens: String) {
        var changed = false
        for (tok in tokens) {
            val clean = tok.trim()
            if (clean.isNotEmpty() && !classOrder.contains(clean)) {
                classOrder.add(clean)
                changed = true
            }
        }
        if (changed) {
            setAttribute("class", classOrder.joinToString(" "))
        }
    }

    fun removeClass(vararg tokens: String) {
        var changed = false
        for (tok in tokens) {
            val clean = tok.trim()
            if (classOrder.remove(clean)) {
                changed = true
            }
        }
        if (changed) {
            setAttribute("class", classOrder.joinToString(" "))
        }
    }

    fun toggleClass(token: String, force: Boolean? = null): Boolean {
        val clean = token.trim()
        if (clean.isEmpty()) return false
        val has = classOrder.contains(clean)
        val target = force ?: !has
        if (target && !has) {
            classOrder.add(clean)
            setAttribute("class", classOrder.joinToString(" "))
        } else if (!target && has) {
            classOrder.remove(clean)
            setAttribute("class", classOrder.joinToString(" "))
        }
        return target
    }

    fun replaceClass(oldToken: String, newToken: String): Boolean {
        val idx = classOrder.indexOf(oldToken.trim())
        val cleanNew = newToken.trim()
        if (idx < 0 || cleanNew.isEmpty()) return false
        classOrder[idx] = cleanNew
        val dedup = LinkedHashSet(classOrder)
        classOrder.clear()
        classOrder.addAll(dedup)
        setAttribute("class", classOrder.joinToString(" "))
        return true
    }

    fun setInlineStyleProperty(property: String, value: String, priority: String = "") {
        val prop = normalizeCssPropName(property)
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            removeInlineStyleProperty(prop)
            return
        }
        val isImportant = priority.equals("important", ignoreCase = true) ||
            trimmed.endsWith("!important", ignoreCase = true)
        val cleanVal = if (trimmed.endsWith("!important", ignoreCase = true)) {
            trimmed.dropLast(10).trim()
        } else {
            trimmed
        }
        inlineStyleMap[prop] = cleanVal
        if (isImportant) inlineImportantProps.add(prop) else inlineImportantProps.remove(prop)
        serializeInlineStyleToAttribute()
    }

    fun removeInlineStyleProperty(property: String): String {
        val prop = normalizeCssPropName(property)
        val prev = inlineStyleMap.remove(prop).orEmpty()
        inlineImportantProps.remove(prop)
        serializeInlineStyleToAttribute()
        return prev
    }

    fun getInlineStyleProperty(property: String): String =
        inlineStyleMap[normalizeCssPropName(property)].orEmpty()

    fun getInlineStylePriority(property: String): String =
        if (normalizeCssPropName(property) in inlineImportantProps) "important" else ""

    fun cssText(): String = buildString {
        inlineStyleMap.entries.forEachIndexed { idx, (k, v) ->
            if (idx > 0) append("; ")
            append(k).append(": ").append(v)
            if (k in inlineImportantProps) append(" !important")
        }
    }

    fun setCssText(text: String) {
        parseInlineStyleString(text)
        attributesMap["style"] = cssText()
        if (isConnected) ownerDocument?.notifyDomChanged()
    }

    fun matches(selector: String): Boolean = WebDomSelectorMatcher.matches(this, selector)

    fun closest(selector: String): WebDomElement? {
        var cur: WebDomElement? = this
        while (cur != null) {
            if (cur.matches(selector)) return cur
            cur = cur.parentNode as? WebDomElement
        }
        return null
    }

    fun findDescendantById(targetId: String): WebDomElement? {
        if (id == targetId) return this
        for (c in childNodes) {
            if (c is WebDomElement) {
                val hit = c.findDescendantById(targetId)
                if (hit != null) return hit
            }
        }
        return null
    }

    fun collectDescendantsByTagName(tag: String, out: MutableList<WebDomElement>) {
        val matchAll = tag == "*"
        val lower = tag.lowercase()
        for (c in childNodes) {
            if (c is WebDomElement) {
                if (matchAll || c.lowerTagName == lower) {
                    out.add(c)
                }
                c.collectDescendantsByTagName(tag, out)
            }
        }
    }

    fun collectDescendantsByClassName(names: List<String>, out: MutableList<WebDomElement>) {
        if (names.isEmpty()) return
        for (c in childNodes) {
            if (c is WebDomElement) {
                if (names.all { c.classOrder.contains(it) }) {
                    out.add(c)
                }
                c.collectDescendantsByClassName(names, out)
            }
        }
    }

    fun attachShadow(mode: String, delegatesFocus: Boolean = false): WebDomShadowRoot {
        if (shadowRoot != null) {
            throw IllegalStateException("NotSupportedError: Shadow root already attached to <$lowerTagName>.")
        }
        val normalizedMode = mode.lowercase()
        if (normalizedMode != "open" && normalizedMode != "closed") {
            throw IllegalArgumentException("TypeError: Invalid shadow root mode '$mode'")
        }
        val sr = WebDomShadowRoot(this, normalizedMode, delegatesFocus, ownerDocument)
        shadowRoot = sr
        if (isConnected) ownerDocument?.notifyDomChanged()
        return sr
    }

    // -------------------------------------------------------------------------
    // Form control properties & W3C constraint validation
    // -------------------------------------------------------------------------

    var inputType: String
        get() = when (lowerTagName) {
            "button" -> (getAttribute("type")?.lowercase() ?: "submit")
            "input" -> (getAttribute("type")?.lowercase() ?: "text")
            "textarea" -> "textarea"
            "select" -> if (hasAttribute("multiple")) "select-multiple" else "select-one"
            else -> ""
        }
        set(v) {
            setAttribute("type", v)
        }

    var value: String
        get() = when (lowerTagName) {
            "textarea" -> valueState ?: textContent
            "option" -> valueState ?: getAttribute("value") ?: textContent
            "select" -> {
                val opts = collectOptions()
                val sel = opts.firstOrNull { it.checked } ?: if (!hasAttribute("multiple")) opts.firstOrNull() else null
                sel?.value.orEmpty()
            }
            "input" -> valueState ?: getAttribute("value") ?: if (inputType == "checkbox" || inputType == "radio") "on" else ""
            else -> valueState ?: getAttribute("value").orEmpty()
        }
        set(newValue) {
            when (lowerTagName) {
                "select" -> {
                    val opts = collectOptions()
                    for (opt in opts) {
                        opt.checkedState = (opt.value == newValue)
                    }
                }
                else -> {
                    valueState = newValue
                }
            }
            if (isConnected) ownerDocument?.notifyDomChanged()
        }

    var defaultValue: String
        get() = if (lowerTagName == "textarea") textContent else getAttribute("value").orEmpty()
        set(v) {
            if (lowerTagName == "textarea") {
                textContent = v
            } else {
                setAttribute("value", v)
            }
        }

    var checked: Boolean
        get() = when (lowerTagName) {
            "option" -> checkedState ?: hasAttribute("selected")
            "input" -> checkedState ?: hasAttribute("checked")
            else -> false
        }
        set(v) {
            if (lowerTagName == "option") {
                if (v) {
                    val parentSelect = findAncestorSelect()
                    if (parentSelect != null && !parentSelect.hasAttribute("multiple")) {
                        for (opt in parentSelect.collectOptions()) {
                            opt.checkedState = false
                        }
                    }
                }
                checkedState = v
            } else if (lowerTagName == "input") {
                checkedState = v
                if (v && inputType == "radio") {
                    uncheckSiblingRadios()
                }
            }
            if (isConnected) ownerDocument?.notifyDomChanged()
        }

    var defaultChecked: Boolean
        get() = hasAttribute(if (lowerTagName == "option") "selected" else "checked")
        set(v) {
            if (v) setAttribute(if (lowerTagName == "option") "selected" else "checked", "")
            else removeAttribute(if (lowerTagName == "option") "selected" else "checked")
        }

    fun resetFormControl() {
        valueState = null
        checkedState = null
        isIndeterminateState = false
        customValidityMessage = ""
        if (lowerTagName == "select") {
            val opts = collectOptions()
            for (opt in opts) {
                opt.checkedState = null
            }
        }
    }

    fun isEffectivelyDisabled(): Boolean {
        if (hasAttribute("disabled")) return true
        var cur: WebDomNode? = parentNode
        while (cur is WebDomElement) {
            if (cur.lowerTagName == "fieldset" && cur.hasAttribute("disabled")) {
                val firstLegend = cur.children.firstOrNull { it.lowerTagName == "legend" }
                if (firstLegend == null || !firstLegend.contains(this)) {
                    return true
                }
            }
            cur = cur.parentNode
        }
        return false
    }

    fun isFocusable(): Boolean {
        if (isEffectivelyDisabled() || hasAttribute("hidden")) return false
        return lowerTagName in setOf("input", "button", "select", "textarea", "a", "summary", "iframe", "video", "audio") ||
            hasAttribute("tabindex") ||
            getAttribute("contenteditable")?.equals("false", ignoreCase = true) == false
    }

    fun findOwningForm(): WebDomElement? {
        val formId = getAttribute("form")?.takeIf { it.isNotEmpty() }
        if (formId != null) {
            ownerDocument?.getElementById(formId)?.takeIf { it.lowerTagName == "form" }?.let { return it }
        }
        var cur = parentNode
        while (cur is WebDomElement) {
            if (cur.lowerTagName == "form") return cur
            cur = cur.parentNode
        }
        return null
    }

    fun collectOptions(): List<WebDomElement> {
        val out = ArrayList<WebDomElement>()
        fun walk(n: WebDomNode) {
            for (c in n.childNodes) {
                if (c is WebDomElement) {
                    if (c.lowerTagName == "option") out.add(c)
                    else walk(c)
                }
            }
        }
        walk(this)
        return out
    }

    private fun findAncestorSelect(): WebDomElement? {
        var cur = parentNode
        while (cur is WebDomElement) {
            if (cur.lowerTagName == "select") return cur
            cur = cur.parentNode
        }
        return null
    }

    private fun uncheckSiblingRadios() {
        val radioName = getAttribute("name")?.takeIf { it.isNotEmpty() } ?: return
        val scopeRoot: WebDomNode = findOwningForm() ?: getRootNode(composed = false)
        fun walk(node: WebDomNode) {
            for (c in node.childNodes) {
                if (c is WebDomElement) {
                    if (c !== this && c.lowerTagName == "input" && c.inputType == "radio" && c.getAttribute("name") == radioName) {
                        c.checkedState = false
                    }
                    walk(c)
                }
            }
        }
        walk(scopeRoot)
    }

    fun setCustomValidity(message: String) {
        customValidityMessage = message
    }

    fun computeValidityFlags(): Map<String, Boolean> {
        val customError = customValidityMessage.isNotEmpty()
        if (!willValidate()) {
            return mapOf(
                "valueMissing" to false,
                "typeMismatch" to false,
                "patternMismatch" to false,
                "tooLong" to false,
                "tooShort" to false,
                "rangeUnderflow" to false,
                "rangeOverflow" to false,
                "stepMismatch" to false,
                "badInput" to false,
                "customError" to customError,
                "valid" to !customError
            )
        }
        val v = value
        val type = inputType
        val required = hasAttribute("required")
        val valueMissing = if (!required) {
            false
        } else when {
            lowerTagName == "input" && type == "checkbox" -> !checked
            lowerTagName == "input" && type == "radio" -> {
                val radioName = getAttribute("name").orEmpty()
                if (radioName.isEmpty()) !checked else {
                    val scopeRoot = findOwningForm() ?: getRootNode(false)
                    var anyChecked = checked
                    fun checkWalk(n: WebDomNode) {
                        for (c in n.childNodes) {
                            if (c is WebDomElement) {
                                if (c.lowerTagName == "input" && c.inputType == "radio" && c.getAttribute("name") == radioName && c.checked) {
                                    anyChecked = true
                                }
                                checkWalk(c)
                            }
                        }
                    }
                    checkWalk(scopeRoot)
                    !anyChecked
                }
            }
            else -> v.isEmpty()
        }

        val typeMismatch = v.isNotEmpty() && when (type) {
            "email" -> !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(v)
            "url" -> !v.contains("://") && !v.startsWith("mailto:", ignoreCase = true)
            else -> false
        }

        val patternAttr = getAttribute("pattern")
        val patternMismatch = v.isNotEmpty() && !patternAttr.isNullOrEmpty() && run {
            try {
                !Regex("^(?:$patternAttr)$").matches(v)
            } catch (t: IllegalArgumentException) {
                // HTML5 Constraint Validation ignores malformed author regex attributes after reporting
                println("[GenericUI WebView] Ignoring invalid input pattern='$patternAttr': ${t.message}")
                false
            }
        }

        val minLen = getAttribute("minlength")?.toIntOrNull()
        val maxLen = getAttribute("maxlength")?.toIntOrNull()
        val tooShort = minLen != null && minLen > 0 && v.isNotEmpty() && v.length < minLen
        val tooLong = maxLen != null && maxLen >= 0 && v.length > maxLen

        var rangeUnderflow = false
        var rangeOverflow = false
        var stepMismatch = false
        var badInput = false
        if (type == "number" || type == "range") {
            if (v.isNotEmpty()) {
                val num = v.toDoubleOrNull()
                if (num == null || !num.isFinite()) {
                    badInput = true
                } else {
                    val minVal = getAttribute("min")?.toDoubleOrNull()
                    val maxVal = getAttribute("max")?.toDoubleOrNull()
                    if (minVal != null && num < minVal) rangeUnderflow = true
                    if (maxVal != null && num > maxVal) rangeOverflow = true
                    val stepAttr = getAttribute("step")?.trim()
                    if (stepAttr != null && !stepAttr.equals("any", ignoreCase = true)) {
                        val stepVal = stepAttr.toDoubleOrNull()
                        if (stepVal != null && stepVal > 0.0) {
                            val base = minVal ?: 0.0
                            val rem = abs((num - base) % stepVal)
                            val eps = 1e-6
                            if (rem > eps && abs(stepVal - rem) > eps) {
                                stepMismatch = true
                            }
                        }
                    }
                }
            }
        }

        val valid = !(valueMissing || typeMismatch || patternMismatch || tooLong || tooShort ||
            rangeUnderflow || rangeOverflow || stepMismatch || badInput || customError)
        return linkedMapOf(
            "valueMissing" to valueMissing,
            "typeMismatch" to typeMismatch,
            "patternMismatch" to patternMismatch,
            "tooLong" to tooLong,
            "tooShort" to tooShort,
            "rangeUnderflow" to rangeUnderflow,
            "rangeOverflow" to rangeOverflow,
            "stepMismatch" to stepMismatch,
            "badInput" to badInput,
            "customError" to customError,
            "valid" to valid
        )
    }

    fun willValidate(): Boolean {
        if (isEffectivelyDisabled() || hasAttribute("readonly")) return false
        return when (lowerTagName) {
            "input" -> inputType != "hidden" && inputType != "button" && inputType != "reset"
            "select", "textarea" -> true
            "button" -> inputType == "submit"
            else -> false
        }
    }

    fun isValidityValid(): Boolean = computeValidityFlags()["valid"] == true

    fun computeValidationMessage(): String {
        if (customValidityMessage.isNotEmpty()) return customValidityMessage
        val flags = computeValidityFlags()
        return when {
            flags["valueMissing"] == true -> "Please fill out this field."
            flags["typeMismatch"] == true -> "Please enter a valid $inputType."
            flags["patternMismatch"] == true -> "Please match the requested format."
            flags["tooShort"] == true -> "Value is shorter than minimum length."
            flags["tooLong"] == true -> "Value exceeds maximum length."
            flags["rangeUnderflow"] == true -> "Value must be greater than or equal to ${getAttribute("min")}."
            flags["rangeOverflow"] == true -> "Value must be less than or equal to ${getAttribute("max")}."
            flags["stepMismatch"] == true -> "Please enter a valid value matching step."
            flags["badInput"] == true -> "Please enter a number."
            else -> ""
        }
    }

    // -------------------------------------------------------------------------
    // Table helpers (Suite 07)
    // -------------------------------------------------------------------------

    fun tableRows(): List<WebDomElement> {
        val headRows = ArrayList<WebDomElement>()
        val bodyRows = ArrayList<WebDomElement>()
        val footRows = ArrayList<WebDomElement>()
        for (c in children) {
            when (c.lowerTagName) {
                "thead" -> headRows.addAll(c.children.filter { it.lowerTagName == "tr" })
                "tbody" -> bodyRows.addAll(c.children.filter { it.lowerTagName == "tr" })
                "tfoot" -> footRows.addAll(c.children.filter { it.lowerTagName == "tr" })
                "tr" -> bodyRows.add(c)
            }
        }
        return headRows + bodyRows + footRows
    }

    // -------------------------------------------------------------------------
    // Serialization & cloning
    // -------------------------------------------------------------------------

    override fun cloneShallow(doc: WebDomDocument?): WebDomNode {
        val copy = WebDomElement(if (namespaceURI == SVG_NAMESPACE) nodeName else lowerTagName, namespaceURI, doc)
        for ((k, v) in attributesMap) {
            copy.attributesMap[k] = v
        }
        copy.syncClassesFromAttribute(attributesMap["class"].orEmpty())
        copy.inlineStyleMap.putAll(inlineStyleMap)
        copy.inlineImportantProps.addAll(inlineImportantProps)
        copy.valueState = valueState
        copy.checkedState = checkedState
        return copy
    }

    override fun serializeOuterHtml(): String = buildString {
        val tagOut = if (namespaceURI == SVG_NAMESPACE) nodeName else lowerTagName
        append('<').append(tagOut)
        for ((k, v) in attributesMap) {
            append(' ').append(k)
            if (v.isNotEmpty() || !isBooleanHtmlAttr(k)) {
                val escaped = v.replace("&", "&amp;").replace("\"", "&quot;")
                append("=\"").append(escaped).append('"')
            } else {
                append("=\"\"")
            }
        }
        append('>')
        if (!isVoidHtmlTag(lowerTagName) || namespaceURI == SVG_NAMESPACE) {
            if (isTemplateTag) {
                append(templateContent.serializeInnerHtml())
            } else if (lowerTagName == "script" || lowerTagName == "style") {
                for (c in childNodes) {
                    append(if (c is WebDomText) c.data else c.serializeOuterHtml())
                }
            } else {
                append(serializeInnerHtml())
            }
            append("</").append(tagOut).append('>')
        }
    }

    private fun normalizeAttributeKey(raw: String): String =
        if (namespaceURI == SVG_NAMESPACE && isCaseSensitiveSvgAttr(raw)) raw else raw.lowercase()

    private fun syncClassesFromAttribute(rawClass: String) {
        classOrder.clear()
        val dedup = LinkedHashSet<String>()
        for (token in rawClass.split(Regex("\\s+"))) {
            if (token.isNotEmpty() && dedup.add(token)) {
                classOrder.add(token)
            }
        }
    }

    private fun parseInlineStyleString(styleText: String) {
        inlineStyleMap.clear()
        inlineImportantProps.clear()
        val decls = splitTopLevelDeclarations(styleText)
        for (decl in decls) {
            val colonIdx = decl.indexOf(':')
            if (colonIdx <= 0) continue
            val prop = normalizeCssPropName(decl.substring(0, colonIdx))
            if (prop.isEmpty()) continue
            val rawVal = decl.substring(colonIdx + 1).trim()
            val isImportant = rawVal.endsWith("!important", ignoreCase = true)
            val cleanVal = if (isImportant) rawVal.dropLast(10).trim() else rawVal
            if (cleanVal.isEmpty()) continue
            inlineStyleMap[prop] = cleanVal
            if (isImportant) inlineImportantProps.add(prop)
        }
    }

    private fun serializeInlineStyleToAttribute() {
        val text = cssText()
        if (text.isEmpty()) {
            attributesMap.remove("style")
        } else {
            attributesMap["style"] = text
        }
        if (isConnected) ownerDocument?.notifyDomChanged()
    }

    private fun collectInheritedCssVars(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val chain = ArrayList<WebDomElement>()
        var cur: WebDomNode? = parentNode
        while (cur is WebDomElement) {
            chain.add(cur)
            cur = cur.parentNode
        }
        for (i in chain.indices.reversed()) {
            val el = chain[i]
            for ((k, v) in el.inlineStyleMap) {
                if (k.startsWith("--")) result[k] = v
            }
            for ((k, v) in el.computedStyleSnapshot) {
                if (k.startsWith("--")) result[k] = v
            }
        }
        return result
    }

    private fun normalizeCssPropName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("--")) return trimmed
        if ('-' in trimmed) return trimmed.lowercase()
        val sb = StringBuilder(trimmed.length + 4)
        for (ch in trimmed) {
            if (ch.isUpperCase()) {
                sb.append('-').append(ch.lowercaseChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun splitTopLevelDeclarations(css: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var parenDepth = 0
        var inQuote: Char? = null
        var i = 0
        while (i < css.length) {
            val c = css[i]
            if (inQuote != null) {
                if (c == '\\' && i + 1 < css.length) {
                    i += 2
                    continue
                }
                if (c == inQuote) inQuote = null
            } else {
                when (c) {
                    '\'', '"' -> inQuote = c
                    '(' -> parenDepth++
                    ')' -> if (parenDepth > 0) parenDepth--
                    ';' -> if (parenDepth == 0) {
                        val segment = css.substring(start, i).trim()
                        if (segment.isNotEmpty()) out.add(segment)
                        start = i + 1
                    }
                }
            }
            i++
        }
        val tail = css.substring(start).trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    private fun isVoidHtmlTag(tag: String): Boolean = tag in VOID_HTML_TAGS

    private fun isBooleanHtmlAttr(attr: String): Boolean = attr in BOOLEAN_HTML_ATTRS

    private fun isCaseSensitiveSvgAttr(attr: String): Boolean =
        attr == "viewBox" || attr == "preserveAspectRatio" || attr == "gradientUnits" ||
            attr == "gradientTransform" || attr == "spreadMethod" || attr == "patternUnits" ||
            attr == "clipPathUnits" || attr == "markerWidth" || attr == "markerHeight" ||
            attr == "refX" || attr == "refY"

    private object Constants {
        val unused = 0
    }
}

private val VOID_HTML_TAGS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr"
)

private val BOOLEAN_HTML_ATTRS = setOf(
    "disabled", "checked", "selected", "multiple", "readonly", "required",
    "hidden", "autoplay", "controls", "loop", "muted", "open", "novalidate"
)
