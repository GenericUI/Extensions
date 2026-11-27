package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * Full W3C Selectors Level 4 matcher and specificity calculator operating over [WebDomNode] /
 * [WebDomElement] trees, supporting all combinators (` `, `>`, `+`, `~`), attribute operators
 * (`=`, `~=`, `|=`, `^=`, `$=`, `*=`, `i`, `s`), structural pseudo-classes (`:nth-child`,
 * `:nth-last-child`, `:first-child`, `:last-child`, `:only-child`, `*-of-type`, `:empty`, `:root`),
 * functional pseudo-classes (`:is`, `:where`, `:not`, `:has`), and UI pseudo-classes.
 */
object WebDomSelectorMatcher {

    private val ATTR_OP_REGEX = Regex("^([a-zA-Z0-9_:\\-]+)\\s*([~|^$*]?=)\\s*(.+)$")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val UNESCAPE_IDENT_REGEX = Regex("\\\\(.)")

    data class Specificity(val ids: Int, val attrsAndClasses: Int, val types: Int) : Comparable<Specificity> {
        operator fun plus(other: Specificity): Specificity =
            Specificity(ids + other.ids, attrsAndClasses + other.attrsAndClasses, types + other.types)

        override fun compareTo(other: Specificity): Int {
            if (ids != other.ids) return ids.compareTo(other.ids)
            if (attrsAndClasses != other.attrsAndClasses) return attrsAndClasses.compareTo(other.attrsAndClasses)
            return types.compareTo(other.types)
        }
    }

    internal data class ComplexSelectorChain(
        val compounds: List<String>,
        val combinators: List<Char>
    )

    internal class CompiledSelector internal constructor(
        internal val chains: List<Pair<ComplexSelectorChain, Specificity>>,
        internal val idKeys: Set<String>,
        internal val classKeys: Set<String>,
        internal val tagKeys: Set<String>,
        internal val needsUniversalBucket: Boolean
    )

    private const val MAX_SPECIFICITY_DEPTH: Int = 16

    fun querySelector(root: WebDomNode, selectors: String): WebDomElement? {
        val chains = splitTopLevelList(selectors, ',').mapNotNull { parseChain(it) }
        if (chains.isEmpty()) return null
        var match: WebDomElement? = null
        fun walk(node: WebDomNode) {
            if (match != null) return
            for (c in node.childNodes) {
                if (c is WebDomElement) {
                    if (chains.any { matchesChain(c, it, root) }) {
                        match = c
                        return
                    }
                    walk(c)
                }
                if (match != null) return
            }
        }
        walk(root)
        return match
    }

    fun querySelectorAll(root: WebDomNode, selectors: String): List<WebDomElement> {
        val chains = splitTopLevelList(selectors, ',').mapNotNull { parseChain(it) }
        if (chains.isEmpty()) return emptyList()
        val out = ArrayList<WebDomElement>()
        fun walk(node: WebDomNode) {
            for (c in node.childNodes) {
                if (c is WebDomElement) {
                    if (chains.any { matchesChain(c, it, root) }) {
                        out.add(c)
                    }
                    walk(c)
                }
            }
        }
        walk(root)
        return out
    }

    fun matches(element: WebDomElement, selectors: String, scopeRoot: WebDomNode? = null): Boolean {
        val chains = splitTopLevelList(selectors, ',').mapNotNull { parseChain(it) }
        if (chains.isEmpty()) return false
        return chains.any { matchesChain(element, it, scopeRoot) }
    }

    internal fun compileSelectorList(selectorList: String): CompiledSelector {
        val idKeys = LinkedHashSet<String>()
        val classKeys = LinkedHashSet<String>()
        val tagKeys = LinkedHashSet<String>()
        var needsUniversal = false

        val parsed = splitTopLevelList(selectorList, ',').mapNotNull { part ->
            val chain = parseChain(part) ?: return@mapNotNull null
            classifyRightmostBucketKey(
                compound = chain.compounds.last(),
                idKeys = idKeys,
                classKeys = classKeys,
                tagKeys = tagKeys,
                onUniversal = { needsUniversal = true }
            )
            chain to computeChainSpecificity(chain, depth = 0)
        }
        return CompiledSelector(
            chains = parsed,
            idKeys = idKeys,
            classKeys = classKeys,
            tagKeys = tagKeys,
            needsUniversalBucket = needsUniversal
        )
    }

    private fun classifyRightmostBucketKey(
        compound: String,
        idKeys: MutableSet<String>,
        classKeys: MutableSet<String>,
        tagKeys: MutableSet<String>,
        onUniversal: () -> Unit
    ) {
        var firstId: String? = null
        var firstClass: String? = null
        var leadingTag: String? = null
        var hasHost = false
        var i = 0
        while (i < compound.length) {
            val c = compound[i]
            when {
                c == '*' -> i++
                c == '#' -> {
                    val end = skipIdentifier(compound, i + 1)
                    if (end > i + 1 && firstId == null) {
                        firstId = unescapeCssIdent(compound.substring(i + 1, end))
                    }
                    i = end.coerceAtLeast(i + 1)
                }
                c == '.' -> {
                    val end = skipIdentifier(compound, i + 1)
                    if (end > i + 1 && firstClass == null) {
                        firstClass = unescapeCssIdent(compound.substring(i + 1, end))
                    }
                    i = end.coerceAtLeast(i + 1)
                }
                c == '[' -> {
                    i = skipBalanced(compound, i, '[', ']').coerceAtLeast(i + 1)
                }
                c == ':' -> {
                    val isDouble = i + 1 < compound.length && compound[i + 1] == ':'
                    val start = if (isDouble) i + 2 else i + 1
                    val nameEnd = skipIdentifier(compound, start)
                    val pName = compound.substring(start, nameEnd).lowercase()
                    if (pName == "host") hasHost = true
                    var next = nameEnd
                    if (next < compound.length && compound[next] == '(') {
                        next = skipBalanced(compound, next, '(', ')')
                    }
                    i = next.coerceAtLeast(i + 1)
                }
                else -> {
                    val end = skipIdentifier(compound, i)
                    if (end > i) {
                        if (leadingTag == null) {
                            leadingTag = unescapeCssIdent(compound.substring(i, end)).lowercase()
                        }
                        i = end
                    } else {
                        i++
                    }
                }
            }
        }
        when {
            hasHost -> onUniversal()
            firstId != null -> idKeys.add(firstId)
            firstClass != null -> classKeys.add(firstClass)
            leadingTag != null -> tagKeys.add(leadingTag)
            else -> onUniversal()
        }
    }

    internal fun maxMatchingSpecificity(element: WebDomElement, compiled: CompiledSelector): Specificity? {
        var best: Specificity? = null
        for ((chain, spec) in compiled.chains) {
            if (matchesChain(element, chain, null)) {
                if (best == null || spec > best) {
                    best = spec
                }
            }
        }
        return best
    }

    fun maxMatchingSpecificity(element: WebDomElement, selectorList: String): Specificity? =
        maxMatchingSpecificity(element, compileSelectorList(selectorList))

    fun computeSpecificity(selector: String, depth: Int = 0): Specificity {
        if (depth >= MAX_SPECIFICITY_DEPTH) return Specificity(0, 0, 0)
        val chains = splitTopLevelList(selector, ',').mapNotNull { parseChain(it) }
        var best = Specificity(0, 0, 0)
        for (chain in chains) {
            val s = computeChainSpecificity(chain, depth)
            if (s > best) best = s
        }
        return best
    }

    private fun computeChainSpecificity(chain: ComplexSelectorChain, depth: Int = 0): Specificity {
        if (depth >= MAX_SPECIFICITY_DEPTH) return Specificity(0, 0, 0)
        var acc = Specificity(0, 0, 0)
        for (compound in chain.compounds) {
            acc += computeCompoundSpecificity(compound, depth)
        }
        return acc
    }

    private fun computeCompoundSpecificity(compound: String, depth: Int = 0): Specificity {
        if (depth >= MAX_SPECIFICITY_DEPTH) return Specificity(0, 0, 0)
        var ids = 0
        var attrs = 0
        var types = 0
        var i = 0
        while (i < compound.length) {
            val c = compound[i]
            when {
                c == '#' -> {
                    ids++
                    i = skipIdentifier(compound, i + 1).coerceAtLeast(i + 1)
                }
                c == '.' -> {
                    attrs++
                    i = skipIdentifier(compound, i + 1).coerceAtLeast(i + 1)
                }
                c == '[' -> {
                    attrs++
                    i = skipBalanced(compound, i, '[', ']').coerceAtLeast(i + 1)
                }
                c == ':' -> {
                    val isPseudoElem = i + 1 < compound.length && compound[i + 1] == ':'
                    val start = if (isPseudoElem) i + 2 else i + 1
                    val nameEnd = skipIdentifier(compound, start)
                    val name = compound.substring(start, nameEnd).lowercase()
                    var next = nameEnd
                    var arg = ""
                    if (next < compound.length && compound[next] == '(') {
                        val endParen = skipBalanced(compound, next, '(', ')')
                        val argEnd = if (endParen > next + 1 && compound[endParen - 1] == ')') endParen - 1 else endParen
                        arg = if (argEnd > next + 1) compound.substring(next + 1, argEnd) else ""
                        next = endParen
                    }
                    if (isPseudoElem) {
                        types++
                    } else when (name) {
                        "where" -> Unit
                        "is", "not", "has" -> {
                            val sub = computeSpecificity(arg, depth + 1)
                            ids += sub.ids
                            attrs += sub.attrsAndClasses
                            types += sub.types
                        }
                        "nth-child", "nth-last-child" -> {
                            attrs++
                            val ofIdx = findTopLevelKeywordOf(arg)
                            if (ofIdx >= 0) {
                                val sub = computeSpecificity(arg.substring(ofIdx + 4), depth + 1)
                                ids += sub.ids
                                attrs += sub.attrsAndClasses
                                types += sub.types
                            }
                        }
                        else -> attrs++
                    }
                    i = next.coerceAtLeast(i + 1)
                }
                c == '*' -> i++
                else -> {
                    val next = skipIdentifier(compound, i)
                    if (next > i) {
                        types++
                        i = next
                    } else {
                        i++
                    }
                }
            }
        }
        return Specificity(ids, attrs, types)
    }

    private fun parseChain(raw: String): ComplexSelectorChain? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val compounds = ArrayList<String>()
        val combinators = ArrayList<Char>()
        val current = StringBuilder()
        var i = 0
        var paren = 0
        var bracket = 0
        var quote: Char? = null

        fun flushCurrent() {
            val s = current.toString().trim()
            if (s.isNotEmpty()) {
                compounds.add(s)
            }
            current.setLength(0)
        }

        while (i < text.length) {
            val c = text[i]
            if (quote != null) {
                current.append(c)
                if (c == '\\' && i + 1 < text.length) {
                    current.append(text[++i])
                } else if (c == quote) {
                    quote = null
                }
                i++
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
                current.append(c)
                i++
                continue
            }
            when (c) {
                '(' -> { paren++; current.append(c) }
                ')' -> { if (paren > 0) paren--; current.append(c) }
                '[' -> { bracket++; current.append(c) }
                ']' -> { if (bracket > 0) bracket--; current.append(c) }
                '>', '+', '~' -> if (paren == 0 && bracket == 0) {
                    flushCurrent()
                    if (compounds.size > combinators.size) {
                        combinators.add(c)
                    } else if (combinators.isNotEmpty()) {
                        combinators[combinators.lastIndex] = c
                    }
                } else {
                    current.append(c)
                }
                else -> if (paren == 0 && bracket == 0 && c.isWhitespace()) {
                    if (current.isNotEmpty()) {
                        flushCurrent()
                        if (compounds.size > combinators.size) {
                            combinators.add(' ')
                        }
                    }
                } else {
                    current.append(c)
                }
            }
            i++
        }
        flushCurrent()
        while (combinators.size >= compounds.size && combinators.isNotEmpty()) {
            combinators.removeAt(combinators.lastIndex)
        }
        if (compounds.isEmpty() || combinators.size != compounds.size - 1) return null
        return ComplexSelectorChain(compounds, combinators)
    }

    private fun matchesChain(element: WebDomElement, chain: ComplexSelectorChain, scopeRoot: WebDomNode?): Boolean {
        return matchAt(element, chain, chain.compounds.lastIndex, scopeRoot)
    }

    private fun matchAt(
        element: WebDomElement,
        chain: ComplexSelectorChain,
        index: Int,
        scopeRoot: WebDomNode?
    ): Boolean {
        if (!matchesCompound(element, chain.compounds[index], scopeRoot)) return false
        if (index == 0) return true
        val comb = chain.combinators[index - 1]
        return when (comb) {
            '>' -> {
                val p = element.parentElement ?: return false
                matchAt(p, chain, index - 1, scopeRoot)
            }
            ' ' -> {
                var p = element.parentElement
                while (p != null) {
                    if (matchAt(p, chain, index - 1, scopeRoot)) return true
                    p = p.parentElement
                }
                false
            }
            '+' -> {
                val prev = element.previousElementSibling ?: return false
                matchAt(prev, chain, index - 1, scopeRoot)
            }
            '~' -> {
                var prev = element.previousElementSibling
                while (prev != null) {
                    if (matchAt(prev, chain, index - 1, scopeRoot)) return true
                    prev = prev.previousElementSibling
                }
                false
            }
            else -> false
        }
    }

    private fun matchesCompound(element: WebDomElement, compound: String, scopeRoot: WebDomNode?): Boolean {
        var i = 0
        while (i < compound.length) {
            val c = compound[i]
            when {
                c == '*' -> i++
                c == '#' -> {
                    val end = skipIdentifier(compound, i + 1)
                    if (end <= i + 1) return false
                    val targetId = unescapeCssIdent(compound.substring(i + 1, end))
                    if (element.id != targetId) return false
                    i = end
                }
                c == '.' -> {
                    val end = skipIdentifier(compound, i + 1)
                    if (end <= i + 1) return false
                    val cls = unescapeCssIdent(compound.substring(i + 1, end))
                    if (!element.classOrder.contains(cls)) return false
                    i = end
                }
                c == '[' -> {
                    val end = skipBalanced(compound, i, '[', ']').coerceAtLeast(i + 1)
                    val exprEnd = if (end > i + 1 && compound[end - 1] == ']') end - 1 else end
                    val expr = if (exprEnd > i + 1) compound.substring(i + 1, exprEnd) else ""
                    if (!matchesAttribute(element, expr)) return false
                    i = end
                }
                c == ':' -> {
                    val isDoubleColon = i + 1 < compound.length && compound[i + 1] == ':'
                    val start = if (isDoubleColon) i + 2 else i + 1
                    val nameEnd = skipIdentifier(compound, start)
                    if (nameEnd <= start) return false
                    val name = compound.substring(start, nameEnd).lowercase()
                    var next = nameEnd
                    var arg: String? = null
                    if (next < compound.length && compound[next] == '(') {
                        val endParen = skipBalanced(compound, next, '(', ')')
                        val argEnd = if (endParen > next + 1 && compound[endParen - 1] == ')') endParen - 1 else endParen
                        arg = if (argEnd > next + 1) compound.substring(next + 1, argEnd) else ""
                        next = endParen
                    }
                    if (!isDoubleColon && !matchesPseudoClass(element, name, arg, scopeRoot)) return false
                    i = next.coerceAtLeast(i + 1)
                }
                else -> {
                    val end = skipIdentifier(compound, i)
                    val tag = unescapeCssIdent(compound.substring(i, end))
                    if (tag.isEmpty()) return false
                    if (!element.lowerTagName.equals(tag, ignoreCase = true)) return false
                    i = end
                }
            }
        }
        return true
    }

    private fun matchesAttribute(element: WebDomElement, expr: String): Boolean {
        val raw = expr.trim()
        if (raw.isEmpty()) return false
        val opMatch = ATTR_OP_REGEX.find(raw)
        if (opMatch == null) {
            return element.hasAttribute(raw)
        }
        val attrName = opMatch.groupValues[1]
        val op = opMatch.groupValues[2]
        var rest = opMatch.groupValues[3].trim()
        var caseInsensitive = false
        if (rest.length >= 2 && (rest.endsWith(" i", ignoreCase = true) || rest.endsWith(" s", ignoreCase = true))) {
            val flag = rest.last().lowercaseChar()
            caseInsensitive = (flag == 'i')
            rest = rest.dropLast(1).trim()
        }
        val expected = stripQuotes(rest)
        val actual = element.getAttribute(attrName) ?: return false
        val cmpActual = if (caseInsensitive) actual.lowercase() else actual
        val cmpExpected = if (caseInsensitive) expected.lowercase() else expected
        return when (op) {
            "=" -> cmpActual == cmpExpected
            "~=" -> cmpExpected.isNotEmpty() && !cmpExpected.any { it.isWhitespace() } &&
                cmpActual.split(WHITESPACE_REGEX).contains(cmpExpected)
            "|=" -> cmpActual == cmpExpected || cmpActual.startsWith("$cmpExpected-")
            "^=" -> cmpExpected.isNotEmpty() && cmpActual.startsWith(cmpExpected)
            "$=" -> cmpExpected.isNotEmpty() && cmpActual.endsWith(cmpExpected)
            "*=" -> cmpExpected.isNotEmpty() && cmpActual.contains(cmpExpected)
            else -> false
        }
    }

    private fun matchesPseudoClass(
        element: WebDomElement,
        name: String,
        arg: String?,
        scopeRoot: WebDomNode?
    ): Boolean {
        return when (name) {
            "root" -> element.parentNode is WebDomDocument || element === element.ownerDocument?.documentElement
            "scope" -> if (scopeRoot != null) element === scopeRoot else element === element.ownerDocument?.documentElement
            "empty" -> element.childNodes.none { it is WebDomElement || (it is WebDomText && it.data.isNotEmpty()) }
            "hover" -> element.isHoveredState
            "focus", "focus-visible" -> element.isFocusedState || element.ownerDocument?.activeElement === element
            "focus-within" -> {
                val active = element.ownerDocument?.activeElement
                active != null && element.contains(active)
            }
            "active" -> element.isActiveState
            "checked" -> element.checked
            "indeterminate" -> element.isIndeterminateState
            "disabled" -> element.isEffectivelyDisabled()
            "enabled" -> (element.lowerTagName == "input" || element.lowerTagName == "button" ||
                element.lowerTagName == "select" || element.lowerTagName == "textarea" ||
                element.lowerTagName == "option") && !element.isEffectivelyDisabled()
            "required" -> element.hasAttribute("required")
            "optional" -> !element.hasAttribute("required")
            "valid" -> element.isValidityValid()
            "invalid" -> !element.isValidityValid()
            "link" -> (element.lowerTagName == "a" || element.lowerTagName == "area") && element.hasAttribute("href")
            "first-child" -> element.previousElementSibling == null && element.parentNode != null
            "last-child" -> element.nextElementSibling == null && element.parentNode != null
            "only-child" -> element.parentNode?.children?.size == 1
            "first-of-type" -> element.parentNode != null && element.parentNode!!.children.firstOrNull { it.lowerTagName == element.lowerTagName } === element
            "last-of-type" -> element.parentNode != null && element.parentNode!!.children.lastOrNull { it.lowerTagName == element.lowerTagName } === element
            "only-of-type" -> element.parentNode != null && element.parentNode!!.children.count { it.lowerTagName == element.lowerTagName } == 1
            "nth-child" -> matchesNth(element, arg.orEmpty(), fromEnd = false, sameType = false, scopeRoot = scopeRoot)
            "nth-last-child" -> matchesNth(element, arg.orEmpty(), fromEnd = true, sameType = false, scopeRoot = scopeRoot)
            "nth-of-type" -> matchesNth(element, arg.orEmpty(), fromEnd = false, sameType = true, scopeRoot = scopeRoot)
            "nth-last-of-type" -> matchesNth(element, arg.orEmpty(), fromEnd = true, sameType = true, scopeRoot = scopeRoot)
            "is", "where", "matches" -> !arg.isNullOrBlank() && matches(element, arg, scopeRoot)
            "not" -> arg.isNullOrBlank() || !matches(element, arg, scopeRoot)
            "has" -> !arg.isNullOrBlank() && matchesHas(element, arg)
            "host" -> {
                val isHost = element.shadowRoot != null
                if (!isHost) false else if (arg.isNullOrBlank()) true else matches(element, arg, scopeRoot)
            }
            else -> false
        }
    }

    private fun matchesHas(element: WebDomElement, relativeSelectorList: String): Boolean {
        val parts = splitTopLevelList(relativeSelectorList, ',')
        for (rawPart in parts) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            when {
                part.startsWith(">") -> {
                    val inner = part.substring(1).trim()
                    if (element.children.any { matches(it, inner, element) }) return true
                }
                part.startsWith("+") -> {
                    val inner = part.substring(1).trim()
                    val next = element.nextElementSibling
                    if (next != null && matches(next, inner, element)) return true
                }
                part.startsWith("~") -> {
                    val inner = part.substring(1).trim()
                    var sib = element.nextElementSibling
                    while (sib != null) {
                        if (matches(sib, inner, element)) return true
                        sib = sib.nextElementSibling
                    }
                }
                else -> {
                    if (querySelector(element, part) != null) return true
                }
            }
        }
        return false
    }

    private fun matchesNth(
        element: WebDomElement,
        rawArg: String,
        fromEnd: Boolean,
        sameType: Boolean,
        scopeRoot: WebDomNode?
    ): Boolean {
        val parent = element.parentNode ?: return false
        val ofIdx = findTopLevelKeywordOf(rawArg)
        val expr = if (ofIdx >= 0) rawArg.substring(0, ofIdx).trim() else rawArg.trim()
        val filterSelector = if (ofIdx >= 0) rawArg.substring(ofIdx + 4).trim() else null

        var siblings = parent.children
        if (sameType) {
            siblings = siblings.filter { it.lowerTagName == element.lowerTagName }
        } else if (!filterSelector.isNullOrEmpty()) {
            if (!matches(element, filterSelector, scopeRoot)) return false
            siblings = siblings.filter { matches(it, filterSelector, scopeRoot) }
        }
        val rawIndex = siblings.indexOf(element)
        if (rawIndex < 0) return false
        val oneBased = if (fromEnd) siblings.size - rawIndex else rawIndex + 1
        return matchesAnPlusB(oneBased, expr)
    }

    private fun matchesAnPlusB(index: Int, rawExpr: String): Boolean {
        val s = rawExpr.replace(WHITESPACE_REGEX, "").lowercase()
        if (s == "odd") return index % 2 == 1
        if (s == "even") return index % 2 == 0
        s.toIntOrNull()?.let { return index == it }
        if (!s.contains('n')) return false
        val nIdx = s.indexOf('n')
        val aPart = s.substring(0, nIdx)
        val bPart = s.substring(nIdx + 1)
        val a = when (aPart) {
            "", "+" -> 1
            "-" -> -1
            else -> aPart.toIntOrNull() ?: return false
        }
        val b = if (bPart.isEmpty()) 0 else bPart.toIntOrNull() ?: return false
        if (a == 0) return index == b
        val diff = index - b
        if (a > 0) return diff >= 0 && diff % a == 0
        return diff <= 0 && diff % (-a) == 0
    }

    private fun splitTopLevelList(input: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var paren = 0
        var bracket = 0
        var quote: Char? = null
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (quote != null) {
                if (c == '\\' && i + 1 < input.length) {
                    i += 2
                    continue
                }
                if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    '(' -> paren++
                    ')' -> if (paren > 0) paren--
                    '[' -> bracket++
                    ']' -> if (bracket > 0) bracket--
                    delimiter -> if (paren == 0 && bracket == 0) {
                        out.add(input.substring(start, i))
                        start = i + 1
                    }
                }
            }
            i++
        }
        out.add(input.substring(start))
        return out
    }

    private fun findTopLevelKeywordOf(arg: String): Int {
        var paren = 0
        var bracket = 0
        var quote: Char? = null
        var i = 0
        while (i + 3 < arg.length) {
            val c = arg[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    '(' -> paren++
                    ')' -> if (paren > 0) paren--
                    '[' -> bracket++
                    ']' -> if (bracket > 0) bracket--
                    else -> if (paren == 0 && bracket == 0 && c.isWhitespace() &&
                        arg.regionMatches(i + 1, "of", 0, 2, ignoreCase = true) &&
                        i + 3 < arg.length && arg[i + 3].isWhitespace()
                    ) {
                        return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun skipIdentifier(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                i += 2
                continue
            }
            if (c.isLetterOrDigit() || c == '-' || c == '_') {
                i++
            } else {
                break
            }
        }
        return i
    }

    private fun skipBalanced(text: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var quote: Char? = null
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (quote != null) {
                if (c == '\\' && i + 1 < text.length) {
                    i += 2
                    continue
                }
                if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return i + 1
                    }
                }
            }
            i++
        }
        return text.length
    }

    private fun stripQuotes(s: String): String =
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '\'' && s.last() == '\''))) {
            s.substring(1, s.length - 1)
        } else {
            s
        }

    private fun unescapeCssIdent(s: String): String =
        if (!s.contains('\\')) s else s.replace(UNESCAPE_IDENT_REGEX, "$1")
}
