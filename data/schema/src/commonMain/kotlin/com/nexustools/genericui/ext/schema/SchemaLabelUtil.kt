package com.nexustools.genericui.ext.schema

/**
 * Internal label formatting utilities for [SchemaForm].
 * Never generates fallback tooltip strings.
 */
internal object SchemaLabelUtil {

    fun formatKeyAsLabel(rawKey: String): String {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) return ""
        val withSpaces = buildString(cleaned.length + 8) {
            for (i in cleaned.indices) {
                val ch = cleaned[i]
                if (ch == '_' || ch == '-' || ch == '.') {
                    if (isNotEmpty() && last() != ' ') append(' ')
                    continue
                }
                val prev = if (i > 0) cleaned[i - 1] else '\u0000'
                val next = if (i + 1 < cleaned.length) cleaned[i + 1] else '\u0000'
                val isBoundary = i > 0 && ch.isUpperCase() && (
                    prev.isLowerCase() ||
                        prev.isDigit() ||
                        (prev.isUpperCase() && next.isLowerCase())
                    )
                if (isBoundary && isNotEmpty() && last() != ' ') {
                    append(' ')
                }
                append(ch)
            }
        }.trim()
        if (withSpaces.isEmpty()) return ""
        return withSpaces.split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                if (word.length > 1 && word.all { it.isUpperCase() }) {
                    word
                } else {
                    word.replaceFirstChar { it.uppercaseChar() }
                }
            }
    }

    fun resolveNodeLabel(node: SchemaNode, translator: ((String) -> String)?): String =
        resolveLabel(node.label, node.key, translator)

    fun resolveLabel(label: String, key: String, translator: ((String) -> String)?): String {
        if (label.isNotBlank()) return label
        if (translator != null) return translator(key)
        return formatKeyAsLabel(key)
    }
}
