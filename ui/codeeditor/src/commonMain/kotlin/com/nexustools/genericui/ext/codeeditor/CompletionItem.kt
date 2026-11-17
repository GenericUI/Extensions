package com.nexustools.genericui.ext.codeeditor

/**
 * Single autocomplete / IntelliSense suggestion item displayed by [IdeCodeEditor].
 */
data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind = CompletionItemKind.KEYWORD,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String = label
)
