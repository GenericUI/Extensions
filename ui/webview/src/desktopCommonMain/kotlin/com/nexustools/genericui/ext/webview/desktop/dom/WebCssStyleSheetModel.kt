package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * Runtime CSSOM model backing a document `<style>` or `<link rel="stylesheet">` sheet, supporting
 * live rule inspection (`cssRules`), `insertRule(rule, index)`, `deleteRule(index)`, and
 * recompilation into GenericUI's [com.nexustools.genericui.style.css.CssStyleSheet].
 */
class WebCssStyleSheetModel(
    val ownerNode: WebDomElement? = null,
    val href: String? = null,
    initialCss: String = ""
) {
    val rules: MutableList<String> = ArrayList()
    var disabled: Boolean = false
        set(value) {
            field = value
            onModified?.invoke()
        }
    var onModified: (() -> Unit)? = null

    init {
        parseRawCssIntoRules(initialCss)
    }

    fun replaceCss(css: String) {
        rules.clear()
        parseRawCssIntoRules(css)
        onModified?.invoke()
    }

    fun insertRule(ruleText: String, index: Int = 0): Int {
        val clean = ruleText.trim()
        if (clean.isEmpty()) throw IllegalArgumentException("SyntaxError: Empty CSS rule")
        if (index < 0 || index > rules.size) {
            throw IndexOutOfBoundsException("IndexSizeError: CSS rule index $index out of range 0..${rules.size}")
        }
        rules.add(index, clean)
        onModified?.invoke()
        return index
    }

    fun deleteRule(index: Int) {
        if (index < 0 || index >= rules.size) {
            throw IndexOutOfBoundsException("IndexSizeError: CSS rule index $index out of range 0..${rules.size - 1}")
        }
        rules.removeAt(index)
        onModified?.invoke()
    }

    fun serializeCssText(): String =
        if (disabled) "" else rules.joinToString("\n")

    private fun parseRawCssIntoRules(css: String) {
        val stripped = stripCssComments(css)
        var i = 0
        var start = 0
        var depth = 0
        var inQuote: Char? = null
        while (i < stripped.length) {
            val c = stripped[i]
            if (inQuote != null) {
                if (c == '\\' && i + 1 < stripped.length) {
                    i += 2
                    continue
                }
                if (c == inQuote) inQuote = null
            } else {
                when (c) {
                    '\'', '"' -> inQuote = c
                    '{' -> depth++
                    '}' -> {
                        if (depth > 0) depth--
                        if (depth == 0) {
                            val rule = stripped.substring(start, i + 1).trim()
                            if (rule.isNotEmpty()) rules.add(rule)
                            start = i + 1
                        }
                    }
                    ';' -> if (depth == 0) {
                        val stmt = stripped.substring(start, i + 1).trim()
                        if (stmt.isNotEmpty()) rules.add(stmt)
                        start = i + 1
                    }
                }
            }
            i++
        }
    }

    private fun stripCssComments(src: String): String {
        val source = src.removePrefix("\uFEFF")
        val sb = StringBuilder(source.length)
        var i = 0
        var inQuote: Char? = null
        while (i < source.length) {
            val c = source[i]
            if (inQuote != null) {
                sb.append(c)
                if (c == '\\' && i + 1 < source.length) {
                    sb.append(source[i + 1])
                    i += 2
                    continue
                }
                if (c == inQuote) inQuote = null
                i++
            } else if (c == '/' && i + 1 < source.length && source[i + 1] == '*') {
                val end = source.indexOf("*/", i + 2)
                i = if (end < 0) source.length else end + 2
            } else {
                if (c == '\'' || c == '"') inQuote = c
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
