package com.nexustools.genericui.ext.codeeditor

/**
 * Zero-based line and column coordinate within a [CodeDocumentBuffer].
 */
data class CursorPosition(
    val line: Int,
    val column: Int
) : Comparable<CursorPosition> {

    override fun compareTo(other: CursorPosition): Int {
        val lineCmp = line.compareTo(other.line)
        return if (lineCmp != 0) lineCmp else column.compareTo(other.column)
    }

    companion object {
        val ORIGIN = CursorPosition(0, 0)
    }
}
