package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Self-rendered Bootstrap 5.3 image component (`ImageAlienImpl`) implementing [ImageNativeImpl].
 *
 * Automatically resolves and caches URLs, `data:` URIs, compiled `Resources.getDrawable(...)` assets,
 * and local resource files (`"logo.png?2"`, `"src/commonMain/resources/..."`) via [ImageCacheManager],
 * measures using the decoded [ImageBuffer] dimensions and CSS `width` / `max-width` / `aspect-ratio`,
 * and renders the image onto the `ImageBuffer2DContext` surface.
 */
class ImageAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "img-fluid",
    tagName = "img",
    session = session
), ImageNativeImpl<Any?> {

    private var sourceUrlValue: String? = null
    private var decodedBuffer: ImageBuffer? = null
    private var fallbackTextValue: String? = null
    private var objectFitValue: ObjectFit = ObjectFit.CONTAIN
    private var onLoadListener: (() -> Unit)? = null
    private var onErrorListener: (() -> Unit)? = null

    init {
        node.customMeasure = { availW, _, style ->
            val explicitPref = node.preferredSize
            if (explicitPref != null && explicitPref.width > 0 && explicitPref.height > 0) {
                explicitPref
            } else {
                val buf = decodedBuffer ?: sourceUrlValue?.let { tryLoadImageBuffer(it) }
                val intrinsicW = buf?.width?.takeIf { it > 1 } ?: 320
                val intrinsicH = buf?.height?.takeIf { it > 1 } ?: 120
                val ratio = style.aspectRatio
                    ?: if (intrinsicW > 0 && intrinsicH > 0) intrinsicW.toFloat() / intrinsicH.toFloat() else (928f / 270f)

                val targetW = when {
                    availW > 0 && (style.widthCss == "100%" || "img-fluid" in node.styleSnapshot.customClasses || node.componentClassPrefix == "img-fluid") ->
                        if (buf != null && style.widthCss == null) min(availW, intrinsicW) else availW
                    else -> intrinsicW
                }.coerceAtLeast(1)
                val targetH = if (ratio > 0f) (targetW / ratio).roundToInt().coerceAtLeast(1) else intrinsicH
                Size(targetW, targetH)
            }
        }
        node.customPaint = { ctx, bounds, style ->
            val buf = decodedBuffer ?: sourceUrlValue?.let { tryLoadImageBuffer(it) }
            if (buf != null && buf.width > 0 && buf.height > 0 && bounds.width > 0 && bounds.height > 0) {
                ctx.save()
                ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
                val bw = bounds.width.toFloat()
                val bh = bounds.height.toFloat()
                val iw = buf.width.toFloat()
                val ih = buf.height.toFloat()
                val (drawX, drawY, drawW, drawH) = when (objectFitValue) {
                    ObjectFit.FILL -> floatArrayOf(bounds.x.toFloat(), bounds.y.toFloat(), bw, bh)
                    ObjectFit.NONE -> floatArrayOf(
                        bounds.x + (bw - iw) * 0.5f,
                        bounds.y + (bh - ih) * 0.5f,
                        iw,
                        ih
                    )
                    ObjectFit.COVER -> {
                        val scale = max(bw / iw, bh / ih)
                        val sw = iw * scale
                        val sh = ih * scale
                        floatArrayOf(bounds.x + (bw - sw) * 0.5f, bounds.y + (bh - sh) * 0.5f, sw, sh)
                    }
                    ObjectFit.SCALE_DOWN -> {
                        val scale = min(1f, min(bw / iw, bh / ih))
                        val sw = iw * scale
                        val sh = ih * scale
                        floatArrayOf(bounds.x + (bw - sw) * 0.5f, bounds.y + (bh - sh) * 0.5f, sw, sh)
                    }
                    ObjectFit.CONTAIN -> {
                        val scale = min(bw / iw, bh / ih)
                        val sw = iw * scale
                        val sh = ih * scale
                        floatArrayOf(bounds.x + (bw - sw) * 0.5f, bounds.y + (bh - sh) * 0.5f, sw, sh)
                    }
                }
                ctx.drawImage(buf, drawX, drawY, drawW, drawH)
                ctx.restore()
            } else {
                val label = fallbackTextValue ?: sourceUrlValue?.substringAfterLast('/')?.substringBefore('?') ?: "Image"
                if (label.isNotEmpty()) {
                    AlienBootstrapPainter.paintText(
                        ctx = ctx,
                        text = label,
                        contentBounds = bounds,
                        style = style.copy(textAlign = "center"),
                        verticalCenter = true,
                        maxLines = 1
                    )
                }
            }
        }
    }

    private fun tryLoadImageBuffer(url: String): ImageBuffer? {
        if (url.isBlank()) return null
        val loaded = runCatching {
            ImageCacheManager.loadCachedOrFetch(url)
        }.getOrNull()
        if (loaded != null && loaded.width > 1 && loaded.height > 1) {
            decodedBuffer = loaded
            return loaded
        }
        return null
    }

    override fun setSourceUrl(url: String?) {
        if (sourceUrlValue != url) {
            sourceUrlValue = url
            decodedBuffer = null
            if (!url.isNullOrBlank()) {
                val buf = tryLoadImageBuffer(url)
                if (buf != null) {
                    onLoadListener?.invoke()
                } else {
                    onErrorListener?.invoke()
                }
            }
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getSourceUrl(): String? = sourceUrlValue

    override fun setFallbackText(fallback: String?) {
        fallbackTextValue = fallback
        node.invalidateBounds()
    }

    override fun setObjectFit(fit: ObjectFit) {
        objectFitValue = fit
        node.invalidateBounds()
    }

    override fun getObjectFit(): ObjectFit = objectFitValue

    override fun setOnLoad(listener: (() -> Unit)?) {
        onLoadListener = listener
    }

    override fun setOnError(listener: (() -> Unit)?) {
        onErrorListener = listener
    }
}
