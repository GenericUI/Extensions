package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssShadowList
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
     * (`UnalienContainer`, `Canvas3D`, `VideoPlayer`, `WebView`, etc.) positioned below the
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
        val radii = resolveCornerRadii(w, h, style)
        val r = radii[0]

        ctx.save()
        ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))

        // 1. Focus Ring (`0 0 0 0.25rem rgba(13, 110, 253, 0.25)`)
        val focusColor = style.focusRingColor
        if (focusColor != null && style.focusRingSpreadPx > 0f) {
            val spread = style.focusRingSpreadPx
            ctx.setFillColor(focusColor)
            fillCornerRoundedRect(
                ctx,
                x - spread,
                y - spread,
                w + spread * 2f,
                h + spread * 2f,
                radii[0] + spread,
                radii[1] + spread,
                radii[2] + spread,
                radii[3] + spread
            )
        }

        // 2. Outer Box Shadow (`shadow-sm`, `shadow`, `shadow-lg`, or custom CSS `box-shadow`)
        val shadowList = style.boxShadowList
        if (shadowList != null && !shadowList.isNone && shadowList.shadows.isNotEmpty()) {
            paintTypedBoxShadows(ctx, x, y, w, h, radii, shadowList, style.fontSizePx, insetOnly = false)
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
                    if (radii.any { it > 0.5f }) {
                        ctx.save()
                        beginCornerRoundedPath(ctx, x, y, w, h, radii)
                        ctx.clip()
                        ctx.fillLinearGradient(x0, y0, x1, y1, c0, c1, x, y, w, h)
                        ctx.restore()
                    } else {
                        ctx.fillLinearGradient(x0, y0, x1, y1, c0, c1, x, y, w, h)
                    }
                }
                is RadialGradient -> {
                    val stops = gradient.resolvedStops
                    val outer = stops.lastOrNull()?.second ?: style.backgroundColor
                    val inner = stops.firstOrNull()?.second ?: outer
                    ctx.setFillColor(outer)
                    fillCornerRoundedRect(ctx, x, y, w, h, radii[0], radii[1], radii[2], radii[3])
                    ctx.setFillColor(inner)
                    fillCornerRoundedRect(ctx, x + w * 0.15f, y + h * 0.15f, w * 0.7f, h * 0.7f, radii[0], radii[1], radii[2], radii[3])
                }
                else -> {
                    val mid = gradient.interpolateAt(0.5f)
                    ctx.setFillColor(mid)
                    fillCornerRoundedRect(ctx, x, y, w, h, radii[0], radii[1], radii[2], radii[3])
                }
            }
        } else if (style.backgroundColor.a > 0) {
            ctx.setFillColor(style.backgroundColor)
            fillCornerRoundedRect(ctx, x, y, w, h, radii[0], radii[1], radii[2], radii[3])
        }

        // 3b. CSS Background Image (`background-image: url(...)`, `background-size`, `background-repeat`, `background-position`)
        if (!style.backgroundImageUrl.isNullOrBlank()) {
            paintBackgroundImage(ctx, x, y, w, h, r, style)
        }

        // 3c. Inset Box Shadows (inside background, behind border)
        if (shadowList != null && !shadowList.isNone && shadowList.shadows.any { it.inset }) {
            paintTypedBoxShadows(ctx, x, y, w, h, radii, shadowList, style.fontSizePx, insetOnly = true)
        }

        // 4. Borders (uniform rounded rect OR per-side widths/colors/styles)
        paintBorders(ctx, x, y, w, h, radii, style)

        // 5. CSS Outline (outside border box)
        if (style.outlineWidthPx > 0f && !style.outlineStyle.equals("none", ignoreCase = true) && style.outlineColor.a > 0) {
            val ow = style.outlineWidthPx
            val oo = style.outlineOffsetPx + ow * 0.5f
            ctx.save()
            ctx.setStrokeColor(style.outlineColor)
            ctx.setLineWidth(ow)
            applyBorderDashStyle(ctx, style.outlineStyle, ow)
            strokeCornerRoundedRect(
                ctx,
                x - oo,
                y - oo,
                w + oo * 2f,
                h + oo * 2f,
                (radii[0] + oo).coerceAtLeast(0f),
                (radii[1] + oo).coerceAtLeast(0f),
                (radii[2] + oo).coerceAtLeast(0f),
                (radii[3] + oo).coerceAtLeast(0f)
            )
            ctx.restore()
        }

        ctx.restore()
    }

    internal fun resolveCornerRadii(w: Float, h: Float, style: AlienResolvedVisualStyle): FloatArray {
        val halfMin = min(w, h) * 0.5f
        val cr = style.cornerRadii
        if (cr != null) {
            val ref = min(w, h)
            return floatArrayOf(
                min((cr.topLeft.resolvePx(ref, 16f, style.fontSizePx) ?: 0f).coerceAtLeast(0f), halfMin),
                min((cr.topRight.resolvePx(ref, 16f, style.fontSizePx) ?: 0f).coerceAtLeast(0f), halfMin),
                min((cr.bottomRight.resolvePx(ref, 16f, style.fontSizePx) ?: 0f).coerceAtLeast(0f), halfMin),
                min((cr.bottomLeft.resolvePx(ref, 16f, style.fontSizePx) ?: 0f).coerceAtLeast(0f), halfMin)
            )
        }
        val base = when {
            style.borderRadiusPercent != null -> (style.borderRadiusPercent / 100f) * min(w, h)
            style.borderRadiusPx > 0f -> style.borderRadiusPx
            else -> 0f
        }
        val clamped = min(base.coerceAtLeast(0f), halfMin)
        return floatArrayOf(clamped, clamped, clamped, clamped)
    }

    private fun beginCornerRoundedPath(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radii: FloatArray
    ) {
        ctx.beginPath()
        if (radii[0] == radii[1] && radii[1] == radii[2] && radii[2] == radii[3]) {
            if (radii[0] <= 0.5f) {
                ctx.rect(x, y, w, h)
            } else {
                ctx.roundRect(x, y, w, h, radii[0])
            }
        } else {
            ctx.roundRect(x, y, w, h, radii[0], radii[1], radii[2], radii[3])
        }
    }

    private fun applyBorderDashStyle(ctx: ImageBuffer2DContext, styleName: String, widthPx: Float) {
        when (styleName.lowercase()) {
            "dashed" -> ctx.setLineDash(floatArrayOf(max(4f, widthPx * 3f), max(3f, widthPx * 2f)))
            "dotted" -> ctx.setLineDash(floatArrayOf(max(1.5f, widthPx), max(2f, widthPx * 1.5f)))
            else -> ctx.setLineDash(FloatArray(0))
        }
    }

    private fun paintBorders(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radii: FloatArray,
        style: AlienResolvedVisualStyle
    ) {
        val bt = if (style.borderTopStyle.equals("none", ignoreCase = true) || style.borderTopColor.a == 0) 0f else style.borderTopWidthPx
        val br = if (style.borderRightStyle.equals("none", ignoreCase = true) || style.borderRightColor.a == 0) 0f else style.borderRightWidthPx
        val bb = if (style.borderBottomStyle.equals("none", ignoreCase = true) || style.borderBottomColor.a == 0) 0f else style.borderBottomWidthPx
        val bl = if (style.borderLeftStyle.equals("none", ignoreCase = true) || style.borderLeftColor.a == 0) 0f else style.borderLeftWidthPx
        if (bt <= 0f && br <= 0f && bb <= 0f && bl <= 0f) return

        val uniform = bt == br && br == bb && bb == bl &&
            style.borderTopColor == style.borderRightColor &&
            style.borderRightColor == style.borderBottomColor &&
            style.borderBottomColor == style.borderLeftColor &&
            style.borderTopStyle.equals(style.borderRightStyle, ignoreCase = true) &&
            style.borderRightStyle.equals(style.borderBottomStyle, ignoreCase = true) &&
            style.borderBottomStyle.equals(style.borderLeftStyle, ignoreCase = true)

        if (uniform) {
            ctx.save()
            ctx.setStrokeColor(style.borderTopColor)
            ctx.setLineWidth(bt)
            applyBorderDashStyle(ctx, style.borderTopStyle, bt)
            strokeCornerRoundedRect(ctx, x, y, w, h, radii[0], radii[1], radii[2], radii[3])
            ctx.restore()
            return
        }

        if (bt > 0f) {
            ctx.save()
            ctx.setStrokeColor(style.borderTopColor)
            ctx.setLineWidth(bt)
            applyBorderDashStyle(ctx, style.borderTopStyle, bt)
            ctx.beginPath()
            ctx.moveTo(x, y + bt * 0.5f)
            ctx.lineTo(x + w, y + bt * 0.5f)
            ctx.stroke()
            ctx.restore()
        }
        if (br > 0f) {
            ctx.save()
            ctx.setStrokeColor(style.borderRightColor)
            ctx.setLineWidth(br)
            applyBorderDashStyle(ctx, style.borderRightStyle, br)
            ctx.beginPath()
            ctx.moveTo(x + w - br * 0.5f, y)
            ctx.lineTo(x + w - br * 0.5f, y + h)
            ctx.stroke()
            ctx.restore()
        }
        if (bb > 0f) {
            ctx.save()
            ctx.setStrokeColor(style.borderBottomColor)
            ctx.setLineWidth(bb)
            applyBorderDashStyle(ctx, style.borderBottomStyle, bb)
            ctx.beginPath()
            ctx.moveTo(x, y + h - bb * 0.5f)
            ctx.lineTo(x + w, y + h - bb * 0.5f)
            ctx.stroke()
            ctx.restore()
        }
        if (bl > 0f) {
            ctx.save()
            ctx.setStrokeColor(style.borderLeftColor)
            ctx.setLineWidth(bl)
            applyBorderDashStyle(ctx, style.borderLeftStyle, bl)
            ctx.beginPath()
            ctx.moveTo(x + bl * 0.5f, y)
            ctx.lineTo(x + bl * 0.5f, y + h)
            ctx.stroke()
            ctx.restore()
        }
    }

    /**
     * Fast helper for widget `customPaint` callbacks and extension widgets (`Svg`, `Canvas2D`,
     * `Canvas3D`, `VideoPlayer`) to blit an [com.nexustools.genericui.image.ImageBuffer] inside [bounds]
     * respecting CSS [ObjectFit] (`CONTAIN`, `COVER`, `FILL`, `NONE`, `SCALE_DOWN`) and `borderRadius`.
     */
    fun paintImageBuffer(
        ctx: ImageBuffer2DContext,
        bounds: Rect,
        buffer: com.nexustools.genericui.image.ImageBuffer,
        objectFit: com.nexustools.genericui.enums.ui.ObjectFit = com.nexustools.genericui.enums.ui.ObjectFit.FILL,
        style: AlienResolvedVisualStyle? = null
    ) {
        if (bounds.width <= 0 || bounds.height <= 0 || buffer.width <= 0 || buffer.height <= 0) return
        val bw = bounds.width.toFloat()
        val bh = bounds.height.toFloat()
        val iw = buffer.width.toFloat()
        val ih = buffer.height.toFloat()
        val radii = if (style != null) resolveCornerRadii(bw, bh, style) else floatArrayOf(0f, 0f, 0f, 0f)
        val hasRoundedClip = radii.any { it > 0.5f }

        val targetCtx: ImageBuffer2DContext
        val drawOriginX: Float
        val drawOriginY: Float
        val scratchBuf: com.nexustools.genericui.ext.imageprocessing.context2d.DefaultCpuImageBuffer?
        if (hasRoundedClip) {
            scratchBuf = com.nexustools.genericui.ext.imageprocessing.context2d.DefaultCpuImageBuffer(bounds.width, bounds.height)
            targetCtx = com.nexustools.genericui.ext.imageprocessing.context2d.DefaultCpuContext2DBackend(scratchBuf)
            drawOriginX = 0f
            drawOriginY = 0f
        } else {
            scratchBuf = null
            targetCtx = ctx
            drawOriginX = bounds.x.toFloat()
            drawOriginY = bounds.y.toFloat()
            ctx.save()
        }

        when (objectFit) {
            com.nexustools.genericui.enums.ui.ObjectFit.FILL ->
                targetCtx.drawImage(buffer, drawOriginX, drawOriginY, bw, bh)
            com.nexustools.genericui.enums.ui.ObjectFit.CONTAIN -> {
                val scale = min(bw / iw, bh / ih)
                val dw = iw * scale
                val dh = ih * scale
                val dx = drawOriginX + (bw - dw) * 0.5f
                val dy = drawOriginY + (bh - dh) * 0.5f
                targetCtx.drawImage(buffer, dx, dy, dw, dh)
            }
            com.nexustools.genericui.enums.ui.ObjectFit.COVER -> {
                val scale = max(bw / iw, bh / ih)
                val dw = iw * scale
                val dh = ih * scale
                val dx = drawOriginX + (bw - dw) * 0.5f
                val dy = drawOriginY + (bh - dh) * 0.5f
                if (!hasRoundedClip) {
                    targetCtx.beginPath()
                    targetCtx.rect(drawOriginX, drawOriginY, bw, bh)
                    targetCtx.clip()
                }
                targetCtx.drawImage(buffer, dx, dy, dw, dh)
            }
            com.nexustools.genericui.enums.ui.ObjectFit.NONE -> {
                val dx = drawOriginX + (bw - iw) * 0.5f
                val dy = drawOriginY + (bh - ih) * 0.5f
                if (!hasRoundedClip) {
                    targetCtx.beginPath()
                    targetCtx.rect(drawOriginX, drawOriginY, bw, bh)
                    targetCtx.clip()
                }
                targetCtx.drawImage(buffer, dx, dy, iw, ih)
            }
            com.nexustools.genericui.enums.ui.ObjectFit.SCALE_DOWN -> {
                val scale = min(1f, min(bw / iw, bh / ih))
                val dw = iw * scale
                val dh = ih * scale
                val dx = drawOriginX + (bw - dw) * 0.5f
                val dy = drawOriginY + (bh - dh) * 0.5f
                targetCtx.drawImage(buffer, dx, dy, dw, dh)
            }
        }

        if (scratchBuf != null) {
            applyRoundedCornerAlphaMask(scratchBuf, bounds.width, bounds.height, radii)
            ctx.drawImage(scratchBuf, bounds.x.toFloat(), bounds.y.toFloat(), bw, bh)
        } else {
            ctx.restore()
        }
    }

    private fun applyRoundedCornerAlphaMask(
        buf: com.nexustools.genericui.ext.imageprocessing.context2d.DefaultCpuImageBuffer,
        w: Int,
        h: Int,
        radii: FloatArray
    ) {
        val pixels = buf.directRgbaBytes() ?: return
        val rTL = radii[0]
        val rTR = radii[1]
        val rBR = radii[2]
        val rBL = radii[3]
        for (py in 0 until h) {
            val cy = py + 0.5f
            val rowOffset = py * w * 4
            for (px in 0 until w) {
                val cx = px + 0.5f
                val outside = when {
                    rTL > 0f && cx < rTL && cy < rTL -> {
                        val dx = rTL - cx; val dy = rTL - cy; dx * dx + dy * dy > rTL * rTL
                    }
                    rTR > 0f && cx > w - rTR && cy < rTR -> {
                        val dx = cx - (w - rTR); val dy = rTR - cy; dx * dx + dy * dy > rTR * rTR
                    }
                    rBR > 0f && cx > w - rBR && cy > h - rBR -> {
                        val dx = cx - (w - rBR); val dy = cy - (h - rBR); dx * dx + dy * dy > rBR * rBR
                    }
                    rBL > 0f && cx < rBL && cy > h - rBL -> {
                        val dx = rBL - cx; val dy = cy - (h - rBL); dx * dx + dy * dy > rBL * rBL
                    }
                    else -> false
                }
                if (outside) {
                    val idx = rowOffset + px * 4
                    pixels[idx] = 0
                    pixels[idx + 1] = 0
                    pixels[idx + 2] = 0
                    pixels[idx + 3] = 0
                }
            }
        }
    }

    fun paintListMarker(
        ctx: ImageBuffer2DContext,
        bounds: Rect,
        style: AlienResolvedVisualStyle,
        itemIndex: Int
    ) {
        val listType = style.listStyleType.lowercase()
        if (listType == "none" || bounds.height <= 0) return
        ctx.save()
        ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
        ctx.setFillColor(style.textColor)
        ctx.setStrokeColor(style.textColor)
        val centerY = bounds.y + style.padding.top + style.fontSizePx * 0.55f
        val markerX = bounds.x - max(10f, style.fontSizePx * 0.85f)
        when (listType) {
            "disc" -> {
                ctx.beginPath()
                ctx.arc(markerX, centerY, max(2f, style.fontSizePx * 0.16f), 0f, 6.2831855f)
                ctx.fill()
            }
            "circle" -> {
                ctx.setLineWidth(1.2f)
                ctx.beginPath()
                ctx.arc(markerX, centerY, max(2f, style.fontSizePx * 0.16f), 0f, 6.2831855f)
                ctx.stroke()
            }
            "square" -> {
                val sz = max(4f, style.fontSizePx * 0.28f)
                ctx.fillRect(markerX - sz * 0.5f, centerY - sz * 0.5f, sz, sz)
            }
            else -> {
                val label = when (listType) {
                    "lower-alpha", "lower-latin" -> "${('a' + ((itemIndex - 1).coerceAtLeast(0) % 26))}."
                    "upper-alpha", "upper-latin" -> "${('A' + ((itemIndex - 1).coerceAtLeast(0) % 26))}."
                    else -> "${itemIndex.coerceAtLeast(1)}."
                }
                ctx.setFont(style.fontFamily, style.fontSizePx, style.fontWeight, style.italic)
                val w = ctx.measureTextWidth(label)
                ctx.fillText(label, bounds.x - w - 6f, bounds.y + style.padding.top + style.fontSizePx * 0.88f)
            }
        }
        ctx.restore()
    }

    fun paintPseudoElementBox(
        ctx: ImageBuffer2DContext,
        hostBounds: Rect,
        pseudoStyle: AlienResolvedVisualStyle,
        isAfter: Boolean
    ) {
        val rawContent = pseudoStyle.content ?: return
        val w = AlienCssStyleResolver.parseLengthPx(
            pseudoStyle.widthCss,
            pseudoStyle.fontSizePx,
            hostBounds.width.toFloat(),
            pseudoStyle.viewportWidthPx,
            pseudoStyle.viewportHeightPx
        )?.roundToInt() ?: if (rawContent.isNotEmpty()) {
            (rawContent.length * pseudoStyle.fontSizePx * 0.6f).roundToInt() + pseudoStyle.padding.left + pseudoStyle.padding.right
        } else 0
        val h = AlienCssStyleResolver.parseLengthPx(
            pseudoStyle.heightCss,
            pseudoStyle.fontSizePx,
            hostBounds.height.toFloat(),
            pseudoStyle.viewportWidthPx,
            pseudoStyle.viewportHeightPx
        )?.roundToInt() ?: if (rawContent.isNotEmpty()) {
            (pseudoStyle.fontSizePx * 1.25f).roundToInt() + pseudoStyle.padding.top + pseudoStyle.padding.bottom
        } else 0
        if (w <= 0 && h <= 0 && rawContent.isEmpty()) return

        val bw = w.coerceAtLeast(1)
        val bh = h.coerceAtLeast(1)
        val bx = if (pseudoStyle.position == "absolute") {
            val l = AlienCssStyleResolver.parseLengthPx(pseudoStyle.leftCss, pseudoStyle.fontSizePx, hostBounds.width.toFloat())
            val r = AlienCssStyleResolver.parseLengthPx(pseudoStyle.rightCss, pseudoStyle.fontSizePx, hostBounds.width.toFloat())
            when {
                l != null -> hostBounds.x + l.roundToInt()
                r != null -> hostBounds.right - r.roundToInt() - bw
                isAfter -> hostBounds.right - bw
                else -> hostBounds.x
            }
        } else {
            if (isAfter) (hostBounds.right - bw - pseudoStyle.margin.right).coerceAtLeast(hostBounds.x)
            else hostBounds.x + pseudoStyle.margin.left
        }
        val by = if (pseudoStyle.position == "absolute") {
            val t = AlienCssStyleResolver.parseLengthPx(pseudoStyle.topCss, pseudoStyle.fontSizePx, hostBounds.height.toFloat())
            val b = AlienCssStyleResolver.parseLengthPx(pseudoStyle.bottomCss, pseudoStyle.fontSizePx, hostBounds.height.toFloat())
            when {
                t != null -> hostBounds.y + t.roundToInt()
                b != null -> hostBounds.bottom - b.roundToInt() - bh
                else -> hostBounds.y + ((hostBounds.height - bh) / 2).coerceAtLeast(0)
            }
        } else {
            hostBounds.y + ((hostBounds.height - bh) / 2).coerceAtLeast(0)
        }
        val pseudoRect = Rect(bx, by, bw, bh)
        paintBoxSurface(ctx, pseudoRect, pseudoStyle)
        if (rawContent.isNotEmpty()) {
            paintText(
                ctx = ctx,
                text = rawContent,
                contentBounds = Rect(
                    bx + pseudoStyle.padding.left,
                    by + pseudoStyle.padding.top,
                    (bw - pseudoStyle.padding.left - pseudoStyle.padding.right).coerceAtLeast(1),
                    (bh - pseudoStyle.padding.top - pseudoStyle.padding.bottom).coerceAtLeast(1)
                ),
                style = pseudoStyle,
                verticalCenter = true
            )
        }
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
        val isSingleLine = maxLines == 1 || style.whiteSpace.equals("nowrap", ignoreCase = true) ||
            style.whiteSpace.equals("pre", ignoreCase = true)
        val indentOffset = if (style.textAlign == "left" || style.textAlign == "start") style.textIndentPx else 0f

        if (isSingleLine && '\n' !in transformed) {
            val displayLine = if (style.textOverflow == "ellipsis") {
                ellipsizeToWidth(ctx, transformed, (maxW - indentOffset).coerceAtLeast(1f))
            } else {
                transformed
            }
            val measuredW = ctx.measureTextWidth(displayLine)
            val drawX = when (style.textAlign) {
                "center" -> contentBounds.x + (contentBounds.width - measuredW) * 0.5f
                "right", "end" -> contentBounds.right - measuredW
                else -> contentBounds.x.toFloat() + indentOffset
            }
            val drawY = if (verticalCenter) {
                contentBounds.y + (contentBounds.height + style.fontSizePx * 0.68f) * 0.5f
            } else {
                contentBounds.y + style.fontSizePx
            }
            ctx.fillText(displayLine, drawX, drawY)
            paintTextDecorationLine(ctx, drawX, drawY, measuredW, style.fontSizePx, style.textDecoration)
        } else {
            val preserveWhitespace = style.whiteSpace.equals("pre", ignoreCase = true) ||
                style.whiteSpace.equals("pre-wrap", ignoreCase = true)
            val wrapWidth = if (style.whiteSpace.equals("pre", ignoreCase = true)) Float.MAX_VALUE else maxW
            val lines = wrapTextLines(ctx, transformed, wrapWidth, maxLines, preserveWhitespace)
            val lineHeight = style.lineHeightPx?.coerceAtLeast(style.fontSizePx)
                ?: (style.fontSizePx * 1.35f).coerceAtLeast(style.fontSizePx + 2f)
            val totalBlockH = lines.size * lineHeight
            var cursorY = if (verticalCenter && totalBlockH < contentBounds.height) {
                contentBounds.y + (contentBounds.height - totalBlockH) * 0.5f + style.fontSizePx * 0.85f
            } else {
                contentBounds.y + style.fontSizePx * 0.9f
            }
            for ((lineIdx, line) in lines.withIndex()) {
                val measuredW = ctx.measureTextWidth(line)
                val firstIndent = if (lineIdx == 0) indentOffset else 0f
                val drawX = when (style.textAlign) {
                    "center" -> contentBounds.x + (contentBounds.width - measuredW) * 0.5f
                    "right", "end" -> contentBounds.right - measuredW
                    else -> contentBounds.x.toFloat() + firstIndent
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

    fun fillCornerRoundedRect(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        tl: Float,
        tr: Float,
        br: Float,
        bl: Float
    ) {
        if (tl == tr && tr == br && br == bl) {
            fillRoundedRect(ctx, x, y, w, h, tl)
        } else {
            ctx.beginPath()
            ctx.roundRect(x, y, w, h, tl, tr, br, bl)
            ctx.fill()
        }
    }

    fun strokeCornerRoundedRect(
        ctx: ImageBuffer2DContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        tl: Float,
        tr: Float,
        br: Float,
        bl: Float
    ) {
        if (tl == tr && tr == br && br == bl) {
            strokeRoundedRect(ctx, x, y, w, h, tl)
        } else {
            ctx.beginPath()
            ctx.roundRect(x, y, w, h, tl, tr, br, bl)
            ctx.stroke()
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
        radii: FloatArray,
        shadowList: CssShadowList,
        fontSizePx: Float,
        insetOnly: Boolean
    ) {
        val r = radii[0]
        if (insetOnly) {
            ctx.save()
            beginCornerRoundedPath(ctx, x, y, w, h, radii)
            ctx.clip()
            for (shadow in shadowList.shadows) {
                if (!shadow.inset) continue
                val ox = shadow.offsetX.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
                val oy = shadow.offsetY.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
                val blur = (shadow.blurRadius.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f).coerceAtLeast(0f)
                val spread = shadow.spreadRadius.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
                val thickness = max(1f, spread + blur * 0.5f + kotlin.math.abs(ox) * 0.5f + kotlin.math.abs(oy) * 0.5f)
                ctx.setStrokeColor(shadow.color)
                ctx.setLineWidth(thickness)
                strokeCornerRoundedRect(
                    ctx,
                    x + ox * 0.5f,
                    y + oy * 0.5f,
                    w,
                    h,
                    radii[0],
                    radii[1],
                    radii[2],
                    radii[3]
                )
            }
            ctx.restore()
            return
        }
        for (shadow in shadowList.shadows) {
            if (shadow.inset) continue
            val ox = shadow.offsetX.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
            val oy = shadow.offsetY.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
            val blur = (shadow.blurRadius.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f).coerceAtLeast(0f)
            val spread = shadow.spreadRadius.resolvePx(fontSizePx, 16f, fontSizePx) ?: 0f
            val expand = spread + blur * 0.35f
            ctx.setFillColor(shadow.color)
            fillCornerRoundedRect(
                ctx,
                x + ox - expand,
                y + oy - expand,
                (w + expand * 2f).coerceAtLeast(0f),
                (h + expand * 2f).coerceAtLeast(0f),
                (radii[0] + expand).coerceAtLeast(0f),
                (radii[1] + expand).coerceAtLeast(0f),
                (radii[2] + expand).coerceAtLeast(0f),
                (radii[3] + expand).coerceAtLeast(0f)
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
        maxLines: Int?,
        preserveWhitespace: Boolean = false
    ): List<String> {
        val paragraphs = text.split('\n')
        val out = mutableListOf<String>()
        for (para in paragraphs) {
            if (preserveWhitespace) {
                if (para.isEmpty() || maxWidthPx >= Float.MAX_VALUE * 0.5f || ctx.measureTextWidth(para) <= maxWidthPx) {
                    out.add(para)
                    continue
                }
            }
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
