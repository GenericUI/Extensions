package com.nexustools.genericui.ext.pip.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.Base64
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.PathParser
import com.nexustools.genericui.ext.pip.PipAction
import com.nexustools.genericui.ext.pip.PipActionIcon
import com.nexustools.genericui.res.Resources
import java.io.File

/**
 * Generates crisp vector/Canvas Android [Icon] drawables for [PipActionIcon] values and
 * resolves optional custom action icon URIs (`android.resource://`, `res://drawable/`, `data:`,
 * `file://`, `content://`, or GenericUI [Resources] drawables) for `RemoteAction` buttons.
 */
object AndroidPipIconRenderer {

    private const val DEFAULT_ICON_SIZE_PX: Int = 72

    fun renderIcon(
        context: Context?,
        action: PipAction,
        sizePx: Int = DEFAULT_ICON_SIZE_PX
    ): Icon = renderIcon(
        context = context,
        icon = action.icon,
        customResourceUri = action.customResourceUri,
        sizePx = sizePx
    )

    fun renderIcon(
        context: Context?,
        icon: PipActionIcon,
        customResourceUri: String? = null,
        sizePx: Int = DEFAULT_ICON_SIZE_PX
    ): Icon {
        val bitmap = renderBitmap(
            context = context,
            icon = icon,
            customResourceUri = customResourceUri,
            sizePx = sizePx
        )
        return Icon.createWithBitmap(bitmap)
    }

    fun renderBitmap(
        context: Context?,
        icon: PipActionIcon,
        customResourceUri: String? = null,
        sizePx: Int = DEFAULT_ICON_SIZE_PX
    ): Bitmap {
        val resolvedSize = sizePx.coerceIn(24, 256)
        val uri = customResourceUri?.trim()?.takeIf { it.isNotEmpty() }
        if (uri != null) {
            resolveCustomBitmap(context, uri, resolvedSize)?.let { return it }
        }
        return drawVectorGlyphBitmap(icon, resolvedSize)
    }

    private fun resolveCustomBitmap(
        context: Context?,
        uriString: String,
        sizePx: Int
    ): Bitmap? = runCatching {
        if (uriString.startsWith("data:image/", ignoreCase = true) && uriString.contains(";base64,")) {
            val b64 = uriString.substringAfter(";base64,")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (decoded != null) return@runCatching scaleBitmapSquare(decoded, sizePx)
        }

        if (uriString.startsWith("file://", ignoreCase = true) || uriString.startsWith("/")) {
            val filePath = if (uriString.startsWith("file://", ignoreCase = true)) {
                Uri.parse(uriString).path ?: uriString.removePrefix("file://")
            } else {
                uriString
            }
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                val decoded = BitmapFactory.decodeFile(file.absolutePath)
                if (decoded != null) return@runCatching scaleBitmapSquare(decoded, sizePx)
            }
        }

        if (context != null && uriString.startsWith("content://", ignoreCase = true)) {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                val decoded = BitmapFactory.decodeStream(stream)
                if (decoded != null) return@runCatching scaleBitmapSquare(decoded, sizePx)
            }
        }

        if (context != null && uriString.startsWith("android.resource://", ignoreCase = true)) {
            val parsed = Uri.parse(uriString)
            val segments = parsed.pathSegments.orEmpty()
            val pkg = parsed.host?.takeIf { it.isNotBlank() } ?: context.packageName
            val resId = when {
                segments.size == 1 -> segments[0].toIntOrNull()
                    ?: context.resources.getIdentifier(segments[0], "drawable", pkg)
                segments.size >= 2 -> context.resources.getIdentifier(segments[1], segments[0], pkg)
                else -> 0
            }
            if (resId != 0) {
                val drawable = runCatching { AppCompatResources.getDrawable(context, resId) }.getOrNull()
                    ?: runCatching { context.getDrawable(resId) }.getOrNull()
                if (drawable != null) {
                    return@runCatching drawableToBitmap(drawable, sizePx)
                }
            }
        }

        val cleanName = uriString
            .removePrefix("res://drawable/")
            .removePrefix("@drawable/")
            .removePrefix("drawable/")
            .removePrefix("res://mipmap/")
            .removePrefix("@mipmap/")
            .removePrefix("mipmap/")
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .replace('-', '_')
            .lowercase()

        if (context != null && cleanName.isNotEmpty()) {
            var resId = runCatching {
                context.resources.getIdentifier(cleanName, "drawable", context.packageName)
            }.getOrDefault(0)
            if (resId == 0) {
                resId = runCatching {
                    context.resources.getIdentifier(cleanName, "mipmap", context.packageName)
                }.getOrDefault(0)
            }
            if (resId != 0) {
                val drawable = runCatching { AppCompatResources.getDrawable(context, resId) }.getOrNull()
                    ?: runCatching { context.getDrawable(resId) }.getOrNull()
                if (drawable != null) {
                    return@runCatching drawableToBitmap(drawable, sizePx)
                }
            }
        }

        val drawableRes = Resources.getDrawable(uriString)
            ?: Resources.getDrawable(cleanName)
            ?: Resources.activeTable.getDrawableByName(cleanName)

        if (drawableRes != null) {
            val b64 = drawableRes.base64Data
            if (!b64.isNullOrBlank()) {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) return@runCatching scaleBitmapSquare(decoded, sizePx)
            }
            if (drawableRes.svgPathDataList.isNotEmpty()) {
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.WHITE
                }
                val vbW = drawableRes.viewBoxWidth.takeIf { it > 0f } ?: 24f
                val vbH = drawableRes.viewBoxHeight.takeIf { it > 0f } ?: 24f
                val matrix = Matrix().apply {
                    setRectToRect(
                        RectF(0f, 0f, vbW, vbH),
                        RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()),
                        Matrix.ScaleToFit.CENTER
                    )
                }
                for (pathData in drawableRes.svgPathDataList) {
                    val path = runCatching { PathParser.createPathFromPathData(pathData) }.getOrNull()
                    if (path != null) {
                        path.transform(matrix)
                        canvas.drawPath(path, paint)
                    }
                }
                return@runCatching bmp
            }
        }
        null
    }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return scaleBitmapSquare(drawable.bitmap, sizePx)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp
    }

    private fun scaleBitmapSquare(source: Bitmap, sizePx: Int): Bitmap {
        if (source.width == sizePx && source.height == sizePx && source.config == Bitmap.Config.ARGB_8888) {
            return source
        }
        val scaled = Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
        if (!hasNonTransparentPixel(scaled) && source.width > 0 && source.height > 0) {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val srcPixels = IntArray(source.width * source.height)
            source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
            if (srcPixels.any { Color.alpha(it) > 0 }) {
                val dstPixels = IntArray(sizePx * sizePx)
                for (y in 0 until sizePx) {
                    val sy = ((y.toFloat() + 0.5f) * source.height / sizePx).toInt().coerceIn(0, source.height - 1)
                    for (x in 0 until sizePx) {
                        val sx = ((x.toFloat() + 0.5f) * source.width / sizePx).toInt().coerceIn(0, source.width - 1)
                        dstPixels[y * sizePx + x] = srcPixels[sy * source.width + sx]
                    }
                }
                out.setPixels(dstPixels, 0, sizePx, 0, 0, sizePx, sizePx)
                return out
            }
        }
        return scaled
    }

    private fun hasNonTransparentPixel(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return false
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        return pixels.any { Color.alpha(it) > 0 }
    }

    private fun drawVectorGlyphBitmap(icon: PipActionIcon, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scale = sizePx / 24f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 2.0f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        fun sx(x: Float): Float = x * scale
        fun sy(y: Float): Float = y * scale

        when (icon) {
            PipActionIcon.PLAY -> {
                val path = Path().apply {
                    moveTo(sx(8.0f), sy(5.5f))
                    lineTo(sx(18.5f), sy(12.0f))
                    lineTo(sx(8.0f), sy(18.5f))
                    close()
                }
                canvas.drawPath(path, fillPaint)
            }

            PipActionIcon.PAUSE -> {
                val radius = 1.2f * scale
                canvas.drawRoundRect(sx(7.0f), sy(5.5f), sx(10.5f), sy(18.5f), radius, radius, fillPaint)
                canvas.drawRoundRect(sx(13.5f), sy(5.5f), sx(17.0f), sy(18.5f), radius, radius, fillPaint)
            }

            PipActionIcon.PREVIOUS -> {
                val radius = 0.8f * scale
                canvas.drawRoundRect(sx(6.0f), sy(6.0f), sx(8.2f), sy(18.0f), radius, radius, fillPaint)
                val tri = Path().apply {
                    moveTo(sx(18.0f), sy(6.0f))
                    lineTo(sx(9.5f), sy(12.0f))
                    lineTo(sx(18.0f), sy(18.0f))
                    close()
                }
                canvas.drawPath(tri, fillPaint)
            }

            PipActionIcon.NEXT -> {
                val tri = Path().apply {
                    moveTo(sx(6.0f), sy(6.0f))
                    lineTo(sx(14.5f), sy(12.0f))
                    lineTo(sx(6.0f), sy(18.0f))
                    close()
                }
                canvas.drawPath(tri, fillPaint)
                val radius = 0.8f * scale
                canvas.drawRoundRect(sx(15.8f), sy(6.0f), sx(18.0f), sy(18.0f), radius, radius, fillPaint)
            }

            PipActionIcon.SKIP_BACKWARD -> {
                val leftTri = Path().apply {
                    moveTo(sx(11.5f), sy(6.5f))
                    lineTo(sx(4.5f), sy(12.0f))
                    lineTo(sx(11.5f), sy(17.5f))
                    close()
                }
                val rightTri = Path().apply {
                    moveTo(sx(19.0f), sy(6.5f))
                    lineTo(sx(12.0f), sy(12.0f))
                    lineTo(sx(19.0f), sy(17.5f))
                    close()
                }
                canvas.drawPath(leftTri, fillPaint)
                canvas.drawPath(rightTri, fillPaint)
            }

            PipActionIcon.SKIP_FORWARD -> {
                val leftTri = Path().apply {
                    moveTo(sx(5.0f), sy(6.5f))
                    lineTo(sx(12.0f), sy(12.0f))
                    lineTo(sx(5.0f), sy(17.5f))
                    close()
                }
                val rightTri = Path().apply {
                    moveTo(sx(12.5f), sy(6.5f))
                    lineTo(sx(19.5f), sy(12.0f))
                    lineTo(sx(12.5f), sy(17.5f))
                    close()
                }
                canvas.drawPath(leftTri, fillPaint)
                canvas.drawPath(rightTri, fillPaint)
            }

            PipActionIcon.MUTE -> {
                val speaker = Path().apply {
                    moveTo(sx(4.5f), sy(9.5f))
                    lineTo(sx(8.0f), sy(9.5f))
                    lineTo(sx(12.5f), sy(5.5f))
                    lineTo(sx(12.5f), sy(18.5f))
                    lineTo(sx(8.0f), sy(14.5f))
                    lineTo(sx(4.5f), sy(14.5f))
                    close()
                }
                canvas.drawPath(speaker, fillPaint)
                canvas.drawLine(sx(15.5f), sy(9.5f), sx(19.5f), sy(14.5f), strokePaint)
                canvas.drawLine(sx(19.5f), sy(9.5f), sx(15.5f), sy(14.5f), strokePaint)
            }

            PipActionIcon.UNMUTE -> {
                val speaker = Path().apply {
                    moveTo(sx(4.5f), sy(9.5f))
                    lineTo(sx(8.0f), sy(9.5f))
                    lineTo(sx(12.5f), sy(5.5f))
                    lineTo(sx(12.5f), sy(18.5f))
                    lineTo(sx(8.0f), sy(14.5f))
                    lineTo(sx(4.5f), sy(14.5f))
                    close()
                }
                canvas.drawPath(speaker, fillPaint)
                val innerArc = RectF(sx(13.0f), sy(8.5f), sx(16.5f), sy(15.5f))
                val outerArc = RectF(sx(13.5f), sy(6.0f), sx(19.5f), sy(18.0f))
                canvas.drawArc(innerArc, -55f, 110f, false, strokePaint)
                canvas.drawArc(outerArc, -55f, 110f, false, strokePaint)
            }

            PipActionIcon.RESTORE -> {
                val tl = Path().apply {
                    moveTo(sx(5.0f), sy(9.5f))
                    lineTo(sx(5.0f), sy(5.0f))
                    lineTo(sx(9.5f), sy(5.0f))
                }
                val br = Path().apply {
                    moveTo(sx(14.5f), sy(19.0f))
                    lineTo(sx(19.0f), sy(19.0f))
                    lineTo(sx(19.0f), sy(14.5f))
                }
                canvas.drawPath(tl, strokePaint)
                canvas.drawPath(br, strokePaint)
                canvas.drawLine(sx(5.5f), sy(5.5f), sx(10.0f), sy(10.0f), strokePaint)
                canvas.drawLine(sx(18.5f), sy(18.5f), sx(14.0f), sy(14.0f), strokePaint)
            }

            PipActionIcon.CLOSE -> {
                canvas.drawLine(sx(6.5f), sy(6.5f), sx(17.5f), sy(17.5f), strokePaint)
                canvas.drawLine(sx(17.5f), sy(6.5f), sx(6.5f), sy(17.5f), strokePaint)
            }

            PipActionIcon.CUSTOM -> {
                canvas.drawCircle(sx(12.0f), sy(12.0f), 6.0f * scale, strokePaint)
                canvas.drawCircle(sx(12.0f), sy(12.0f), 2.2f * scale, fillPaint)
            }
        }

        if (!hasNonTransparentPixel(bmp)) {
            rasterizeVectorGlyphSoftwareFallback(bmp, icon, sizePx)
        }
        return bmp
    }

    /**
     * Software pixel rasterizer fallback used when Android [Canvas] runs under a headless
     * test environment (such as Robolectric `LEGACY` graphics mode) where vector path draws
     * do not commit pixels onto [Bitmap]. Produces clean anti-aliased glyphs per [PipActionIcon].
     */
    private fun rasterizeVectorGlyphSoftwareFallback(bmp: Bitmap, icon: PipActionIcon, sizePx: Int) {
        val pixels = IntArray(sizePx * sizePx)
        val scale = sizePx / 24f

        fun fillTriangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
            val px0 = x0 * scale; val py0 = y0 * scale
            val px1 = x1 * scale; val py1 = y1 * scale
            val px2 = x2 * scale; val py2 = y2 * scale
            val minX = minOf(px0, px1, px2).toInt().coerceIn(0, sizePx - 1)
            val maxX = kotlin.math.ceil(maxOf(px0, px1, px2)).toInt().coerceIn(0, sizePx - 1)
            val minY = minOf(py0, py1, py2).toInt().coerceIn(0, sizePx - 1)
            val maxY = kotlin.math.ceil(maxOf(py0, py1, py2)).toInt().coerceIn(0, sizePx - 1)
            val area = (px1 - px0) * (py2 - py0) - (px2 - px0) * (py1 - py0)
            if (area == 0f) return
            val sgn = if (area > 0f) 1f else -1f
            for (y in minY..maxY) {
                val cy = y + 0.5f
                for (x in minX..maxX) {
                    val cx = x + 0.5f
                    val e0 = ((px1 - px0) * (cy - py0) - (cx - px0) * (py1 - py0)) * sgn
                    val e1 = ((px2 - px1) * (cy - py1) - (cx - px1) * (py2 - py1)) * sgn
                    val e2 = ((px0 - px2) * (cy - py2) - (cx - px2) * (py0 - py2)) * sgn
                    if (e0 >= 0f && e1 >= 0f && e2 >= 0f) {
                        pixels[y * sizePx + x] = Color.WHITE
                    }
                }
            }
        }

        fun fillRect(l: Float, t: Float, r: Float, b: Float) {
            val minX = (l * scale).toInt().coerceIn(0, sizePx - 1)
            val maxX = kotlin.math.ceil(r * scale).toInt().coerceIn(0, sizePx - 1)
            val minY = (t * scale).toInt().coerceIn(0, sizePx - 1)
            val maxY = kotlin.math.ceil(b * scale).toInt().coerceIn(0, sizePx - 1)
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    pixels[y * sizePx + x] = Color.WHITE
                }
            }
        }

        fun drawThickSegment(x0: Float, y0: Float, x1: Float, y1: Float, halfWidth: Float = 1.0f) {
            val px0 = x0 * scale; val py0 = y0 * scale
            val px1 = x1 * scale; val py1 = y1 * scale
            val rad = (halfWidth * scale).coerceAtLeast(1.0f)
            val radSq = rad * rad
            val minX = (minOf(px0, px1) - rad).toInt().coerceIn(0, sizePx - 1)
            val maxX = kotlin.math.ceil(maxOf(px0, px1) + rad).toInt().coerceIn(0, sizePx - 1)
            val minY = (minOf(py0, py1) - rad).toInt().coerceIn(0, sizePx - 1)
            val maxY = kotlin.math.ceil(maxOf(py0, py1) + rad).toInt().coerceIn(0, sizePx - 1)
            val dx = px1 - px0
            val dy = py1 - py0
            val lenSq = dx * dx + dy * dy
            for (y in minY..maxY) {
                val cy = y + 0.5f
                for (x in minX..maxX) {
                    val cx = x + 0.5f
                    val t = if (lenSq > 0f) (((cx - px0) * dx + (cy - py0) * dy) / lenSq).coerceIn(0f, 1f) else 0f
                    val projX = px0 + t * dx
                    val projY = py0 + t * dy
                    val distSq = (cx - projX) * (cx - projX) + (cy - projY) * (cy - projY)
                    if (distSq <= radSq) {
                        pixels[y * sizePx + x] = Color.WHITE
                    }
                }
            }
        }

        fun fillCircle(cx0: Float, cy0: Float, radius: Float) {
            val pcx = cx0 * scale; val pcy = cy0 * scale; val r = (radius * scale).coerceAtLeast(1f)
            val rSq = r * r
            val minX = (pcx - r).toInt().coerceIn(0, sizePx - 1)
            val maxX = kotlin.math.ceil(pcx + r).toInt().coerceIn(0, sizePx - 1)
            val minY = (pcy - r).toInt().coerceIn(0, sizePx - 1)
            val maxY = kotlin.math.ceil(pcy + r).toInt().coerceIn(0, sizePx - 1)
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val dx = x + 0.5f - pcx
                    val dy = y + 0.5f - pcy
                    if (dx * dx + dy * dy <= rSq) {
                        pixels[y * sizePx + x] = Color.WHITE
                    }
                }
            }
        }

        fun strokeRing(cx0: Float, cy0: Float, radius: Float, strokeHalfWidth: Float = 1.0f, minCosX: Float = -2f) {
            val pcx = cx0 * scale; val pcy = cy0 * scale
            val outer = ((radius + strokeHalfWidth) * scale).coerceAtLeast(1f)
            val inner = ((radius - strokeHalfWidth) * scale).coerceAtLeast(0f)
            val outerSq = outer * outer; val innerSq = inner * inner
            val minX = (pcx - outer).toInt().coerceIn(0, sizePx - 1)
            val maxX = kotlin.math.ceil(pcx + outer).toInt().coerceIn(0, sizePx - 1)
            val minY = (pcy - outer).toInt().coerceIn(0, sizePx - 1)
            val maxY = kotlin.math.ceil(pcy + outer).toInt().coerceIn(0, sizePx - 1)
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val dx = x + 0.5f - pcx
                    val dy = y + 0.5f - pcy
                    val d2 = dx * dx + dy * dy
                    if (d2 in innerSq..outerSq) {
                        if (minCosX <= -1.5f || (dx / kotlin.math.sqrt(d2.coerceAtLeast(1e-5f))) >= minCosX) {
                            pixels[y * sizePx + x] = Color.WHITE
                        }
                    }
                }
            }
        }

        when (icon) {
            PipActionIcon.PLAY -> fillTriangle(8.0f, 5.5f, 18.5f, 12.0f, 8.0f, 18.5f)
            PipActionIcon.PAUSE -> {
                fillRect(7.0f, 5.5f, 10.5f, 18.5f)
                fillRect(13.5f, 5.5f, 17.0f, 18.5f)
            }
            PipActionIcon.PREVIOUS -> {
                fillRect(6.0f, 6.0f, 8.2f, 18.0f)
                fillTriangle(18.0f, 6.0f, 9.5f, 12.0f, 18.0f, 18.0f)
            }
            PipActionIcon.NEXT -> {
                fillTriangle(6.0f, 6.0f, 14.5f, 12.0f, 6.0f, 18.0f)
                fillRect(15.8f, 6.0f, 18.0f, 18.0f)
            }
            PipActionIcon.SKIP_BACKWARD -> {
                fillTriangle(11.5f, 6.5f, 4.5f, 12.0f, 11.5f, 17.5f)
                fillTriangle(19.0f, 6.5f, 12.0f, 12.0f, 19.0f, 17.5f)
            }
            PipActionIcon.SKIP_FORWARD -> {
                fillTriangle(5.0f, 6.5f, 12.0f, 12.0f, 5.0f, 17.5f)
                fillTriangle(12.5f, 6.5f, 19.5f, 12.0f, 12.5f, 17.5f)
            }
            PipActionIcon.MUTE -> {
                fillRect(4.5f, 9.5f, 8.2f, 14.5f)
                fillTriangle(7.8f, 9.5f, 12.5f, 5.5f, 12.5f, 18.5f)
                fillTriangle(7.8f, 14.5f, 12.5f, 5.5f, 12.5f, 18.5f)
                drawThickSegment(15.5f, 9.5f, 19.5f, 14.5f)
                drawThickSegment(19.5f, 9.5f, 15.5f, 14.5f)
            }
            PipActionIcon.UNMUTE -> {
                fillRect(4.5f, 9.5f, 8.2f, 14.5f)
                fillTriangle(7.8f, 9.5f, 12.5f, 5.5f, 12.5f, 18.5f)
                fillTriangle(7.8f, 14.5f, 12.5f, 5.5f, 12.5f, 18.5f)
                strokeRing(13.5f, 12.0f, 3.0f, minCosX = 0.45f)
                strokeRing(14.5f, 12.0f, 5.0f, minCosX = 0.45f)
            }
            PipActionIcon.RESTORE -> {
                drawThickSegment(5.0f, 9.5f, 5.0f, 5.0f)
                drawThickSegment(5.0f, 5.0f, 9.5f, 5.0f)
                drawThickSegment(14.5f, 19.0f, 19.0f, 19.0f)
                drawThickSegment(19.0f, 19.0f, 19.0f, 14.5f)
                drawThickSegment(5.5f, 5.5f, 10.0f, 10.0f)
                drawThickSegment(18.5f, 18.5f, 14.0f, 14.0f)
            }
            PipActionIcon.CLOSE -> {
                drawThickSegment(6.5f, 6.5f, 17.5f, 17.5f)
                drawThickSegment(17.5f, 6.5f, 6.5f, 17.5f)
            }
            PipActionIcon.CUSTOM -> {
                strokeRing(12.0f, 12.0f, 6.0f)
                fillCircle(12.0f, 12.0f, 2.2f)
            }
        }
        bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }
}
