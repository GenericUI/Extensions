package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.RgbaColor

/**
 * Multi-page high-resolution printable 2D canvas document (`DCGame`'s `CharGenView` A4 `2480 x 3508`
 * character sheet & handout card generator).
 *
 * Supports:
 * - Creating fixed and dynamic overflow pages ([createPage], [createOverflowTextPages]).
 * - Rendering 4-up (`2x2`) [CanvasHandoutCardSpec] grids with automatic overflow continuation cards
 *   ([renderHandoutCardPages]).
 * - Triggering document print ([printDocument]).
 */
open class PrintableCanvasDocument(
    val pageWidthPx: Int = 2480,
    val pageHeightPx: Int = 3508
) : VStack() {

    private val _pages = mutableListOf<Canvas2DExtSurface>()
    val pages: List<Canvas2DExtSurface>
        get() = _pages

    var printCount: Int = 0
        private set

    var onPrintRequested: ((List<Canvas2DExtSurface>) -> Unit)? = null

    init {
        addClass("printable-canvas-document")
    }

    fun clearPages() {
        _pages.clear()
        clearChildren()
    }

    /**
     * Appends a new [Canvas2DExtSurface] page (`pageWidthPx x pageHeightPx`), optionally drawing
     * [backgroundImage] and invoking [renderBlock] on its [Canvas2DContext].
     */
    fun createPage(
        backgroundImage: ImageBuffer? = null,
        renderBlock: ((Canvas2DContext) -> Unit)? = null
    ): Canvas2DExtSurface {
        val surface = Canvas2DExtSurface(pageWidthPx, pageHeightPx).apply {
            addClass("d-block w-100 printable-page")
        }
        val ctx = surface.resolveRenderingContext()
        if (ctx != null) {
            if (backgroundImage != null) {
                Canvas2DExtension.drawImage(ctx, backgroundImage, 0f, 0f)
            }
            renderBlock?.invoke(ctx)
        }
        _pages.add(surface)
        addChild(surface)
        return surface
    }

    /**
     * Renders [initialOverflowText] across as many continuation pages as needed until no overflow
     * remains (matching `DCGame`'s `equipmentExtended` multi-page loop).
     */
    fun createOverflowTextPages(
        initialOverflowText: String,
        textBounds: Rect,
        backgroundImage: ImageBuffer? = null,
        fontFamily: String = "serif",
        fontSizePx: Float = 36f,
        maxContinuationPages: Int = 16,
        perPageHeaderRender: ((Canvas2DContext, Int) -> Unit)? = null
    ): List<Canvas2DExtSurface> {
        var remaining = initialOverflowText
        val created = mutableListOf<Canvas2DExtSurface>()
        var pageIdx = 0
        while (remaining.isNotEmpty() && pageIdx < maxContinuationPages) {
            val currentIdx = pageIdx++
            val page = createPage(backgroundImage) { ctx ->
                perPageHeaderRender?.invoke(ctx, currentIdx)
                val nextRemaining = CanvasTextLayoutHelper.fillTextAreaWithOverflow(
                    ctx = ctx,
                    text = remaining,
                    bounds = textBounds,
                    fontFamily = fontFamily,
                    fontSizePx = fontSizePx
                )
                remaining = if (nextRemaining == remaining) "" else nextRemaining
            }
            created.add(page)
        }
        return created
    }

    /**
     * Renders [cards] in a multi-up grid (default `2x2` = 4 cards per page), automatically generating
     * continuation cards when a card's `description` overflows its description box.
     */
    fun renderHandoutCardPages(
        cards: List<CanvasHandoutCardSpec>,
        columns: Int = 2,
        rows: Int = 2,
        fontFamily: String = "serif"
    ): List<Canvas2DExtSurface> {
        if (cards.isEmpty()) return emptyList()
        val safeCols = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cardsPerPage = safeCols * safeRows
        val cellW = pageWidthPx / safeCols
        val cellH = pageHeightPx / safeRows

        val expandedQueue = ArrayDeque<CanvasHandoutCardSpec>(cards)
        val createdPages = mutableListOf<Canvas2DExtSurface>()

        while (expandedQueue.isNotEmpty()) {
            val page = createPage { ctx ->
                for (slot in 0 until cardsPerPage) {
                    val card = expandedQueue.removeFirstOrNull() ?: break
                    val col = slot % safeCols
                    val row = slot / safeCols
                    val originX = col * cellW
                    val originY = row * cellH

                    card.backgroundImage?.let {
                        Canvas2DExtension.drawImage(ctx, it, originX.toFloat(), originY.toFloat())
                    }
                    card.iconImage?.let {
                        Canvas2DExtension.drawImage(ctx, it, (originX + 40).toFloat(), (originY + 40).toFloat())
                    }
                    card.sideImage?.let {
                        Canvas2DExtension.drawImage(ctx, it, (originX + cellW - it.width - 40).toFloat(), (originY + 40).toFloat())
                    }

                    // Title
                    CanvasTextLayoutHelper.fillTextFitted(
                        ctx = ctx,
                        text = card.title,
                        bounds = Rect(originX + 60, originY + 50, cellW - 120, 64),
                        fontFamily = fontFamily,
                        initialFontSizePx = 48f,
                        horizontalAlign = TextAlignment.CENTER
                    )

                    // Category tag & subHeader
                    if (card.categoryTag.isNotEmpty()) {
                        CanvasTextLayoutHelper.fillTextFitted(
                            ctx = ctx,
                            text = card.categoryTag,
                            bounds = Rect(originX + 60, originY + 120, cellW - 120, 40),
                            fontFamily = fontFamily,
                            initialFontSizePx = 30f,
                            horizontalAlign = TextAlignment.CENTER
                        )
                    }
                    if (card.subHeader.isNotEmpty()) {
                        CanvasTextLayoutHelper.fillTextFitted(
                            ctx = ctx,
                            text = card.subHeader,
                            bounds = Rect(originX + 60, originY + 170, cellW - 120, 40),
                            fontFamily = fontFamily,
                            initialFontSizePx = 28f,
                            italic = true,
                            horizontalAlign = TextAlignment.START
                        )
                    }

                    // Description with overflow continuation card generation
                    val descBounds = Rect(originX + 60, originY + 230, cellW - 120, cellH - 320)
                    val overflow = CanvasTextLayoutHelper.fillTextAreaWithOverflow(
                        ctx = ctx,
                        text = card.description,
                        bounds = descBounds,
                        fontFamily = fontFamily,
                        fontSizePx = 30f,
                        color = RgbaColor.BLACK
                    )
                    if (overflow.isNotEmpty() && overflow != card.description) {
                        expandedQueue.addFirst(
                            card.copy(
                                subHeader = "(Continued)",
                                description = overflow
                            )
                        )
                    }
                }
            }
            createdPages.add(page)
        }
        return createdPages
    }

    fun printDocument() {
        printCount++
        onPrintRequested?.invoke(_pages)
    }
}
