package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssShadowList
import com.nexustools.genericui.style.gradient.LinearGradient
import com.nexustools.genericui.style.gradient.RadialGradient
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Low-level Bootstrap 5.3 vector & pixel painter operating on `:extensions:imageprocessing`'s [ImageBuffer2DContext].
 *
 * Renders browser-identical Bootstrap 5.3 surfaces, linear/radial gradients, CSS `background-image`
 * (`cover`, `contain`, `100% 100%`, `auto`, `repeat`, `no-repeat`, `repeat-x`, `repeat-y`, `center`, `left`, `right`, `top`, `bottom`),
 * multi-layer box shadows, text shadows, focus rings (`.25rem` `rgba(..., 0.25)`), rounded borders,
 * aligned/wrapped/truncated typography, form controls, and transparent hole punch-outs for underlay native/extension widgets.
 */
object AlienBootstrapPainter {

    private val backgroundImages = mutableMapOf<String, ImageBuffer?>()
    private val loadingUrls = mutableSetOf<String>()

    fun clearBackgroundImageCache() {
        backgroundImages.clear()
        loadingUrls.clear()
    }

    /**
     * Cuts a transparent hole (`rgba(0, 0, 0, 0)`) into [ctx] so a native widget or extension surface
     * (`UnalienContainer`, `GLCanvas`, `VKCanvas`, `VideoPlayer`, `WebView`, etc.) positioned below the
     * `Canvas2D` is visible through the canvas while higher Z-order Alien components can still paint over it.
     */
    fun cutTransparentHole(
        ctx: ImageBuffer2DContext,
        bounds: Rect,
        borderRadiusPx: Float = 0f
    ) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        val x = bounds.x.toFloat()
        val y = bounds.y.toFloat()
        val w = bounds.width.toFloat()
        val h = bounds.height.toFloat()

        // Clear the rectangular hole directly on the context and backing ImageBuffer
        ctx.clearRect(x, y, w, h)
        val buf = ctx.imageBuffer
        val startX = bounds.x.coerceIn(0, buf.width)
        val endX = bounds.right.coerceIn(0, buf.width)
        val startY = bounds.y.coerceIn(0, buf.height)
        val endY = bounds.bottom.coerceIn(0, buf.height)
        for (py in startY until endY) {
            for (px in startX until endX) {
                buf.setPixel(px, py, RgbaColor.TRANSPARENT)
            }
        }
    }

    /**
     * Paints a complete Bootstrap 5.3 box surface (drop shadow, focus ring, background color or gradient,
     * and rounded border) inside [bounds].
     */
    fun paintBoxSurface(
        ctx: ImageBuffer2DContext,
        bounds: Rect,
        style: AlienResolvedVisualStyle
    ) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        val x = bounds.x.toFloat()
        val y = bounds.y.toFloat()
        val w = bounds.width.toFloat()
        val h = bounds.height.toFloat()
        val baseRadius = when {
            style.borderRadiusPercent != null -> (style.borderRadiusPercent / 100f) * min(w, h)
            style.borderRadiusPx > 0f -> style.borderRadiusPx
            style.cornerRadii != null -> style.cornerRadii.topLeft.resolvePx(min(w, h), 16f, style.fontSizePx) ?: 0f
            else -> 0f
        }
        val r = min(baseRadius.coerceAtLeast(0f), min(w, h) * 0.5f)

        ctx.save()
        ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))

        // 1. Focus Ring (`0 0 0 0.25rem rgba(13, 110, 253, 0.25)`)
        val focusColor = style.focusRingColor
        if (focusColor != null && style.focusRingSpreadPx > 0f) {
            val spread = style.focusRingSpreadPx
            ctx.setFillColor(focusColor)
            fillRoundedRect(ctx, x - spread, y - spread, w + spread * 2f, h + spread * 2f, r + spread)
        }

        // 2. Box Shadow (`shadow-sm`, `shadow`, `shadow-lg`, or custom CSS `box-shadow`)
        val shadowList = style.boxShadowList
        if (shadowList != null && !shadowList.isNone && shadowList.shadows.isNotEmpty()) {
            paintTypedBoxShadows(ctx, x, y, w, h, r, shadowList, style.fontSizePx)
        } else if (!style.boxShadowCss.isNullOrBlank() && !style.boxShadowCss.equals("none", ignoreCase = true)) {
            paintSoftBoxShadow(ctx, x, y, w, h, r, style.boxShadowCss)
        }

        // 3. Background Gradient or Solid Background Color
        val gradient = style.backgroundGradient
        if (gradient != null) {
            when (gradient) {
                is LinearGradient -> {
                    val stops = gradient.resolvedStops
                    val c0 = stops.firstOrNull()?.second ?: style.backgroundColor
                    val c1 = stops.lastOrNull()?.second ?: c0
                    val rad = (gradient.angleDegrees - 90f) * (3.1415927f / 180f)
                    val cx = x + w * 0.5f
                    val cy = y + h * 0.5f
                    val halfSpan = max(w, h) * 0.5f
                    val x0 = cx - cos(rad) * halfSpan
                    val y0 = cy - sin(rad) * halfSpan
                    val x1 = cx + cos(rad) * halfSpan
                    val y1 = cy + sin(rad) * halfSpan
                    ctx.fillLinearGradient(x0, y0, x1, y1, c0, c1, x, y, w, h)
                }
                is RadialGradient -> {
                    val stops = gradient.resolvedStops
                    val outer = stops.lastOrNull()?.second ?: style.backgroundColor
                    val inner = stops.firstOrNull()?.second ?: outer
                    ctx.setFillColor(outer)
                    fillRoundedRect(ctx, x, y, w, h, r)
                    ctx.setFillColor(inner)
                    fillRoundedRect(ctx, x + w * 0.15f, y + h * 0.15f, w * 0.7f, h * 0.7f, r)
                }
                else -> {
                    val mid = gradient.interpolateAt(0.5f)
                    ctx.setFillColor(mid)
                    fillRoundedRect(ctx, x, y, w, h, r)
                }
            }
        } else if (style.backgroundColor.a > 0) {
            ctx.setFillColor(style.backgroundColor)
            fillRoundedRect(ctx, x, y, w, h, r)
        }

        // 3b. CSS Background Image (`background-image: url(...)`, `background-size`, `background-repeat`, `background-position`)
        if (!style.backgroundImageUrl.isNullOrBlank()) {
            paintBackgroundImage(ctx, x, y, w, h, r, style)
        }

        // 4. Rounded Border
        if (style.borderWidthPx > 0f && style.borderColor.a > 0 && !style.borderStyle.equals("none", ignoreCase = true)) {
            ctx.setStrokeColor(style.borderColor)
            ctx.setLineWidth(style.borderWidthPx)
            strokeRoundedRect(ctx, x, y, w, h, r)
        }

        ctx.restore()
    }

    private fun resolveBackgroundImage(url: String): ImageBuffer? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null
        backgroundImages[cleanUrl]?.let { return it }
        if (cleanUrl in loadingUrls) return null

        loadingUrls.add(cleanUrl)
        return try {
            val loaded = runCatching {
                ImageCacheManager.loadCachedOrFetch(cleanUrl)
                    ?: cleanUrl.substringBefore('?').substringBefore('#').takeIf { it != cleanUrl }?.let { stripped ->
                        ImageCacheManager.loadCachedOrFetch(stripped)
                    }
            }.getOrNull()
            if (loaded != null && loaded.width > 0 && loaded.height > 0) {
                val rgba = loaded.asRgba8888()
                backgroundImages[cleanUrl] = rgba
                rgba
            } else {
                null
            }
        } finally {
            loadingUrls.remove(cleanUrl)
        }
    }

    private fun paintBackgroundImage(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        style: AlienResolvedVisualStyle
    ) {
        val url = style.backgroundImageUrl?.trim() ?: return
        val img = resolveBackgroundImage(url) ?: return
        if (img.width <= 0 || img.height <= 0 || w <= 0f || h <= 0f) return

        val imgW = img.width.toFloat()
        val imgH = img.height.toFloat()
        val sizeMode = style.backgroundSize.trim().lowercase()
        val (tileW, tileH) = when {
            sizeMode == "cover" -> {
                val scale = max(w / imgW, h / imgH)
                (imgW * scale).coerceAtLeast(1f) to (imgH * scale).coerceAtLeast(1f)
            }
            sizeMode == "contain" -> {
                val scale = min(w / imgW, h / imgH)
                (imgW * scale).coerceAtLeast(1f) to (imgH * scale).coerceAtLeast(1f)
            }
            sizeMode == "100% 100%" || sizeMode == "100%" || sizeMode == "fill" -> {
                w.coerceAtLeast(1f) to h.coerceAtLeast(1f)
            }
            else -> {
                val parts = sizeMode.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val pw = parts.getOrNull(0)?.let { AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, w) }
                val ph = parts.getOrNull(1)?.let { AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, h) }
                val resolvedW = pw ?: imgW
                val resolvedH = ph ?: if (pw != null && imgW > 0f) (pw / imgW) * imgH else imgH
                resolvedW.coerceAtLeast(1f) to resolvedH.coerceAtLeast(1f)
            }
        }

        val posTokens = style.backgroundPosition.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val drawX = when {
            "left" in posTokens || posTokens.firstOrNull() == "0" || posTokens.firstOrNull() == "0%" -> x
            "right" in posTokens || posTokens.firstOrNull() == "100%" -> x + (w - tileW)
            else -> x + (w - tileW) * 0.5f
        }
        val drawY = when {
            "top" in posTokens || posTokens.lastOrNull() == "0" || posTokens.lastOrNull() == "0%" -> y
            "bottom" in posTokens || posTokens.lastOrNull() == "100%" -> y + (h - tileH)
            else -> y + (h - tileH) * 0.5f
        }

        val repeatMode = style.backgroundRepeat.trim().lowercase()

        ctx.save()
        ctx.beginPath()
        if (radius > 0.5f) {
            ctx.roundRect(x, y, w, h, radius)
        } else {
            ctx.rect(x, y, w, h)
        }
        ctx.clip()

        when (repeatMode) {
            "no-repeat" -> {
                ctx.drawImage(img, drawX, drawY, tileW, tileH)
            }
            "repeat-x" -> {
                var cx = drawX
                while (cx > x) cx -= tileW
                while (cx < x + w) {
                    ctx.drawImage(img, cx, drawY, tileW, tileH)
                    cx += tileW
                }
            }
            "repeat-y" -> {
                var cy = drawY
                while (cy > y) cy -= tileH
                while (cy < y + h) {
                    ctx.drawImage(img, drawX, cy, tileW, tileH)
                    cy += tileH
                }
            }
            else -> {
                var startX = drawX
                while (startX > x) startX -= tileW
                var startY = drawY
                while (startY > y) startY -= tileH
                var cy = startY
                while (cy < y + h) {
                    var cx = startX
                    while (cx < x + w) {
                        ctx.drawImage(img, cx, cy, tileW, tileH)
                        cx += tileW
                    }
                    cy += tileH
                }
            }
        }

        ctx.restore()
    }

    /**
     * Paints single-line or multi-line text inside [contentBounds] respecting alignment, text transform,
     * text shadow, word wrapping, and ellipsis truncation.
     */
    fun paintText(
        ctx: ImageBuffer2DContext,
        text: String,
        contentBounds: Rect,
        style: AlienResolvedVisualStyle,
        verticalCenter: Boolean = true,
        maxLines: Int? = null
    ) {
        if (text.isEmpty() || contentBounds.width <= 0 || contentBounds.height <= 0) return

        val transformed = when (style.textTransform) {
            "uppercase" -> text.uppercase()
            "lowercase" -> text.lowercase()
            "capitalize" -> text.split(' ').joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            else -> text
        }

        ctx.save()
        ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
        ctx.setFont(style.fontFamily, style.fontSizePx, style.fontWeight, style.italic)
        ctx.setFillColor(style.textColor)

        // Apply CSS `text-shadow` if specified (e.g. `0 0 2px #000`)
        val typedTextShadow = style.textShadow
        val parsedShadow = if (typedTextShadow != null) {
            ParsedTextShadow(
                offsetX = typedTextShadow.offsetX.resolvePx(style.fontSizePx, 16f, style.fontSizePx) ?: 0f,
                offsetY = typedTextShadow.offsetY.resolvePx(style.fontSizePx, 16f, style.fontSizePx) ?: 0f,
                blurPx = (typedTextShadow.blurRadius.resolvePx(style.fontSizePx, 16f, style.fontSizePx) ?: 0f).coerceAtLeast(0f),
                color = typedTextShadow.color
            )
        } else {
            parseTextShadow(style.textShadowCss, style.fontSizePx)
        }
        if (parsedShadow != null) {
            ctx.setShadow(
                blur = parsedShadow.blurPx,
                color = parsedShadow.color,
                offsetX = parsedShadow.offsetX,
                offsetY = parsedShadow.offsetY
            )
        } else {
            ctx.clearShadow()
        }

        val maxW = contentBounds.width.toFloat().coerceAtLeast(1f)
        val isSingleLine = maxLines == 1 || style.whiteSpace.equals("nowrap", ignoreCase = true)

        if (isSingleLine) {
            val displayLine = if (style.textOverflow == "ellipsis") {
                ellipsizeToWidth(ctx, transformed, maxW)
            } else {
                transformed
            }
            val measuredW = ctx.measureTextWidth(displayLine)
            val drawX = when (style.textAlign) {
                "center" -> contentBounds.x + (contentBounds.width - measuredW) * 0.5f
                "right", "end" -> contentBounds.right - measuredW
                else -> contentBounds.x.toFloat()
            }
            val drawY = if (verticalCenter) {
                contentBounds.y + (contentBounds.height + style.fontSizePx * 0.68f) * 0.5f
            } else {
                contentBounds.y + style.fontSizePx
            }
            ctx.fillText(displayLine, drawX, drawY)
            paintTextDecorationLine(ctx, drawX, drawY, measuredW, style.fontSizePx, style.textDecoration)
        } else {
            val lines = wrapTextLines(ctx, transformed, maxW, maxLines)
            val lineHeight = style.lineHeightPx?.coerceAtLeast(style.fontSizePx)
                ?: (style.fontSizePx * 1.35f).coerceAtLeast(style.fontSizePx + 2f)
            val totalBlockH = lines.size * lineHeight
            var cursorY = if (verticalCenter && totalBlockH < contentBounds.height) {
                contentBounds.y + (contentBounds.height - totalBlockH) * 0.5f + style.fontSizePx * 0.85f
            } else {
                contentBounds.y + style.fontSizePx * 0.9f
            }
            for (line in lines) {
                val measuredW = ctx.measureTextWidth(line)
                val drawX = when (style.textAlign) {
                    "center" -> contentBounds.x + (contentBounds.width - measuredW) * 0.5f
                    "right", "end" -> contentBounds.right - measuredW
                    else -> contentBounds.x.toFloat()
                }
                ctx.fillText(line, drawX, cursorY)
                paintTextDecorationLine(ctx, drawX, cursorY, measuredW, style.fontSizePx, style.textDecoration)
                cursorY += lineHeight
            }
        }

        ctx.clearShadow()
        ctx.restore()
    }

    fun fillRoundedRect(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float
    ) {
        if (radius <= 0.5f) {
            ctx.fillRect(x, y, w, h)
        } else {
            ctx.beginPath()
            ctx.roundRect(x, y, w, h, radius)
            ctx.fill()
        }
    }

    fun strokeRoundedRect(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float
    ) {
        if (radius <= 0.5f) {
            ctx.strokeRect(x, y, w, h)
        } else {
            ctx.beginPath()
            ctx.roundRect(x, y, w, h, radius)
            ctx.stroke()
        }
    }

    private fun paintTextDecorationLine(
        ctx: ImageBuffer2DContext,
        drawX: Float,
        baselineY: Float,
        width: Float,
        fontSizePx: Float,
        decoration: CssTextDecoration
    ) {
        if (decoration == CssTextDecoration.NONE || width <= 0f) return
        val lineY = when (decoration) {
            CssTextDecoration.UNDERLINE -> baselineY + 2f
            CssTextDecoration.LINE_THROUGH -> baselineY - fontSizePx * 0.3f
            CssTextDecoration.OVERLINE -> baselineY - fontSizePx * 0.85f
            CssTextDecoration.NONE -> return
        }
        ctx.fillRect(drawX, lineY, width, 1f)
    }

    private fun paintTypedBoxShadows(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
        shadowList: CssShadowList,
        fontSizePx: Float
    ) {
        for (shadow in shadowList.shadows) {
            if (shadow.inset) continue
            val ox = shadow.offsetX.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
            val oy = shadow.offsetY.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
            val blur = (shadow.blurRadius.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f).coerceAtLeast(0f)
            val spread = shadow.spreadRadius.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
            val expand = spread + blur * 0.35f
            ctx.setFillColor(shadow.color)
            fillRoundedRect(
                ctx,
                x + ox - expand,
                y + oy - expand,
                (w + expand * 2f).coerceAtLeast(0f),
                (h + expand * 2f).coerceAtLeast(0f),
                (r + expand).coerceAtLeast(0f)
            )
        }
    }

    private fun paintSoftBoxShadow(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
        shadowCss: String
    ) {
        val isLarge = shadowCss.contains("48px") || shadowCss.contains("16px")
        val layers = if (isLarge) 3 else 2
        for (i in layers downTo 1) {
            val spread = i * 2f
            val offsetY = i * 1.5f
            ctx.setFillColor(RgbaColor(0, 0, 0, (24 / i).coerceAtLeast(6)))
            fillRoundedRect(ctx, x - spread * 0.5f, y + offsetY, w + spread, h + spread, r + spread * 0.5f)
        }
    }

    private fun wrapTextLines(
        ctx: ImageBuffer2DContext,
        text: String,
        maxWidthPx: Float,
        maxLines: Int?
    ): List<String> {
        val paragraphs = text.split('\n')
        val out = mutableListOf<String>()
        for (para in paragraphs) {
            val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                out.add("")
                continue
            }
            var current = words[0]
            for (i in 1 until words.size) {
                val candidate = "$current ${words[i]}"
                if (ctx.measureTextWidth(candidate) <= maxWidthPx) {
                    current = candidate
                } else {
                    out.add(current)
                    current = words[i]
                }
            }
            out.add(current)
        }
        return if (maxLines != null && maxLines > 0 && out.size > maxLines) {
            out.take(maxLines)
        } else {
            out
        }
    }

    private fun ellipsizeToWidth(ctx: ImageBuffer2DContext, text: String, maxWidthPx: Float): String {
        if (ctx.measureTextWidth(text) <= maxWidthPx) return text
        val ellipsis = "..."
        val ellipsisW = ctx.measureTextWidth(ellipsis)
        if (ellipsisW >= maxWidthPx) return ""
        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end) + ellipsis
            if (ctx.measureTextWidth(candidate) <= maxWidthPx) {
                return candidate
            }
            end--
        }
        return ellipsis
    }

    private data class ParsedTextShadow(
        val offsetX: Float,
        val offsetY: Float,
        val blurPx: Float,
        val color: RgbaColor
    )

    private fun parseTextShadow(raw: String?, fontSizePx: Float): ParsedTextShadow? {
        if (raw.isNullOrBlank() || raw.equals("none", ignoreCase = true)) return null
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size < 2) return null
        val numericTokens = mutableListOf<Float>()
        var color: RgbaColor? = null
        for (tok in tokens) {
            val len = AlienCssStyleResolver.parseLengthPx(tok, fontSizePx, null)
            if (len != null) {
                numericTokens.add(len)
            } else {
                RgbaColor.fromCss(tok)?.let { color = it }
            }
        }
        val ox = numericTokens.getOrElse(0) { 0f }
        val oy = numericTokens.getOrElse(1) { 0f }
        val blur = numericTokens.getOrElse(2) { 2f }.coerceAtLeast(0f)
        return ParsedTextShadow(
            offsetX = ox,
            offsetY = oy,
            blurPx = blur,
            color = color ?: RgbaColor(0, 0, 0, 220)
        )
    }
}
