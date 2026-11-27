package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.component.tabs.TabIconPlacement
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.MonocolorImageRecolorProcessor
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import kotlinx.coroutines.Job

/**
 * Self-rendered Bootstrap 5.3 `.nav-link` tab header (`TabAlienImpl`) implementing [TabNativeImpl].
 */
class TabAlienImpl(
    initialClosable: Boolean = false,
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "nav-link",
    tagName = "button",
    session = session
), TabNativeImpl<Any?> {

    var tabId: String = ""
        private set
    var titleText: String = ""
        private set
    var iconText: String? = null
        private set
    var iconSizePx: Int = 20
        private set
    var iconPlacement: TabIconPlacement = TabIconPlacement.START
        private set
    var tintImageToTheme: Boolean = true
        private set
    var decodedIconBuffer: ImageBuffer? = null
        private set
    var closableState: Boolean = initialClosable
        private set
    var dirtyState: Boolean = false
        private set
    var pinnedState: Boolean = false
        private set
    var editableState: Boolean = false
        private set
    var badgeText: String? = null
        private set
    var badgeVariantCss: String? = null
        private set
    var selectedState: Boolean = false
        private set
    var customHeaderImpl: NativeImpl<*>? = null
        private set
    var onCloseClickedListener: (() -> Unit)? = null
        private set
    var onTitleEditedListener: ((String) -> Unit)? = null
        private set

    internal var onHeaderUpdated: (() -> Unit)? = null
    internal var lastCloseBounds: Rect? = null
    private var activeIconLoadJob: Job? = null
    private var iconLoadGen: Int = 0
    private var userPointerDownListener: ((PointerEvent) -> Unit)? = null

    init {
        node.customMeasure = { _, _, style ->
            val label = formatDisplayHeaderBody()
            val textW = (label.length * style.fontSizePx * 0.56f).toInt().coerceAtLeast(32)
            val iconW = if (hasVisualIcon()) (iconSizePx.coerceIn(12, 32) + 6) else 0
            val closeW = if (closableState && !pinnedState) 20 else 0
            Size(width = (textW + iconW + closeW + 20).coerceAtLeast(48), height = 38)
        }
        node.customPaint = { ctx, bounds, style ->
            if (selectedState) {
                ctx.setStrokeColor(AlienBootstrapThemeTokens.PRIMARY)
                ctx.setLineWidth(2f)
                ctx.beginPath()
                ctx.moveTo(bounds.x.toFloat(), bounds.bottom.toFloat() - 1f)
                ctx.lineTo(bounds.right.toFloat(), bounds.bottom.toFloat() - 1f)
                ctx.stroke()
            }
            val textColor = if (selectedState) AlienBootstrapThemeTokens.PRIMARY else style.textColor
            paintTabHeaderContent(
                ctx = ctx,
                bounds = bounds,
                textColor = textColor,
                style = style,
                isSelected = selectedState
            )
        }
        wirePointerDown()
    }

    private fun wirePointerDown() {
        node.onPointerDown = { ev ->
            val closeBox = lastCloseBounds
            if (closableState && !pinnedState && closeBox != null &&
                ev.x.toInt() in closeBox.x until closeBox.right &&
                ev.y.toInt() in closeBox.y until closeBox.bottom
            ) {
                onCloseClickedListener?.invoke()
            } else {
                userPointerDownListener?.invoke(ev)
            }
        }
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        userPointerDownListener = listener
        wirePointerDown()
    }

    internal fun paintTabHeaderContent(
        ctx: com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext,
        bounds: Rect,
        textColor: RgbaColor,
        style: com.nexustools.genericui.ext.alien.AlienResolvedVisualStyle,
        isSelected: Boolean
    ): Rect? {
        val showClose = closableState && !pinnedState
        val closeW = if (showClose) 18 else 0
        val closeRect = if (showClose && bounds.width > 24) {
            Rect(
                bounds.right - 20,
                bounds.y + ((bounds.height - 16) / 2).coerceAtLeast(0),
                16,
                16
            )
        } else null
        lastCloseBounds = closeRect

        val iconBuf = preparedIconBuffer(textColor, style.darkTheme)
        val iconSz = if (iconBuf != null) iconSizePx.coerceIn(12, 24) else 0
        val iconReserve = if (iconBuf != null) iconSz + 6 else 0
        val iconOnTop = iconPlacement == TabIconPlacement.TOP

        val contentX = bounds.x + 8
        val contentW = (bounds.width - 16 - closeW).coerceAtLeast(0)
        if (iconBuf != null) {
            val iconX = if (iconOnTop) {
                contentX + ((contentW - iconSz) / 2).coerceAtLeast(0)
            } else {
                contentX
            }
            val iconY = if (iconOnTop) {
                bounds.y + 3
            } else {
                bounds.y + ((bounds.height - iconSz) / 2).coerceAtLeast(0)
            }
            ctx.drawImage(iconBuf, iconX.toFloat(), iconY.toFloat(), iconSz.toFloat(), iconSz.toFloat())
        }

        val textX = if (iconBuf != null && !iconOnTop) contentX + iconReserve else contentX
        val textW = if (iconBuf != null && !iconOnTop) (contentW - iconReserve).coerceAtLeast(0) else contentW
        val textY = if (iconBuf != null && iconOnTop) bounds.y + iconSz + 2 else bounds.y
        val textH = if (iconBuf != null && iconOnTop) (bounds.height - iconSz - 2).coerceAtLeast(12) else bounds.height
        val label = formatDisplayHeaderBody()
        if (textW > 0 && label.isNotEmpty()) {
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = label,
                contentBounds = Rect(textX, textY, textW, textH),
                style = style.copy(
                    textColor = textColor,
                    fontWeight = if (isSelected) 600 else 400,
                    textAlign = if (iconBuf != null && !iconOnTop) "left" else "center"
                ),
                verticalCenter = true,
                maxLines = 1
            )
        }

        if (closeRect != null) {
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = "×",
                contentBounds = closeRect,
                style = style.copy(
                    textColor = textColor,
                    fontWeight = 600,
                    textAlign = "center"
                ),
                verticalCenter = true,
                maxLines = 1
            )
        }
        return closeRect
    }

    internal fun preparedIconBuffer(tintColor: RgbaColor, isDark: Boolean): ImageBuffer? {
        val raw = decodedIconBuffer ?: return null
        if (raw.width <= 0 || raw.height <= 0) return null
        val target = iconSizePx.coerceIn(12, 32)
        val scaled = if (raw.width != target || raw.height != target) {
            ImageLoader.resize(raw, target, target, antialiasing = true)
        } else {
            raw
        }
        return if (tintImageToTheme) {
            MonocolorImageRecolorProcessor.processImageBuffer(scaled, ImageTintMode.SRC_IN, tintColor, isDark)
        } else {
            scaled
        }
    }

    internal fun hasVisualIcon(): Boolean =
        decodedIconBuffer != null || (!iconText.isNullOrBlank() && looksLikeImageOrVectorSpec(iconText!!))

    private fun looksLikeImageOrVectorSpec(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (t.contains("://") || t.contains('/') || t.contains('\\')) return true
        if (t.startsWith("<svg", ignoreCase = true) || t.startsWith("data:", ignoreCase = true)) return true
        val lower = t.substringBefore('?').substringBefore('#').lowercase()
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") ||
            lower.endsWith(".ico") || lower.endsWith(".svg")
        ) return true
        return Resources.getDrawable(t) != null
    }

    private fun hasVisiblePixels(buf: ImageBuffer): Boolean {
        if (buf.width <= 0 || buf.height <= 0) return false
        val rgba = buf.toRgba8888().bytes
        var i = 3
        while (i < rgba.size) {
            if ((rgba[i].toInt() and 0xFF) > 0) return true
            i += 4
        }
        return false
    }

    private fun refreshIconBuffer() {
        activeIconLoadJob?.cancel()
        activeIconLoadJob = null
        val gen = ++iconLoadGen
        val raw = iconText?.trim().orEmpty()
        if (raw.isEmpty() || !looksLikeImageOrVectorSpec(raw)) {
            decodedIconBuffer = null
            return
        }
        if (raw.startsWith("memory://", ignoreCase = true)) {
            ImageLoader.resolveMemoryBuffer(raw)?.let { buf ->
                decodedIconBuffer = buf
                return
            }
        }
        val size = iconSizePx.coerceIn(12, 32)
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
            val cachedSync = runCatching { ImageCacheManager.loadCachedOrFetch(raw) }.getOrNull()
            if (cachedSync != null && hasVisiblePixels(cachedSync)) {
                decodedIconBuffer = cachedSync
                return
            }
        }
        decodedIconBuffer = null
        activeIconLoadJob = ImageLoader.launchLoad(
            source = ImageSource.fromUrl(raw),
            options = ImageDecodeOptions(targetWidth = size, targetHeight = size, antialiasing = true),
            onLoaded = { buf ->
                if (gen != iconLoadGen) return@launchLoad
                if (hasVisiblePixels(buf)) {
                    decodedIconBuffer = buf
                    node.markLayoutDirty()
                    node.invalidateBounds()
                    onHeaderUpdated?.invoke()
                }
            },
            onError = {}
        )
    }

    internal fun formatDisplayHeaderBody(): String {
        val sb = StringBuilder()
        if (pinnedState) sb.append("📌 ")
        val icon = iconText
        if (!icon.isNullOrBlank() && !looksLikeImageOrVectorSpec(icon)) {
            sb.append(icon).append(' ')
        }
        sb.append(titleText)
        if (dirtyState) sb.append(" •")
        if (!badgeText.isNullOrBlank()) sb.append(" [").append(badgeText).append(']')
        return sb.toString()
    }

    fun formatDisplayHeader(): String {
        val sb = StringBuilder()
        if (pinnedState) sb.append("📌 ")
        if (!iconText.isNullOrBlank()) sb.append(iconText).append(' ')
        sb.append(titleText)
        if (dirtyState) sb.append(" •")
        if (!badgeText.isNullOrBlank()) sb.append(" [").append(badgeText).append(']')
        if (closableState && !pinnedState) sb.append(" ×")
        return sb.toString()
    }

    override fun setTabIdNative(id: String) {
        tabId = id
    }

    override fun setTitleNative(title: String) {
        titleText = title
        node.markLayoutDirty()
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setIconNative(icon: String?) {
        iconText = icon
        refreshIconBuffer()
        node.markLayoutDirty()
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setIconSizePxNative(sizePx: Int) {
        iconSizePx = sizePx.coerceAtLeast(1)
        node.markLayoutDirty()
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setIconPlacementNative(placement: TabIconPlacement) {
        iconPlacement = placement
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setTintImageToThemeNative(tintToTheme: Boolean) {
        tintImageToTheme = tintToTheme
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setTooltipNative(tooltip: String?) {
        setTooltipText(tooltip)
    }

    override fun setClosableNative(closable: Boolean) {
        closableState = closable
        node.markLayoutDirty()
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setDirtyNative(dirty: Boolean) {
        dirtyState = dirty
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setPinnedNative(pinned: Boolean) {
        pinnedState = pinned
        node.markLayoutDirty()
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setEditableNative(editable: Boolean) {
        editableState = editable
    }

    override fun setBadgeNative(badgeText: String?, variantCss: String?) {
        this.badgeText = badgeText
        this.badgeVariantCss = variantCss
        node.markLayoutDirty()
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setSelectedNative(selected: Boolean) {
        selectedState = selected
        node.invalidateBounds()
    }

    override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {
        customHeaderImpl = customImpl
        if (customImpl != null) {
            addChildNative(customImpl, -1)
        }
        node.markLayoutDirty()
        node.invalidateBounds()
        onHeaderUpdated?.invoke()
    }

    override fun setOnCloseClicked(listener: (() -> Unit)?) {
        onCloseClickedListener = listener
    }

    override fun setOnTitleEdited(listener: ((String) -> Unit)?) {
        onTitleEditedListener = listener
    }

    override fun dispose() {
        activeIconLoadJob?.cancel()
        activeIconLoadJob = null
        decodedIconBuffer = null
        super.dispose()
    }
}
