package com.nexustools.genericui.ext.codeeditor

/**
 * Contiguous text span bounded by [start] and [end] [CursorPosition]s in a [CodeDocumentBuffer].
 */
data class TextRange(
    val start: CursorPosition,
    val end: CursorPosition
) {
    val normalizedStart: CursorPosition
        get() = if (start <= end) start else end

    val normalizedEnd: CursorPosition
        get() = if (start <= end) end else start

    val isEmpty: Boolean
        get() = start == end

    fun contains(position: CursorPosition): Boolean =
        position >= normalizedStart && position <= normalizedEnd
}
