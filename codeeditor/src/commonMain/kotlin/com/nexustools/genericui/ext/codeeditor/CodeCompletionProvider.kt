package com.nexustools.genericui.ext.codeeditor

/**
 * Strategy interface for supplying context-aware autocomplete [CompletionItem]s at a cursor position.
 */
fun interface CodeCompletionProvider {
    fun computeCompletions(
        document: CodeDocumentBuffer,
        position: CursorPosition,
        prefix: String
    ): List<CompletionItem>
}
