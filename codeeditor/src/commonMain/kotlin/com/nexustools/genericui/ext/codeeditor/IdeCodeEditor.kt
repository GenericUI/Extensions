package com.nexustools.genericui.ext.codeeditor

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Full IDE-style Code Editor extension widget built atop GenericUI's [Canvas2D] and layout containers.
 *
 * Features:
 * - Multi-language lexical syntax highlighting ([SyntaxTokenizer] + [CodeEditorTheme]).
 * - Line-number, breakpoint, and code-folding (`{ ... }`) gutter.
 * - Multi-cursor editing, bracket matching, auto-indentation, and undo/redo ([CodeDocumentBuffer]).
 * - Inline compiler/linter diagnostic squiggle underlines ([CodeDiagnostic]) and hover diagnostic inspection.
 * - Context-aware IntelliSense autocomplete popup ([CodeCompletionProvider] & [CompletionItem]).
 * - Find & Replace search highlights and synchronized [CodeMinimap] overview.
 */
class IdeCodeEditor(
    initialText: String = "",
    language: CodeLanguage = CodeLanguage.KOTLIN,
    theme: CodeEditorTheme = CodeEditorTheme.DARK_IDE,
    width: Int = 960,
    height: Int = 640
) : Container("ide-code-editor") {

    val document: CodeDocumentBuffer = CodeDocumentBuffer(initialText)
    val tokenizer: SyntaxTokenizer = SyntaxTokenizer(language)

    var theme: CodeEditorTheme = theme
        set(value) {
            field = value
            minimap.theme = value
            editorCanvas.requestRender()
            minimap.requestRender()
        }

    var language: CodeLanguage
        get() = tokenizer.language
        set(value) {
            tokenizer.language = value
            rebuildSyntaxAndFolding()
        }

    var readOnly: Boolean = false
    var fontFamily: String = "JetBrains Mono"
    var fontSizePx: Float = 14f
    var lineHeightPx: Float = 20f
    var charWidthPx: Float = 8.4f
    var gutterWidthPx: Float = 56f

    var firstVisibleLine: Int = 0
        private set
    var horizontalScrollPx: Float = 0f
        private set

    val breakpoints: MutableSet<Int> = linkedSetOf()
    private val foldingRegions = mutableListOf<CodeFoldingRegion>()
    private var cachedTokens: List<List<SyntaxToken>> = emptyList()
    private var diagnosticsList: List<CodeDiagnostic> = emptyList()
    private var searchHighlights: List<TextRange> = emptyList()

    var completionProvider: CodeCompletionProvider? = null
    private var activeCompletions: List<CompletionItem> = emptyList()
    private var selectedCompletionIndex: Int = 0
    private var hoveredDiagnostic: CodeDiagnostic? = null

    private var onTextChangedListener: ((String) -> Unit)? = null
    private var onCursorChangedListener: ((CursorPosition) -> Unit)? = null

    val editorCanvas: Canvas2DExtSurface = Canvas2DExtSurface(width = (width - 96).coerceAtLeast(320), height = height)
    val minimap: CodeMinimap = CodeMinimap(
        document = document,
        tokenizer = tokenizer,
        theme = theme,
        width = 96,
        height = height
    )

    private val viewportRow = HStack()

    init {
        setPreferredSize(Size(width, height))
        rebuildSyntaxAndFolding()

        minimap.onScrollToLineRequested = { targetLine ->
            scrollToLine(targetLine)
        }

        editorCanvas.registerVBlankRender { ctx, _, _, _ ->
            renderEditorSurface(ctx)
        }

        editorCanvas.setOnPointerDown { event ->
            if (event.x <= gutterWidthPx) {
                val clickedLine = yToDocumentLine(event.y)
                toggleBreakpointOrFold(clickedLine, event.x)
            } else {
                val pos = pointerToCursorPosition(event.x, event.y)
                document.setCursorPosition(pos, clearSelection = !event.shiftKey)
                activeCompletions = emptyList()
                onCursorChangedListener?.invoke(document.primaryCursor)
            }
            editorCanvas.requestRender()
        }

        editorCanvas.setOnPointerMove { event ->
            val hoverPos = pointerToCursorPosition(event.x, event.y)
            hoveredDiagnostic = diagnosticsList.firstOrNull { it.range.contains(hoverPos) }
            if (event.buttonsMask != 0 && event.x > gutterWidthPx) {
                val dragPos = pointerToCursorPosition(event.x, event.y)
                document.selectionRange = TextRange(document.primaryCursor, dragPos)
            }
            editorCanvas.requestRender()
        }

        editorCanvas.setOnWheel { wheel ->
            val deltaLines = if (wheel.deltaY > 0f) 3 else if (wheel.deltaY < 0f) -3 else 0
            scrollToLine((firstVisibleLine + deltaLines).coerceIn(0, (document.lineCount - 1).coerceAtLeast(0)))
        }

        setOnKeyDown { keyEvent ->
            handleKeyDown(keyEvent)
        }

        viewportRow.addChild(editorCanvas)
        viewportRow.addChild(minimap)
        addChild(viewportRow)
    }

    fun getText(): String = document.getText()

    fun setText(text: String) {
        document.setText(text)
        rebuildSyntaxAndFolding()
        onTextChangedListener?.invoke(text)
    }

    fun setDiagnostics(diagnostics: List<CodeDiagnostic>) {
        diagnosticsList = diagnostics
        minimap.diagnostics = diagnostics
        editorCanvas.requestRender()
        minimap.requestRender()
    }

    fun getDiagnostics(): List<CodeDiagnostic> = diagnosticsList

    fun setOnTextChanged(listener: ((String) -> Unit)?) {
        onTextChangedListener = listener
    }

    fun setOnCursorChanged(listener: ((CursorPosition) -> Unit)?) {
        onCursorChangedListener = listener
    }

    fun scrollToLine(lineIndex: Int) {
        firstVisibleLine = lineIndex.coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
        minimap.firstVisibleLine = firstVisibleLine
        editorCanvas.requestRender()
        minimap.requestRender()
    }

    fun find(query: String, ignoreCase: Boolean = false): List<TextRange> {
        searchHighlights = document.findAll(query, ignoreCase)
        searchHighlights.firstOrNull()?.let { firstMatch ->
            document.setCursorPosition(firstMatch.normalizedStart)
            scrollToLine((firstMatch.normalizedStart.line - 3).coerceAtLeast(0))
        }
        editorCanvas.requestRender()
        return searchHighlights
    }

    fun replaceNext(query: String, replacement: String, ignoreCase: Boolean = false): Boolean {
        if (readOnly) return false
        val replaced = document.replaceNext(query, replacement, ignoreCase)
        if (replaced) {
            rebuildSyntaxAndFolding()
            searchHighlights = document.findAll(query, ignoreCase)
            onTextChangedListener?.invoke(document.getText())
        }
        return replaced
    }

    fun replaceAll(query: String, replacement: String, ignoreCase: Boolean = false): Int {
        if (readOnly) return 0
        val count = document.replaceAll(query, replacement, ignoreCase)
        if (count > 0) {
            rebuildSyntaxAndFolding()
            searchHighlights = emptyList()
            onTextChangedListener?.invoke(document.getText())
        }
        return count
    }

    fun triggerAutocomplete() {
        val provider = completionProvider ?: defaultCompletionProvider()
        val prefix = document.extractWordPrefixAt(document.primaryCursor)
        activeCompletions = provider.computeCompletions(document, document.primaryCursor, prefix)
        selectedCompletionIndex = 0
        editorCanvas.requestRender()
    }

    fun acceptSelectedCompletion(): Boolean {
        val item = activeCompletions.getOrNull(selectedCompletionIndex) ?: return false
        val prefix = document.extractWordPrefixAt(document.primaryCursor)
        repeat(prefix.length) {
            document.deleteBackward()
        }
        document.insertTextAtCursor(item.insertText)
        activeCompletions = emptyList()
        rebuildSyntaxAndFolding()
        onTextChangedListener?.invoke(document.getText())
        return true
    }

    private fun handleKeyDown(event: KeyEvent) {
        if (activeCompletions.isNotEmpty()) {
            when (event.key) {
                "ArrowDown" -> {
                    selectedCompletionIndex = (selectedCompletionIndex + 1) % activeCompletions.size
                    editorCanvas.requestRender()
                    return
                }
                "ArrowUp" -> {
                    selectedCompletionIndex = (selectedCompletionIndex - 1 + activeCompletions.size) % activeCompletions.size
                    editorCanvas.requestRender()
                    return
                }
                "Enter", "Tab" -> {
                    if (acceptSelectedCompletion()) return
                }
                "Escape" -> {
                    activeCompletions = emptyList()
                    editorCanvas.requestRender()
                    return
                }
            }
        }

        if (event.isCommandOrCtrl && event.key.equals("z", ignoreCase = true)) {
            if (!readOnly) {
                if (event.shiftKey) document.redo() else document.undo()
                rebuildSyntaxAndFolding()
                onTextChangedListener?.invoke(document.getText())
            }
            return
        }

        if (event.isCommandOrCtrl && event.key == " ") {
            triggerAutocomplete()
            return
        }

        val cur = document.primaryCursor
        when (event.key) {
            "ArrowLeft" -> document.setCursorPosition(CursorPosition(cur.line, (cur.column - 1).coerceAtLeast(0)))
            "ArrowRight" -> document.setCursorPosition(CursorPosition(cur.line, cur.column + 1))
            "ArrowUp" -> document.setCursorPosition(CursorPosition((cur.line - 1).coerceAtLeast(0), cur.column))
            "ArrowDown" -> document.setCursorPosition(CursorPosition(cur.line + 1, cur.column))
            "Backspace" -> if (!readOnly) {
                document.deleteBackward()
                rebuildSyntaxAndFolding()
                onTextChangedListener?.invoke(document.getText())
            }
            "Delete" -> if (!readOnly) {
                document.deleteForward()
                rebuildSyntaxAndFolding()
                onTextChangedListener?.invoke(document.getText())
            }
            "Enter" -> if (!readOnly) {
                document.insertNewLineWithAutoIndent()
                rebuildSyntaxAndFolding()
                onTextChangedListener?.invoke(document.getText())
            }
            "Tab" -> if (!readOnly) {
                document.insertTextAtCursor(" ".repeat(document.tabSize))
                rebuildSyntaxAndFolding()
                onTextChangedListener?.invoke(document.getText())
            }
            else -> if (!readOnly && event.key.length == 1 && !event.isCommandOrCtrl) {
                document.insertTextAtCursor(event.key)
                rebuildSyntaxAndFolding()
                onTextChangedListener?.invoke(document.getText())
            }
        }
        onCursorChangedListener?.invoke(document.primaryCursor)
        editorCanvas.requestRender()
    }

    private fun toggleBreakpointOrFold(lineIndex: Int, clickX: Float) {
        val fold = foldingRegions.firstOrNull { it.startLine == lineIndex }
        if (fold != null && clickX >= gutterWidthPx - 18f) {
            fold.isCollapsed = !fold.isCollapsed
        } else {
            if (!breakpoints.add(lineIndex)) {
                breakpoints.remove(lineIndex)
            }
        }
    }

    private fun rebuildSyntaxAndFolding() {
        val lines = document.getLines()
        cachedTokens = tokenizer.tokenizeDocument(lines)
        val detected = document.detectFoldingRegions()
        val previouslyCollapsed = foldingRegions.filter { it.isCollapsed }.map { it.startLine }.toSet()
        foldingRegions.clear()
        for (region in detected) {
            region.isCollapsed = region.startLine in previouslyCollapsed
            foldingRegions.add(region)
        }
        editorCanvas.requestRender()
        minimap.requestRender()
    }

    fun renderEditorSurface(ctx: ImageBuffer2DContext) {
        val w = ctx.width.toFloat().coerceAtLeast(320f)
        val h = ctx.height.toFloat().coerceAtLeast(200f)

        // 1. Editor & Gutter backgrounds
        ctx.setFillColor(theme.background)
        ctx.fillRect(0f, 0f, w, h)
        ctx.setFillColor(theme.gutterBackground)
        ctx.fillRect(0f, 0f, gutterWidthPx, h)

        ctx.setFont(fontFamily, fontSizePx)

        val visibleRows = (h / lineHeightPx).toInt().coerceAtLeast(1)
        minimap.visibleLineCount = visibleRows

        val matchedBracket = document.findMatchingBracket(document.primaryCursor)

        var visualRow = 0
        var docLine = firstVisibleLine
        while (docLine < document.lineCount && visualRow < visibleRows) {
            if (foldingRegions.any { it.containsHiddenLine(docLine) }) {
                docLine++
                continue
            }

            val rowTop = visualRow * lineHeightPx
            val baselineY = rowTop + lineHeightPx * 0.76f

            // Active line highlight
            if (docLine == document.primaryCursor.line) {
                ctx.setFillColor(theme.activeLineBackground)
                ctx.fillRect(gutterWidthPx, rowTop, w - gutterWidthPx, lineHeightPx)
            }

            // Selection highlight on this line
            document.selectionRange?.let { sel ->
                val s = sel.normalizedStart
                val e = sel.normalizedEnd
                if (docLine in s.line..e.line && !sel.isEmpty) {
                    val startCol = if (docLine == s.line) s.column else 0
                    val endCol = if (docLine == e.line) e.column else document.getLine(docLine).length
                    val selX = gutterWidthPx + 8f + startCol * charWidthPx - horizontalScrollPx
                    val selW = ((endCol - startCol).coerceAtLeast(1)) * charWidthPx
                    ctx.setFillColor(theme.selectionBackground)
                    ctx.fillRect(selX, rowTop, selW, lineHeightPx)
                }
            }

            // Breakpoint indicator
            if (docLine in breakpoints) {
                ctx.setFillColor(theme.errorSquiggle)
                ctx.beginPath()
                ctx.arc(10f, rowTop + lineHeightPx * 0.5f, 4.5f, 0f, 6.283185f)
                ctx.fill()
            }

            // Line number & fold indicator
            ctx.setFillColor(theme.gutterText)
            ctx.fillText((docLine + 1).toString(), 20f, baselineY)
            val foldRegion = foldingRegions.firstOrNull { it.startLine == docLine }
            if (foldRegion != null) {
                ctx.fillText(if (foldRegion.isCollapsed) "▸" else "▾", gutterWidthPx - 14f, baselineY)
            }

            // Syntax-highlighted tokens
            val lineTokens = cachedTokens.getOrElse(docLine) { emptyList() }
            for (token in lineTokens) {
                if (token.type == SyntaxTokenType.WHITESPACE) continue
                ctx.setFillColor(theme.colorForToken(token.type))
                val x = gutterWidthPx + 8f + token.startColumn * charWidthPx - horizontalScrollPx
                ctx.fillText(token.text, x, baselineY)
            }

            // Folded placeholder pill
            if (foldRegion != null && foldRegion.isCollapsed) {
                val endX = gutterWidthPx + 12f + document.getLine(docLine).length * charWidthPx
                ctx.setStrokeColor(theme.gutterText)
                ctx.strokeRect(endX, rowTop + 2f, 28f, lineHeightPx - 4f)
                ctx.setFillColor(theme.gutterText)
                ctx.fillText(foldRegion.placeholder, endX + 4f, baselineY)
            }

            // Bracket match box
            if (matchedBracket != null && matchedBracket.line == docLine) {
                val bx = gutterWidthPx + 8f + matchedBracket.column * charWidthPx - horizontalScrollPx
                ctx.setStrokeColor(theme.bracketMatchBorder)
                ctx.setLineWidth(1.2f)
                ctx.strokeRect(bx, rowTop + 1f, charWidthPx, lineHeightPx - 2f)
            }

            // Diagnostic squiggles on this line
            for (diag in diagnosticsList) {
                val ds = diag.range.normalizedStart
                val de = diag.range.normalizedEnd
                if (docLine in ds.line..de.line) {
                    val startCol = if (docLine == ds.line) ds.column else 0
                    val endCol = if (docLine == de.line) de.column else document.getLine(docLine).length
                    val sx = gutterWidthPx + 8f + startCol * charWidthPx - horizontalScrollPx
                    val sw = ((endCol - startCol).coerceAtLeast(1)) * charWidthPx
                    drawSquiggleUnderline(ctx, sx, rowTop + lineHeightPx - 2f, sw, theme.colorForDiagnostic(diag.severity))
                }
            }

            // Primary Caret
            if (docLine == document.primaryCursor.line) {
                val caretX = gutterWidthPx + 8f + document.primaryCursor.column * charWidthPx - horizontalScrollPx
                ctx.setFillColor(theme.caretColor)
                ctx.fillRect(caretX, rowTop + 2f, 2f, lineHeightPx - 4f)
            }

            visualRow++
            docLine++
        }

        // Hover Diagnostic Popover
        hoveredDiagnostic?.let { diag ->
            val popX = (gutterWidthPx + 24f).coerceAtMost(w - 260f)
            val popY = 12f
            ctx.setFillColor(theme.gutterBackground)
            ctx.fillRect(popX, popY, 250f, 28f)
            ctx.setStrokeColor(theme.colorForDiagnostic(diag.severity))
            ctx.strokeRect(popX, popY, 250f, 28f)
            ctx.setFillColor(theme.caretColor)
            ctx.fillText("[${diag.severity.name}] ${diag.message}", popX + 8f, popY + 18f)
        }

        // Autocomplete / IntelliSense Popup
        if (activeCompletions.isNotEmpty()) {
            val popupX = (gutterWidthPx + 8f + document.primaryCursor.column * charWidthPx).coerceAtMost(w - 220f)
            val popupY = ((document.primaryCursor.line - firstVisibleLine + 1) * lineHeightPx).coerceIn(20f, h - 140f)
            val maxItems = activeCompletions.size.coerceAtMost(6)
            val popupH = maxItems * 22f + 6f
            ctx.setFillColor(theme.gutterBackground)
            ctx.fillRect(popupX, popupY, 210f, popupH)
            ctx.setStrokeColor(theme.bracketMatchBorder)
            ctx.strokeRect(popupX, popupY, 210f, popupH)
            for (idx in 0 until maxItems) {
                val item = activeCompletions[idx]
                val iy = popupY + 3f + idx * 22f
                if (idx == selectedCompletionIndex) {
                    ctx.setFillColor(theme.selectionBackground)
                    ctx.fillRect(popupX + 1f, iy, 208f, 20f)
                }
                ctx.setFillColor(theme.colorForToken(SyntaxTokenType.KEYWORD))
                ctx.fillText(item.kind.badgeChar, popupX + 6f, iy + 15f)
                ctx.setFillColor(theme.caretColor)
                ctx.fillText(item.label, popupX + 24f, iy + 15f)
            }
        }
    }

    private fun drawSquiggleUnderline(
        ctx: ImageBuffer2DContext,
        startX: Float,
        y: Float,
        width: Float,
        color: RgbaColor
    ) {
        ctx.setStrokeColor(color)
        ctx.setLineWidth(1.3f)
        ctx.beginPath()
        var x = startX
        val endX = startX + width
        var up = true
        ctx.moveTo(x, y)
        while (x < endX) {
            x += 3f
            val wy = if (up) y - 2f else y + 1.5f
            ctx.lineTo(x.coerceAtMost(endX), wy)
            up = !up
        }
        ctx.stroke()
    }

    private fun yToDocumentLine(y: Float): Int {
        val targetVisualRow = (y / lineHeightPx).toInt().coerceAtLeast(0)
        var visualRow = 0
        var docLine = firstVisibleLine
        while (docLine < document.lineCount) {
            if (!foldingRegions.any { it.containsHiddenLine(docLine) }) {
                if (visualRow == targetVisualRow) return docLine
                visualRow++
            }
            docLine++
        }
        return (document.lineCount - 1).coerceAtLeast(0)
    }

    private fun pointerToCursorPosition(x: Float, y: Float): CursorPosition {
        val lineIdx = yToDocumentLine(y)
        val lineText = document.getLine(lineIdx)
        val col = (((x - gutterWidthPx - 8f + horizontalScrollPx) / charWidthPx) + 0.35f)
            .toInt()
            .coerceIn(0, lineText.length)
        return CursorPosition(lineIdx, col)
    }

    private fun defaultCompletionProvider(): CodeCompletionProvider =
        CodeCompletionProvider { _, _, prefix ->
            val keywords = SyntaxTokenizer.keywordsFor(language)
            keywords
                .filter { prefix.isEmpty() || it.startsWith(prefix, ignoreCase = true) }
                .sorted()
                .map { kw -> CompletionItem(label = kw, kind = CompletionItemKind.KEYWORD) }
        }
}
