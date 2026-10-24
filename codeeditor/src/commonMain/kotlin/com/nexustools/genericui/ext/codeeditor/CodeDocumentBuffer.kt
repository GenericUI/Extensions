package com.nexustools.genericui.ext.codeeditor

/**
 * Line-indexed mutable source document model with undo/redo command history, multi-cursor support,
 * auto-indentation, bracket auto-closing/matching, folding detection, and find/replace operations.
 */
class CodeDocumentBuffer(
    initialText: String = "",
    var tabSize: Int = 4,
    var autoCloseBrackets: Boolean = true
) {
    private val lines = mutableListOf<String>()
    private val undoStack = ArrayDeque<Pair<List<String>, CursorPosition>>()
    private val redoStack = ArrayDeque<Pair<List<String>, CursorPosition>>()

    var primaryCursor: CursorPosition = CursorPosition.ORIGIN
        private set

    val additionalCursors: MutableList<CursorPosition> = mutableListOf()
    var selectionRange: TextRange? = null

    init {
        setText(initialText, recordUndo = false)
    }

    val lineCount: Int
        get() = lines.size

    fun getLines(): List<String> = lines.toList()

    fun getLine(lineIndex: Int): String =
        lines.getOrElse(lineIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) { "" }

    fun getText(): String = lines.joinToString("\n")

    fun setText(newText: String, recordUndo: Boolean = true) {
        if (recordUndo && lines.isNotEmpty()) {
            pushUndoSnapshot()
        }
        lines.clear()
        val normalized = newText.replace("\r\n", "\n").replace('\r', '\n')
        lines.addAll(normalized.split('\n'))
        if (lines.isEmpty()) {
            lines.add("")
        }
        primaryCursor = clampPosition(primaryCursor)
        selectionRange = null
    }

    fun setCursorPosition(position: CursorPosition, clearSelection: Boolean = true) {
        primaryCursor = clampPosition(position)
        if (clearSelection) {
            selectionRange = null
        }
    }

    fun insertTextAtCursor(text: String) {
        pushUndoSnapshot()
        selectionRange?.let { deleteRangeInternal(it) }

        if (text.length == 1 && autoCloseBrackets) {
            val ch = text[0]
            val closing = matchingClosingBracket(ch)
            if (closing != null) {
                val line = getLine(primaryCursor.line)
                val col = primaryCursor.column.coerceIn(0, line.length)
                lines[primaryCursor.line] = line.substring(0, col) + ch + closing + line.substring(col)
                primaryCursor = CursorPosition(primaryCursor.line, col + 1)
                return
            }
        }

        if (text == "\n") {
            insertNewLineWithAutoIndent()
            return
        }

        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val parts = normalized.split('\n')
        val curLine = getLine(primaryCursor.line)
        val col = primaryCursor.column.coerceIn(0, curLine.length)
        val before = curLine.substring(0, col)
        val after = curLine.substring(col)

        if (parts.size == 1) {
            lines[primaryCursor.line] = before + parts[0] + after
            primaryCursor = CursorPosition(primaryCursor.line, col + parts[0].length)
        } else {
            lines[primaryCursor.line] = before + parts.first()
            for (idx in 1 until parts.lastIndex) {
                lines.add(primaryCursor.line + idx, parts[idx])
            }
            val lastIdx = primaryCursor.line + parts.lastIndex
            lines.add(lastIdx, parts.last() + after)
            primaryCursor = CursorPosition(lastIdx, parts.last().length)
        }
    }

    fun insertNewLineWithAutoIndent() {
        val curLine = getLine(primaryCursor.line)
        val col = primaryCursor.column.coerceIn(0, curLine.length)
        val before = curLine.substring(0, col)
        val after = curLine.substring(col)
        val leadingSpaces = before.takeWhile { it == ' ' || it == '\t' }
        val extraIndent = if (before.trimEnd().endsWith("{") || before.trimEnd().endsWith(":")) {
            " ".repeat(tabSize)
        } else {
            ""
        }
        lines[primaryCursor.line] = before
        val nextIndent = leadingSpaces + extraIndent
        lines.add(primaryCursor.line + 1, nextIndent + after)
        primaryCursor = CursorPosition(primaryCursor.line + 1, nextIndent.length)
    }

    fun deleteBackward() {
        pushUndoSnapshot()
        val sel = selectionRange
        if (sel != null && !sel.isEmpty) {
            deleteRangeInternal(sel)
            return
        }
        val row = primaryCursor.line
        val col = primaryCursor.column
        if (col > 0) {
            val line = getLine(row)
            val safeCol = col.coerceAtMost(line.length)
            lines[row] = line.removeRange(safeCol - 1, safeCol)
            primaryCursor = CursorPosition(row, safeCol - 1)
        } else if (row > 0) {
            val prevLine = getLine(row - 1)
            val curLine = getLine(row)
            lines[row - 1] = prevLine + curLine
            lines.removeAt(row)
            primaryCursor = CursorPosition(row - 1, prevLine.length)
        }
    }

    fun deleteForward() {
        pushUndoSnapshot()
        val sel = selectionRange
        if (sel != null && !sel.isEmpty) {
            deleteRangeInternal(sel)
            return
        }
        val row = primaryCursor.line
        val col = primaryCursor.column
        val line = getLine(row)
        if (col < line.length) {
            lines[row] = line.removeRange(col, col + 1)
        } else if (row + 1 < lines.size) {
            lines[row] = line + lines[row + 1]
            lines.removeAt(row + 1)
        }
    }

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(lines.toList() to primaryCursor)
        lines.clear()
        lines.addAll(prev.first)
        primaryCursor = clampPosition(prev.second)
        selectionRange = null
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(lines.toList() to primaryCursor)
        lines.clear()
        lines.addAll(next.first)
        primaryCursor = clampPosition(next.second)
        selectionRange = null
        return true
    }

    fun findAll(query: String, ignoreCase: Boolean = false): List<TextRange> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<TextRange>()
        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            var startIdx = 0
            while (startIdx <= line.length - query.length) {
                val found = line.indexOf(query, startIdx, ignoreCase)
                if (found < 0) break
                matches.add(
                    TextRange(
                        CursorPosition(lineIdx, found),
                        CursorPosition(lineIdx, found + query.length)
                    )
                )
                startIdx = found + query.length.coerceAtLeast(1)
            }
        }
        return matches
    }

    fun replaceNext(query: String, replacement: String, ignoreCase: Boolean = false): Boolean {
        val matches = findAll(query, ignoreCase)
        val target = matches.firstOrNull { it.normalizedStart >= primaryCursor } ?: matches.firstOrNull() ?: return false
        pushUndoSnapshot()
        deleteRangeInternal(target)
        insertTextAtCursor(replacement)
        return true
    }

    fun replaceAll(query: String, replacement: String, ignoreCase: Boolean = false): Int {
        if (query.isEmpty()) return 0
        val matches = findAll(query, ignoreCase)
        if (matches.isEmpty()) return 0
        pushUndoSnapshot()
        for (i in lines.indices) {
            lines[i] = lines[i].replace(query, replacement, ignoreCase = ignoreCase)
        }
        primaryCursor = clampPosition(primaryCursor)
        return matches.size
    }

    /**
     * Finds the matching opening/closing bracket position if the cursor is adjacent to a bracket.
     */
    fun findMatchingBracket(position: CursorPosition = primaryCursor): CursorPosition? {
        val line = getLine(position.line)
        if (line.isEmpty()) return null
        val col = position.column.coerceIn(0, line.length - 1)
        val ch = line[col]
        val closeTarget = matchingClosingBracket(ch)
        if (closeTarget != null) {
            var depth = 0
            for (r in position.line until lines.size) {
                val rowText = lines[r]
                val cStart = if (r == position.line) col else 0
                for (c in cStart until rowText.length) {
                    if (rowText[c] == ch) depth++
                    else if (rowText[c] == closeTarget) {
                        depth--
                        if (depth == 0) return CursorPosition(r, c)
                    }
                }
            }
        }
        val openTarget = matchingOpeningBracket(ch)
        if (openTarget != null) {
            var depth = 0
            for (r in position.line downTo 0) {
                val rowText = lines[r]
                val cStart = if (r == position.line) col else rowText.length - 1
                for (c in cStart downTo 0) {
                    if (rowText[c] == ch) depth++
                    else if (rowText[c] == openTarget) {
                        depth--
                        if (depth == 0) return CursorPosition(r, c)
                    }
                }
            }
        }
        return null
    }

    /**
     * Automatically detects foldable brace `{ ... }` blocks in the document.
     */
    fun detectFoldingRegions(): List<CodeFoldingRegion> {
        val regions = mutableListOf<CodeFoldingRegion>()
        val openStack = ArrayDeque<Int>()
        for (lineIdx in lines.indices) {
            val trimmed = lines[lineIdx].trim()
            if (trimmed.endsWith("{")) {
                openStack.addLast(lineIdx)
            }
            if (trimmed.startsWith("}") && openStack.isNotEmpty()) {
                val start = openStack.removeLast()
                if (lineIdx > start + 1) {
                    regions.add(CodeFoldingRegion(startLine = start, endLine = lineIdx))
                }
            }
        }
        return regions.sortedBy { it.startLine }
    }

    fun extractWordPrefixAt(position: CursorPosition = primaryCursor): String {
        val line = getLine(position.line)
        val col = position.column.coerceIn(0, line.length)
        var start = col
        while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')) {
            start--
        }
        return line.substring(start, col)
    }

    private fun deleteRangeInternal(range: TextRange) {
        val start = clampPosition(range.normalizedStart)
        val end = clampPosition(range.normalizedEnd)
        if (start.line == end.line) {
            val line = getLine(start.line)
            lines[start.line] = line.removeRange(
                start.column.coerceIn(0, line.length),
                end.column.coerceIn(0, line.length)
            )
        } else {
            val firstPart = getLine(start.line).substring(0, start.column.coerceIn(0, getLine(start.line).length))
            val lastLine = getLine(end.line)
            val lastPart = lastLine.substring(end.column.coerceIn(0, lastLine.length))
            lines[start.line] = firstPart + lastPart
            for (r in end.line downTo start.line + 1) {
                lines.removeAt(r)
            }
        }
        primaryCursor = start
        selectionRange = null
    }

    private fun clampPosition(pos: CursorPosition): CursorPosition {
        val row = pos.line.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val col = pos.column.coerceIn(0, lines[row].length)
        return CursorPosition(row, col)
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(lines.toList() to primaryCursor)
        if (undoStack.size > 200) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    private fun matchingClosingBracket(ch: Char): Char? = when (ch) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        '"' -> '"'
        '\'' -> '\''
        else -> null
    }

    private fun matchingOpeningBracket(ch: Char): Char? = when (ch) {
        ')' -> '('
        ']' -> '['
        '}' -> '{'
        else -> null
    }
}
