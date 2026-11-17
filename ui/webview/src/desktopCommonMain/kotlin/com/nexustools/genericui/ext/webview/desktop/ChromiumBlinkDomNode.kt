package com.nexustools.genericui.ext.webview.desktop

/**
 * Represents a DOM node inside the Chromium Blink layout tree for Desktop WebView.
 */
class ChromiumBlinkDomNode(
    val tagName: String,
    var id: String = "",
    val attributes: MutableMap<String, String> = LinkedHashMap(),
    var textContent: String = "",
    val children: MutableList<ChromiumBlinkDomNode> = ArrayList()
) {
    var innerHTML: String = ""
    var value: String = ""
    var checked: Boolean = false
    var disabled: Boolean = false
    val style: MutableMap<String, String> = LinkedHashMap()
    val classList: MutableSet<String> = LinkedHashSet()
    var parent: ChromiumBlinkDomNode? = null
    internal var jsPeer: Any? = null

    init {
        if (id.isNotEmpty()) {
            attributes["id"] = id
        }
        val initialClass = attributes["class"]
        if (!initialClass.isNullOrBlank()) {
            classList.addAll(initialClass.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
        }
        for (child in children) {
            child.parent = this
        }
    }

    var className: String
        get() = classList.joinToString(" ")
        set(value) {
            classList.clear()
            val tokens = value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            classList.addAll(tokens)
            if (tokens.isEmpty()) {
                attributes.remove("class")
            } else {
                attributes["class"] = tokens.joinToString(" ")
            }
        }

    fun setAttribute(name: String, attrValue: String) {
        attributes[name] = attrValue
        when (name.lowercase()) {
            "id" -> id = attrValue
            "class" -> {
                classList.clear()
                classList.addAll(attrValue.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
            }
            "value" -> value = attrValue
            "disabled" -> disabled = true
        }
    }

    fun getAttribute(name: String): String? {
        when (name.lowercase()) {
            "id" -> if (id.isNotEmpty()) return id
            "class" -> if (classList.isNotEmpty()) return classList.joinToString(" ")
        }
        return attributes[name] ?: attributes.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    fun removeAttribute(name: String) {
        attributes.remove(name)
        val iterator = attributes.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.equals(name, ignoreCase = true)) {
                iterator.remove()
            }
        }
        when (name.lowercase()) {
            "id" -> id = ""
            "class" -> classList.clear()
            "disabled" -> disabled = false
        }
    }

    fun hasAttribute(name: String): Boolean =
        getAttribute(name) != null

    fun appendChild(child: ChromiumBlinkDomNode): ChromiumBlinkDomNode {
        child.parent?.children?.remove(child)
        child.parent = this
        children.add(child)
        return child
    }

    fun insertBefore(newNode: ChromiumBlinkDomNode, referenceNode: ChromiumBlinkDomNode?): ChromiumBlinkDomNode {
        newNode.parent?.children?.remove(newNode)
        newNode.parent = this
        val idx = if (referenceNode != null) children.indexOf(referenceNode) else -1
        if (idx >= 0) {
            children.add(idx, newNode)
        } else {
            children.add(newNode)
        }
        return newNode
    }

    fun removeChild(child: ChromiumBlinkDomNode): ChromiumBlinkDomNode {
        if (children.remove(child)) {
            child.parent = null
        }
        return child
    }

    fun remove() {
        parent?.removeChild(this)
    }

    fun findById(targetId: String): ChromiumBlinkDomNode? {
        if (targetId.isEmpty()) return null
        if (id == targetId || attributes["id"] == targetId) return this
        for (child in children) {
            val found = child.findById(targetId)
            if (found != null) return found
        }
        return null
    }

    fun findAllByTagName(targetTag: String): List<ChromiumBlinkDomNode> {
        val result = ArrayList<ChromiumBlinkDomNode>()
        val normalized = targetTag.trim().replace("\\:", ":").lowercase()
        collectByTagName(normalized, result, includeSelf = false)
        return result
    }

    private fun collectByTagName(normalizedTag: String, out: MutableList<ChromiumBlinkDomNode>, includeSelf: Boolean) {
        if (includeSelf && (normalizedTag == "*" || tagName.lowercase() == normalizedTag)) {
            out.add(this)
        }
        for (child in children) {
            child.collectByTagName(normalizedTag, out, includeSelf = true)
        }
    }

    fun findBySelector(selector: String): ChromiumBlinkDomNode? =
        findAllBySelector(selector).firstOrNull()

    fun findAllBySelector(selector: String): List<ChromiumBlinkDomNode> {
        val trimmed = selector.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.contains(',')) {
            val combined = LinkedHashSet<ChromiumBlinkDomNode>()
            for (part in trimmed.split(',')) {
                combined.addAll(findAllBySingleSelectorChain(part.trim()))
            }
            return combined.toList()
        }
        return findAllBySingleSelectorChain(trimmed)
    }

    private fun findAllBySingleSelectorChain(chainSelector: String): List<ChromiumBlinkDomNode> {
        if (chainSelector.isEmpty()) return emptyList()
        val tokens = splitSelectorCombinators(chainSelector)
        if (tokens.isEmpty()) return emptyList()
        val allDescendants = ArrayList<ChromiumBlinkDomNode>()
        collectAllNodes(allDescendants)
        return allDescendants.filter { matchesSelectorChain(it, tokens, tokens.lastIndex) }
    }

    private fun collectAllNodes(out: MutableList<ChromiumBlinkDomNode>) {
        out.add(this)
        for (child in children) {
            child.collectAllNodes(out)
        }
    }

    private fun splitSelectorCombinators(selector: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var bracketDepth = 0
        var parenDepth = 0
        var quoteChar = '\u0000'
        var i = 0
        while (i < selector.length) {
            val ch = selector[i]
            if (quoteChar != '\u0000') {
                current.append(ch)
                if (ch == quoteChar && (i == 0 || selector[i - 1] != '\\')) {
                    quoteChar = '\u0000'
                }
            } else {
                when (ch) {
                    '\'', '"' -> {
                        quoteChar = ch
                        current.append(ch)
                    }
                    '[' -> {
                        bracketDepth++
                        current.append(ch)
                    }
                    ']' -> {
                        bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                        current.append(ch)
                    }
                    '(' -> {
                        parenDepth++
                        current.append(ch)
                    }
                    ')' -> {
                        parenDepth = (parenDepth - 1).coerceAtLeast(0)
                        current.append(ch)
                    }
                    '>', ' ', '\t', '\n' -> {
                        if (bracketDepth == 0 && parenDepth == 0) {
                            if (current.isNotEmpty()) {
                                result.add(current.toString())
                                current.setLength(0)
                            }
                            if (ch == '>') {
                                result.add(">")
                            }
                        } else {
                            current.append(ch)
                        }
                    }
                    else -> current.append(ch)
                }
            }
            i++
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun matchesSelectorChain(node: ChromiumBlinkDomNode, tokens: List<String>, index: Int): Boolean {
        if (index < 0) return true
        val token = tokens[index]
        if (!matchesCompoundSelector(node, token)) return false
        if (index == 0) return true
        val prevToken = tokens[index - 1]
        if (prevToken == ">") {
            val parentSelectorIdx = index - 2
            if (parentSelectorIdx < 0) return false
            val p = node.parent ?: return false
            return matchesSelectorChain(p, tokens, parentSelectorIdx)
        }
        var anc = node.parent
        while (anc != null) {
            if (matchesSelectorChain(anc, tokens, index - 1)) return true
            anc = anc.parent
        }
        return false
    }

    private fun matchesCompoundSelector(node: ChromiumBlinkDomNode, compound: String): Boolean {
        var remaining = compound.trim()
        if (remaining.isEmpty()) return false
        if (remaining == "*") return true

        // Handle :not(...) pseudo-selectors
        val notRegex = Regex(":not\\(([^)]+)\\)")
        for (match in notRegex.findAll(remaining)) {
            val inner = match.groupValues[1].trim()
            if (matchesCompoundSelector(node, inner)) return false
        }
        remaining = remaining.replace(notRegex, "")

        // Handle [attr...] selectors
        val attrRegex = Regex("\\[([^\\]=~|^$*]+)(?:([=~|^$*]?=)\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\]]*)))?\\]")
        for (match in attrRegex.findAll(remaining)) {
            val attrName = match.groupValues[1].trim()
            val op = match.groupValues[2]
            val expected = match.groupValues[3].ifEmpty { match.groupValues[4].ifEmpty { match.groupValues[5].trim() } }
            val actual = node.getAttribute(attrName) ?: return false
            when (op) {
                "=" -> if (actual != expected) return false
                "^=" -> if (!actual.startsWith(expected)) return false
                "$=" -> if (!actual.endsWith(expected)) return false
                "*=" -> if (!actual.contains(expected)) return false
                else -> {}
            }
        }
        remaining = remaining.replace(attrRegex, "")

        // Unescape CSS escaped colons in custom tags (e.g. genericui\:app -> genericui:app)
        remaining = remaining.replace("\\:", ":")

        // Extract #id
        val idRegex = Regex("#([a-zA-Z0-9_-]+)")
        for (match in idRegex.findAll(remaining)) {
            val expectedId = match.groupValues[1]
            if (node.id != expectedId && node.getAttribute("id") != expectedId) return false
        }
        remaining = remaining.replace(idRegex, "")

        // Extract .class
        val classRegex = Regex("\\.([a-zA-Z0-9_-]+)")
        for (match in classRegex.findAll(remaining)) {
            val expectedClass = match.groupValues[1]
            if (expectedClass !in node.classList) return false
        }
        remaining = remaining.replace(classRegex, "").trim()

        if (remaining.isNotEmpty() && remaining != "*") {
            if (!node.tagName.equals(remaining, ignoreCase = true)) return false
        }
        return true
    }
}
