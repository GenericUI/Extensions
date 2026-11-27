package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * State-machine HTML5 tokenizer and tree builder parsing full HTML documents and fragments into
 * a standards-compliant [WebDomDocument] / [WebDomNode] tree with SVG/MathML namespaces, void
 * elements, raw-text elements (`<script>`, `<style>`, `<textarea>`, `<title>`, `<pre>`),
 * `<template>.content`, and named/numeric HTML character entity decoding.
 */
object WebHtml5Parser {

    private val VOID_ELEMENTS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    private val RAW_TEXT_ELEMENTS = setOf("script", "style", "textarea", "title", "xmp")

    private val IMPLIED_AUTO_CLOSE_PEERS = mapOf(
        "p" to setOf(
            "address", "article", "aside", "blockquote", "div", "dl", "fieldset", "figcaption",
            "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr",
            "main", "menu", "nav", "ol", "p", "pre", "section", "table", "ul"
        ),
        "li" to setOf("li"),
        "dt" to setOf("dt", "dd"),
        "dd" to setOf("dt", "dd"),
        "option" to setOf("option", "optgroup"),
        "td" to setOf("td", "th", "tr"),
        "th" to setOf("td", "th", "tr"),
        "tr" to setOf("tr", "tbody", "thead", "tfoot")
    )

    private val SVG_CASE_MAP = mapOf(
        "lineargradient" to "linearGradient",
        "radialgradient" to "radialGradient",
        "clippath" to "clipPath",
        "textpath" to "textPath",
        "foreignobject" to "foreignObject",
        "fegaussianblur" to "feGaussianBlur",
        "feoffset" to "feOffset",
        "feblend" to "feBlend",
        "fecolormatrix" to "feColorMatrix",
        "fecomposite" to "feComposite",
        "femerge" to "feMerge",
        "femergenode" to "feMergeNode",
        "feflood" to "feFlood",
        "animatetransform" to "animateTransform",
        "animatemotion" to "animateMotion"
    )

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0",
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "mdash" to "\u2014",
        "ndash" to "\u2013",
        "hellip" to "\u2026",
        "laquo" to "\u00AB",
        "raquo" to "\u00BB",
        "times" to "\u00D7",
        "divide" to "\u00F7",
        "plusmn" to "\u00B1",
        "deg" to "\u00B0",
        "check" to "\u2713",
        "cross" to "\u2717"
    )

    fun parseDocument(html: String, url: String = "about:blank", allowStorage: Boolean = true): WebDomDocument =
        parseDocument(html, WebDomDocument(url = url, allowStorage = allowStorage))

    fun parseDocument(html: String, targetDoc: WebDomDocument): WebDomDocument {
        val tokens = tokenize(html)
        var parsedDoctype: WebDomDocumentType? = null
        val rootHtml = WebDomElement("html", WebDomElement.HTML_NAMESPACE, targetDoc)
        val headEl = WebDomElement("head", WebDomElement.HTML_NAMESPACE, targetDoc)
        val bodyEl = WebDomElement("body", WebDomElement.HTML_NAMESPACE, targetDoc)

        var explicitHtmlAttrs: Map<String, String>? = null
        var explicitHeadSeen = false
        var explicitBodySeen = false
        var currentTarget: WebDomNode = bodyEl

        val stack = ArrayList<WebDomNode>()
        stack.add(rootHtml)
        stack.add(headEl)

        var inHead = true

        fun ensureHeadAndBodyAttached() {
            if (headEl.parentNode !== rootHtml) {
                rootHtml.appendChild(headEl)
            }
            if (bodyEl.parentNode !== rootHtml) {
                rootHtml.appendChild(bodyEl)
            }
        }
        ensureHeadAndBodyAttached()

        val headTagSet = setOf("title", "meta", "link", "style", "base", "script", "template")

        for (tok in tokens) {
            when (tok) {
                is HtmlToken.Doctype -> {
                    parsedDoctype = WebDomDocumentType(tok.name.ifEmpty { "html" }, ownerDoc = targetDoc)
                }
                is HtmlToken.Comment -> {
                    val parent = stack.lastOrNull() ?: bodyEl
                    val parentTarget = if (parent is WebDomElement && parent.isTemplateTag) parent.templateContent else parent
                    parentTarget.appendChild(WebDomComment(tok.data, targetDoc))
                }
                is HtmlToken.Text -> {
                    val decoded = decodeEntities(tok.data)
                    if (decoded.isEmpty()) continue
                    if (inHead && stack.lastOrNull() === headEl && decoded.isBlank()) {
                        continue
                    }
                    if (inHead && stack.lastOrNull() === headEl && decoded.isNotBlank()) {
                        stack.removeAt(stack.lastIndex)
                        stack.add(bodyEl)
                        inHead = false
                    }
                    val parent = stack.lastOrNull() ?: bodyEl
                    val parentTarget = if (parent is WebDomElement && parent.isTemplateTag) parent.templateContent else parent
                    val last = parentTarget.lastChild
                    if (last is WebDomText) {
                        last.data += decoded
                    } else {
                        parentTarget.appendChild(WebDomText(decoded, targetDoc))
                    }
                }
                is HtmlToken.RawText -> {
                    if (tok.data.isEmpty()) continue
                    val parent = stack.lastOrNull() ?: bodyEl
                    val parentTarget = if (parent is WebDomElement && parent.isTemplateTag) parent.templateContent else parent
                    val last = parentTarget.lastChild
                    if (last is WebDomText) {
                        last.data += tok.data
                    } else {
                        parentTarget.appendChild(WebDomText(tok.data, targetDoc))
                    }
                }
                is HtmlToken.StartTag -> {
                    val lower = tok.tagName.lowercase()
                    when (lower) {
                        "html" -> {
                            explicitHtmlAttrs = tok.attributes
                            for ((k, v) in tok.attributes) {
                                rootHtml.setAttribute(k, decodeEntities(v))
                            }
                            continue
                        }
                        "head" -> {
                            explicitHeadSeen = true
                            for ((k, v) in tok.attributes) {
                                headEl.setAttribute(k, decodeEntities(v))
                            }
                            inHead = true
                            if (stack.lastOrNull() !== headEl) {
                                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                                stack.add(headEl)
                            }
                            continue
                        }
                        "body" -> {
                            explicitBodySeen = true
                            for ((k, v) in tok.attributes) {
                                bodyEl.setAttribute(k, decodeEntities(v))
                            }
                            inHead = false
                            while (stack.size > 1) stack.removeAt(stack.lastIndex)
                            stack.add(bodyEl)
                            continue
                        }
                    }

                    if (inHead && stack.lastOrNull() === headEl && lower !in headTagSet) {
                        stack.removeAt(stack.lastIndex)
                        stack.add(bodyEl)
                        inHead = false
                    }

                    val topElement = stack.lastOrNull() as? WebDomElement
                    if (topElement != null) {
                        val peers = IMPLIED_AUTO_CLOSE_PEERS[topElement.lowerTagName]
                        if (peers != null && lower in peers && stack.size > 2) {
                            stack.removeAt(stack.lastIndex)
                        }
                    }

                    val activeParent = stack.lastOrNull() ?: bodyEl
                    val inSvg = (activeParent as? WebDomElement)?.let {
                        it.namespaceURI == WebDomElement.SVG_NAMESPACE && it.lowerTagName != "foreignobject"
                    } == true
                    val ns = when {
                        lower == "svg" -> WebDomElement.SVG_NAMESPACE
                        lower == "math" -> WebDomElement.MATHML_NAMESPACE
                        inSvg -> WebDomElement.SVG_NAMESPACE
                        else -> WebDomElement.HTML_NAMESPACE
                    }
                    val actualTag = if (ns == WebDomElement.SVG_NAMESPACE) {
                        SVG_CASE_MAP[lower] ?: tok.tagName
                    } else {
                        lower
                    }
                    val el = WebDomElement(actualTag, ns, targetDoc)
                    for ((k, v) in tok.attributes) {
                        el.setAttribute(k, decodeEntities(v))
                    }

                    val mountParent = if (activeParent is WebDomElement && activeParent.isTemplateTag) {
                        activeParent.templateContent
                    } else {
                        activeParent
                    }
                    mountParent.appendChild(el)

                    val isVoid = (ns == WebDomElement.HTML_NAMESPACE && lower in VOID_ELEMENTS) ||
                        (ns != WebDomElement.HTML_NAMESPACE && tok.selfClosing)
                    if (!isVoid) {
                        stack.add(el)
                    }
                }
                is HtmlToken.EndTag -> {
                    val lower = tok.tagName.lowercase()
                    if (lower == "html" || lower == "body") {
                        continue
                    }
                    if (lower == "head") {
                        if (stack.lastOrNull() === headEl) {
                            stack.removeAt(stack.lastIndex)
                            stack.add(bodyEl)
                            inHead = false
                        }
                        continue
                    }
                    var foundIdx = -1
                    for (idx in stack.lastIndex downTo 1) {
                        val node = stack[idx]
                        if (node is WebDomElement && node.lowerTagName == lower) {
                            foundIdx = idx
                            break
                        }
                    }
                    if (foundIdx >= 2 || (foundIdx == 1 && stack[1] !== bodyEl && stack[1] !== headEl)) {
                        while (stack.size > foundIdx) {
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                }
            }
            @Suppress("UNUSED_VARIABLE")
            val unusedCur = currentTarget
        }

        if (explicitHtmlAttrs != null && !explicitHeadSeen && !explicitBodySeen && headEl.childNodes.isEmpty() && bodyEl.childNodes.isEmpty()) {
            // Keep standard empty head + body structure cleanly
        }
        targetDoc.rebuildDocumentRoots(parsedDoctype, rootHtml)
        return targetDoc
    }

    fun parseFragment(
        html: String,
        contextElement: WebDomElement?,
        ownerDoc: WebDomDocument
    ): List<WebDomNode> {
        val tokens = tokenize(html)
        val container = WebDomDocumentFragment(ownerDoc)
        val stack = ArrayList<WebDomNode>()
        stack.add(container)

        val contextIsSvg = contextElement?.namespaceURI == WebDomElement.SVG_NAMESPACE &&
            contextElement.lowerTagName != "foreignobject"

        for (tok in tokens) {
            when (tok) {
                is HtmlToken.Doctype -> Unit
                is HtmlToken.Comment -> {
                    val p = stack.last()
                    val target = if (p is WebDomElement && p.isTemplateTag) p.templateContent else p
                    target.appendChild(WebDomComment(tok.data, ownerDoc))
                }
                is HtmlToken.Text -> {
                    val decoded = decodeEntities(tok.data)
                    if (decoded.isEmpty()) continue
                    val p = stack.last()
                    val target = if (p is WebDomElement && p.isTemplateTag) p.templateContent else p
                    val last = target.lastChild
                    if (last is WebDomText) {
                        last.data += decoded
                    } else {
                        target.appendChild(WebDomText(decoded, ownerDoc))
                    }
                }
                is HtmlToken.RawText -> {
                    if (tok.data.isEmpty()) continue
                    val p = stack.last()
                    val target = if (p is WebDomElement && p.isTemplateTag) p.templateContent else p
                    val last = target.lastChild
                    if (last is WebDomText) {
                        last.data += tok.data
                    } else {
                        target.appendChild(WebDomText(tok.data, ownerDoc))
                    }
                }
                is HtmlToken.StartTag -> {
                    val lower = tok.tagName.lowercase()
                    val topEl = stack.last() as? WebDomElement
                    if (topEl != null) {
                        val peers = IMPLIED_AUTO_CLOSE_PEERS[topEl.lowerTagName]
                        if (peers != null && lower in peers && stack.size > 1) {
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                    val activeParent = stack.last()
                    val inSvg = when {
                        activeParent is WebDomElement ->
                            activeParent.namespaceURI == WebDomElement.SVG_NAMESPACE &&
                                activeParent.lowerTagName != "foreignobject"
                        else -> contextIsSvg
                    }
                    val ns = when {
                        lower == "svg" -> WebDomElement.SVG_NAMESPACE
                        lower == "math" -> WebDomElement.MATHML_NAMESPACE
                        inSvg -> WebDomElement.SVG_NAMESPACE
                        else -> WebDomElement.HTML_NAMESPACE
                    }
                    val actualTag = if (ns == WebDomElement.SVG_NAMESPACE) {
                        SVG_CASE_MAP[lower] ?: tok.tagName
                    } else {
                        lower
                    }
                    val el = WebDomElement(actualTag, ns, ownerDoc)
                    for ((k, v) in tok.attributes) {
                        el.setAttribute(k, decodeEntities(v))
                    }
                    val mountParent = if (activeParent is WebDomElement && activeParent.isTemplateTag) {
                        activeParent.templateContent
                    } else {
                        activeParent
                    }
                    mountParent.appendChild(el)
                    val isVoid = (ns == WebDomElement.HTML_NAMESPACE && lower in VOID_ELEMENTS) ||
                        (ns != WebDomElement.HTML_NAMESPACE && tok.selfClosing)
                    if (!isVoid) {
                        stack.add(el)
                    }
                }
                is HtmlToken.EndTag -> {
                    val lower = tok.tagName.lowercase()
                    var foundIdx = -1
                    for (idx in stack.lastIndex downTo 1) {
                        val node = stack[idx]
                        if (node is WebDomElement && node.lowerTagName == lower) {
                            foundIdx = idx
                            break
                        }
                    }
                    if (foundIdx >= 1) {
                        while (stack.size > foundIdx) {
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                }
            }
        }
        val out = container.childNodes.toList()
        for (c in out) {
            c.parentNode = null
        }
        container.childNodes.clear()
        return out
    }

    fun decodeEntities(input: String): String {
        if ('&' !in input) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '&') {
                val semi = input.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 12)) {
                    val body = input.substring(i + 1, semi)
                    val resolved = resolveEntity(body)
                    if (resolved != null) {
                        sb.append(resolved)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun resolveEntity(body: String): String? {
        if (body.startsWith("#")) {
            val code = if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
                body.substring(2).toIntOrNull(16)
            } else {
                body.substring(1).toIntOrNull(10)
            } ?: return null
            if (code !in 1..0x10FFFF) return null
            return if (code <= 0xFFFF) {
                code.toChar().toString()
            } else {
                val u = code - 0x10000
                charArrayOf(((u shr 10) + 0xD800).toChar(), ((u and 0x3FF) + 0xDC00).toChar()).concatToString()
            }
        }
        return NAMED_ENTITIES[body] ?: NAMED_ENTITIES[body.lowercase()]
    }

    private sealed class HtmlToken {
        data class Doctype(val name: String) : HtmlToken()
        data class Comment(val data: String) : HtmlToken()
        data class Text(val data: String) : HtmlToken()
        data class RawText(val data: String) : HtmlToken()
        data class StartTag(
            val tagName: String,
            val attributes: Map<String, String>,
            val selfClosing: Boolean
        ) : HtmlToken()
        data class EndTag(val tagName: String) : HtmlToken()
    }

    private fun tokenize(html: String): List<HtmlToken> {
        val tokens = ArrayList<HtmlToken>()
        val len = html.length
        var i = 0

        while (i < len) {
            val lt = html.indexOf('<', i)
            if (lt < 0) {
                if (i < len) tokens.add(HtmlToken.Text(html.substring(i)))
                break
            }
            if (lt > i) {
                tokens.add(HtmlToken.Text(html.substring(i, lt)))
            }

            if (lt + 1 >= len) {
                tokens.add(HtmlToken.Text("<"))
                break
            }

            // Comment <!-- ... -->
            if (html.startsWith("<!--", lt)) {
                val endComment = html.indexOf("-->", lt + 4)
                if (endComment < 0) {
                    tokens.add(HtmlToken.Comment(html.substring(lt + 4)))
                    break
                } else {
                    tokens.add(HtmlToken.Comment(html.substring(lt + 4, endComment)))
                    i = endComment + 3
                    continue
                }
            }

            // DOCTYPE or bogus declaration
            if (html[lt + 1] == '!') {
                val closeGt = html.indexOf('>', lt + 2)
                if (closeGt < 0) break
                val decl = html.substring(lt + 2, closeGt).trim()
                if (decl.startsWith("DOCTYPE", ignoreCase = true)) {
                    val docName = decl.substring(7).trim().substringBefore(' ')
                    tokens.add(HtmlToken.Doctype(docName))
                }
                i = closeGt + 1
                continue
            }

            // End tag </tagName>
            if (html[lt + 1] == '/') {
                val closeGt = html.indexOf('>', lt + 2)
                if (closeGt < 0) {
                    tokens.add(HtmlToken.Text(html.substring(lt)))
                    break
                }
                val rawName = html.substring(lt + 2, closeGt).trim().takeWhile { !it.isWhitespace() }
                if (rawName.isNotEmpty()) {
                    tokens.add(HtmlToken.EndTag(rawName))
                }
                i = closeGt + 1
                continue
            }

            // Start tag <tagName ...>
            val nameStart = lt + 1
            if (!html[nameStart].isLetter()) {
                tokens.add(HtmlToken.Text("<"))
                i = lt + 1
                continue
            }
            var pos = nameStart
            while (pos < len && !html[pos].isWhitespace() && html[pos] != '>' && html[pos] != '/') {
                pos++
            }
            val tagName = html.substring(nameStart, pos)
            val attrs = LinkedHashMap<String, String>()
            var selfClosing = false

            while (pos < len) {
                while (pos < len && html[pos].isWhitespace()) pos++
                if (pos >= len) break
                val c = html[pos]
                if (c == '>') {
                    pos++
                    break
                }
                if (c == '/' && pos + 1 < len && html[pos + 1] == '>') {
                    selfClosing = true
                    pos += 2
                    break
                }
                if (c == '/') {
                    pos++
                    continue
                }
                val attrStart = pos
                while (pos < len && !html[pos].isWhitespace() && html[pos] != '=' && html[pos] != '>' && !(html[pos] == '/' && pos + 1 < len && html[pos + 1] == '>')) {
                    pos++
                }
                val attrName = html.substring(attrStart, pos)
                while (pos < len && html[pos].isWhitespace()) pos++
                var attrVal = ""
                if (pos < len && html[pos] == '=') {
                    pos++
                    while (pos < len && html[pos].isWhitespace()) pos++
                    if (pos < len) {
                        val q = html[pos]
                        if (q == '"' || q == '\'') {
                            pos++
                            val vStart = pos
                            val endQ = html.indexOf(q, vStart)
                            if (endQ < 0) {
                                attrVal = html.substring(vStart)
                                pos = len
                            } else {
                                attrVal = html.substring(vStart, endQ)
                                pos = endQ + 1
                            }
                        } else {
                            val vStart = pos
                            while (pos < len && !html[pos].isWhitespace() && html[pos] != '>') {
                                pos++
                            }
                            attrVal = html.substring(vStart, pos)
                        }
                    }
                }
                if (attrName.isNotEmpty() && !attrs.containsKey(attrName)) {
                    attrs[attrName] = attrVal
                }
            }

            tokens.add(HtmlToken.StartTag(tagName, attrs, selfClosing))
            i = pos

            val lowerTag = tagName.lowercase()
            if (!selfClosing && lowerTag in RAW_TEXT_ELEMENTS) {
                val closeTagIdx = findRawTextEndTag(html, i, lowerTag)
                val emitRaw = (lowerTag == "script" || lowerTag == "style")
                if (closeTagIdx < 0) {
                    if (i < len) {
                        val content = html.substring(i)
                        tokens.add(if (emitRaw) HtmlToken.RawText(content) else HtmlToken.Text(content))
                    }
                    i = len
                } else {
                    if (closeTagIdx > i) {
                        val content = html.substring(i, closeTagIdx)
                        tokens.add(if (emitRaw) HtmlToken.RawText(content) else HtmlToken.Text(content))
                    }
                    val endTagClose = html.indexOf('>', closeTagIdx + 2 + lowerTag.length)
                    tokens.add(HtmlToken.EndTag(lowerTag))
                    i = if (endTagClose >= 0) endTagClose + 1 else len
                }
            }
        }

        return tokens
    }

    private fun findRawTextEndTag(html: String, start: Int, lowerTag: String): Int {
        val needle = "</$lowerTag"
        var pos = start
        val len = html.length
        while (pos < len) {
            val found = html.indexOf(needle, pos, ignoreCase = true)
            if (found < 0) return -1
            val after = found + needle.length
            if (after < len) {
                val ch = html[after]
                if (ch == '>' || ch == '/' || ch.isWhitespace()) {
                    if (lowerTag == "script") {
                        if (!isInsideJsStringOrComment(html, start, found)) {
                            return found
                        }
                    } else {
                        return found
                    }
                }
            }
            pos = after
        }
        return -1
    }

    private fun isInsideJsStringOrComment(src: String, start: Int, target: Int): Boolean {
        var inSingle = false
        var inDouble = false
        var inTemplate = false
        var inLineComment = false
        var inBlockComment = false
        var i = start
        while (i < target) {
            val c = src[i]
            val next = if (i + 1 < target) src[i + 1] else '\u0000'
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && next == '/') {
                    inBlockComment = false
                    i++
                }
                inSingle -> {
                    if (c == '\\') i++
                    else if (c == '\'') inSingle = false
                }
                inDouble -> {
                    if (c == '\\') i++
                    else if (c == '"') inDouble = false
                }
                inTemplate -> {
                    if (c == '\\') i++
                    else if (c == '`') inTemplate = false
                }
                else -> when {
                    c == '/' && next == '/' -> {
                        inLineComment = true
                        i++
                    }
                    c == '/' && next == '*' -> {
                        inBlockComment = true
                        i++
                    }
                    c == '\'' -> inSingle = true
                    c == '"' -> inDouble = true
                    c == '`' -> inTemplate = true
                }
            }
            i++
        }
        return inSingle || inDouble || inTemplate || inLineComment || inBlockComment
    }
}
