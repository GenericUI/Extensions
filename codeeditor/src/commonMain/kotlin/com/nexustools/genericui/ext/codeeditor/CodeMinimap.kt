package com.nexustools.genericui.ext.codeeditor

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface

/**
 * IDE minimap overview component rendering scaled syntax-colored source lines, diagnostic markers,
 * and the visible viewport slider onto a hardware-accelerated [Canvas2DExtSurface].
 */
class CodeMinimap(
    var document: CodeDocumentBuffer = CodeDocumentBuffer(),
    var tokenizer: SyntaxTokenizer = SyntaxTokenizer(),
    var theme: CodeEditorTheme = CodeEditorTheme.DARK_IDE,
    width: Int = 96,
    height: Int = 600
) : Canvas2DExtSurface(width = width, height = height) {

    var firstVisibleLine: Int = 0
    var visibleLineCount: Int = 35
    var diagnostics: List<CodeDiagnostic> = emptyList()
    var onScrollToLineRequested: ((Int) -> Unit)? = null

    init {
        addClass("code-minimap")
        registerVBlankRender { ctx, _, _, _ ->
            renderMinimap(ctx)
        }
        setOnPointerDown { event ->
            handlePointerJump(event.y)
        }
        setOnPointerMove { event ->
            if (event.buttonsMask != 0) {
                handlePointerJump(event.y)
            }
        }
    }

    private fun handlePointerJump(pointerY: Float) {
        val lineScale = 3f
        val clickedLine = (pointerY / lineScale).toInt().coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
        val targetFirst = (clickedLine - visibleLineCount / 2).coerceAtLeast(0)
        firstVisibleLine = targetFirst
        onScrollToLineRequested?.invoke(targetFirst)
        requestRender()
    }

    fun renderMinimap(ctx: Canvas2DContext) {
        val w = ctx.width.toFloat().coerceAtLeast(60f)
        val h = ctx.height.toFloat().coerceAtLeast(100f)

        ctx.setFillColor(theme.gutterBackground)
        ctx.fillRect(0f, 0f, w, h)

        val lines = document.getLines()
        val tokenized = tokenizer.tokenizeDocument(lines)
        val rowHeight = 3f
        val charWidth = 1.2f
        val leftPad = 6f
        val maxRows = (h / rowHeight).toInt().coerceAtMost(tokenized.size)

        for (lineIdx in 0 until maxRows) {
            val y = lineIdx * rowHeight
            val tokens = tokenized[lineIdx]
            for (token in tokens) {
                if (token.type == SyntaxTokenType.WHITESPACE) continue
                val color = theme.colorForToken(token.type)
                ctx.setFillColor(color)
                val x = leftPad + token.startColumn * charWidth
                val spanW = (token.length * charWidth).coerceAtMost(w - x - 4f)
                if (spanW > 0f) {
                    ctx.fillRect(x, y, spanW, 1.8f)
                }
            }
        }

        // Render diagnostic ticks along the right edge of the minimap
        for (diag in diagnostics) {
            val diagY = diag.range.normalizedStart.line * rowHeight
            if (diagY in 0f..h) {
                ctx.setFillColor(theme.colorForDiagnostic(diag.severity))
                ctx.fillRect(w - 6f, diagY, 5f, 2.5f)
            }
        }

        // Render visible viewport slider overlay
        val sliderTop = (firstVisibleLine * rowHeight).coerceIn(0f, h)
        val sliderHeight = (visibleLineCount * rowHeight).coerceIn(16f, (h - sliderTop).coerceAtLeast(16f))
        ctx.setFillColor(theme.minimapViewportOverlay)
        ctx.fillRect(0f, sliderTop, w, sliderHeight)
        ctx.setStrokeColor(theme.gutterText)
        ctx.setLineWidth(1f)
        ctx.strokeRect(0.5f, sliderTop + 0.5f, w - 1f, sliderHeight - 1f)
    }
}
