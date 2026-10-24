package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.RgbaColor

/**
 * 2D canvas text layout & pagination helper (`DCGame`'s `CharGenView.fillText` and
 * `fillTextAreaWithOverflow`).
 *
 * Provides:
 * - [fillTextFitted]: Single-line text drawing that automatically shrinks `fontSizePx` (or invokes
 *   a custom truncation function) until the measured width fits within `bounds.width`, supporting
 *   horizontal (`START`, `CENTER`, `END`) and vertical alignment.
 * - [fillTextAreaWithOverflow]: Multi-line word-wrapped text area drawing with `*italic*` span
 *   support and automatic overflow pagination — returns the remaining unrendered text string when
 *   the vertical height of `bounds` is exhausted so callers can flow it onto a continuation page.
 */
object CanvasTextLayoutHelper {

    fun fillTextFitted(
        ctx: Canvas2DContext,
        text: String,
        bounds: Rect,
        fontFamily: String = "serif",
        initialFontSizePx: Float = 44f,
        minFontSizePx: Float = 10f,
        fontWeight: Int = 400,
        italic: Boolean = false,
        color: RgbaColor = RgbaColor.BLACK,
        horizontalAlign: TextAlignment = TextAlignment.START,
        verticalCenter: Boolean = true,
        customTruncator: ((String) -> String)? = null
    ): Float {
        if (text.isEmpty() || bounds.width <= 0) return initialFontSizePx
        ctx.setFillColor(color)

        var currentText = text
        var currentSize = initialFontSizePx.coerceAtLeast(minFontSizePx)
        ctx.setFont(fontFamily, currentSize, fontWeight, italic)

        var measuredWidth = ctx.measureTextWidth(currentText)
        val maxW = bounds.width.toFloat()

        while (measuredWidth > maxW && currentSize > minFontSizePx) {
            if (customTruncator != null) {
                val nextText = customTruncator(currentText)
                if (nextText != currentText && nextText.isNotEmpty()) {
                    currentText = nextText
                    measuredWidth = ctx.measureTextWidth(currentText)
                    continue
                }
            }
            currentSize = (currentSize - 1f).coerceAtLeast(minFontSizePx)
            ctx.setFont(fontFamily, currentSize, fontWeight, italic)
            measuredWidth = ctx.measureTextWidth(currentText)
        }

        val drawX = when (horizontalAlign) {
            TextAlignment.CENTER -> bounds.x + (maxW - measuredWidth) * 0.5f
            TextAlignment.END -> bounds.x + (maxW - measuredWidth)
            else -> bounds.x.toFloat()
        }
        val drawY = if (verticalCenter) {
            bounds.y + (bounds.height + currentSize * 0.75f) * 0.5f
        } else {
            bounds.y + currentSize
        }

        ctx.fillText(currentText, drawX, drawY)
        return currentSize
    }

    /**
     * Renders word-wrapped multi-line [text] inside [bounds] (supporting `*italic*` inline markers
     * and newline breaks), returning any remaining unrendered text that overflowed the bottom of
     * [bounds] (or `""` if all text fit).
     */
    fun fillTextAreaWithOverflow(
        ctx: Canvas2DContext,
        text: String,
        bounds: Rect,
        fontFamily: String = "serif",
        fontSizePx: Float = 36f,
        lineHeightMultiplier: Float = 1.2f,
        fontWeight: Int = 400,
        color: RgbaColor = RgbaColor.BLACK
    ): String {
        if (text.isEmpty() || bounds.width <= 0 || bounds.height <= 0) return ""
        ctx.setFillColor(color)
        val lineHeight = (fontSizePx * lineHeightMultiplier).coerceAtLeast(1f)
        val maxWidth = bounds.width.toFloat()
        val maxBottomY = (bounds.y + bounds.height).toFloat()

        var cursorY = bounds.y + fontSizePx
        val paragraphs = text.split('\n')

        for (pIndex in paragraphs.indices) {
            val paragraph = paragraphs[pIndex]
            if (paragraph.isBlank()) {
                if (cursorY + lineHeight > maxBottomY) {
                    return paragraphs.subList(pIndex, paragraphs.size).joinToString("\n")
                }
                cursorY += lineHeight
                continue
            }

            val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var wordIdx = 0
            while (wordIdx < words.size) {
                if (cursorY > maxBottomY) {
                    val remainingInParagraph = words.subList(wordIdx, words.size).joinToString(" ")
                    val remainingParagraphs = if (pIndex + 1 < paragraphs.size) {
                        "\n" + paragraphs.subList(pIndex + 1, paragraphs.size).joinToString("\n")
                    } else {
                        ""
                    }
                    return remainingInParagraph + remainingParagraphs
                }

                val lineWords = mutableListOf<String>()
                while (wordIdx < words.size) {
                    val candidate = if (lineWords.isEmpty()) {
                        words[wordIdx]
                    } else {
                        lineWords.joinToString(" ") + " " + words[wordIdx]
                    }
                    ctx.setFont(fontFamily, fontSizePx, fontWeight, italic = false)
                    val w = ctx.measureTextWidth(stripItalicMarkers(candidate))
                    if (w <= maxWidth || lineWords.isEmpty()) {
                        lineWords.add(words[wordIdx])
                        wordIdx++
                        if (w > maxWidth) break
                    } else {
                        break
                    }
                }

                drawLineWithInlineItalics(
                    ctx = ctx,
                    line = lineWords.joinToString(" "),
                    x = bounds.x.toFloat(),
                    y = cursorY,
                    maxWidth = maxWidth,
                    fontFamily = fontFamily,
                    fontSizePx = fontSizePx,
                    fontWeight = fontWeight
                )
                cursorY += lineHeight
            }
        }

        return ""
    }

    private fun stripItalicMarkers(raw: String): String =
        raw.replace("*", "")

    private fun drawLineWithInlineItalics(
        ctx: Canvas2DContext,
        line: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        fontFamily: String,
        fontSizePx: Float,
        fontWeight: Int
    ) {
        val clean = stripItalicMarkers(line)
        var effectiveSize = fontSizePx
        ctx.setFont(fontFamily, effectiveSize, fontWeight, italic = false)
        while (ctx.measureTextWidth(clean) > maxWidth && effectiveSize > 10f) {
            effectiveSize -= 1f
            ctx.setFont(fontFamily, effectiveSize, fontWeight, italic = false)
        }

        var cursorX = x
        var italicMode = false
        val segments = line.split('*')
        for (seg in segments) {
            if (seg.isNotEmpty()) {
                ctx.setFont(fontFamily, effectiveSize, fontWeight, italic = italicMode)
                ctx.fillText(seg, cursorX, y)
                cursorX += ctx.measureTextWidth(seg)
            }
            italicMode = !italicMode
        }
    }
}
