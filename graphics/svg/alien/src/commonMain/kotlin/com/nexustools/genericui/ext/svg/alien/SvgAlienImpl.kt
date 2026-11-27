package com.nexustools.genericui.ext.svg.alien

import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienResolvedVisualStyle
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.svg.Svg
import com.nexustools.genericui.ext.svg.SvgElement
import com.nexustools.genericui.ext.svg.SvgNativeImpl
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import kotlin.math.roundToInt

/**
 * Self-rendered Alien implementation of [SvgNativeImpl] (`SvgAlienImpl`).
 *
 * Rasterizes the mutable [Svg] vector scene graph into an RGBA8888 [ImageBuffer] cached by
 * `(sceneVersion, rasterWidth, rasterHeight)`, paints directly onto the enclosing [AlienRenderSession]'s
 * `ImageBuffer2DContext`, honors W3C `<svg>` intrinsic sizing (`svgWidth`, `svgHeight`, `viewBox`,
 * default `300x150`), and routes inverse-transform pointer hover/click events to [SvgElement]s.
 */
class SvgAlienImpl(
    val svgComponent: Svg,
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "svg",
    tagName = "svg",
    session = session
), SvgNativeImpl<Any?> {

    private var activeSvg: Svg = svgComponent
    private var disposedFlag: Boolean = false
    override val isDisposed: Boolean
        get() = disposedFlag

    var renderedBuffer: ImageBuffer? = null
        private set

    var renderedWidthPx: Int = 0
        private set

    var renderedHeightPx: Int = 0
        private set

    var renderedSceneVersion: Long = -1L
        private set

    private var sceneDirty: Boolean = true

    private var lastPointerX: Float = 0f
    private var lastPointerY: Float = 0f
    private var hasLastPointer: Boolean = false
    private var lastDownElement: SvgElement? = null
    private var hoveredElement: SvgElement? = null

    private var componentOnClick: (() -> Unit)? = null
    private var componentOnPointerDown: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerMove: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerUp: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerEnter: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerLeave: ((PointerEvent) -> Unit)? = null

    init {
        node.styleSnapshot = node.styleSnapshot.copy(display = DisplayMode.INLINE_BLOCK)
        syncIntrinsicSizeToNode()

        node.customMeasure = { availW, availH, style ->
            measureSvgContentSize(availW, availH, style)
        }

        node.customPaint = { ctx, bounds, style ->
            val pad = style.padding
            val drawX = bounds.x + pad.left
            val drawY = bounds.y + pad.top
            val drawW = (bounds.width - pad.left - pad.right).coerceAtLeast(0)
            val drawH = (bounds.height - pad.top - pad.bottom).coerceAtLeast(0)
            if (drawW > 0 && drawH > 0) {
                val buf = ensureRasterized(drawW, drawH)
                if (buf != null && buf.width > 0 && buf.height > 0) {
                    ctx.save()
                    ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
                    ctx.imageSmoothingEnabled = true
                    ctx.drawImage(
                        image = buf,
                        sx = 0f,
                        sy = 0f,
                        sWidth = buf.width.toFloat(),
                        sHeight = buf.height.toFloat(),
                        dx = drawX.toFloat(),
                        dy = drawY.toFloat(),
                        dWidth = drawW.toFloat(),
                        dHeight = drawH.toFloat()
                    )
                    ctx.restore()
                }
            }
        }

        node.onPointerMove = { event ->
            val localEv = toLocalPointerEvent(event)
            handleLocalPointerMove(localEv)
            componentOnPointerMove?.invoke(localEv)
        }

        node.onPointerDown = { event ->
            val localEv = toLocalPointerEvent(event)
            lastPointerX = localEv.x
            lastPointerY = localEv.y
            hasLastPointer = true
            lastDownElement = hitTestViewport(localEv.x, localEv.y)
            updateHoverTarget(lastDownElement, localEv)
            componentOnPointerDown?.invoke(localEv)
        }

        node.onPointerUp = { event ->
            val localEv = toLocalPointerEvent(event)
            lastPointerX = localEv.x
            lastPointerY = localEv.y
            hasLastPointer = true
            updateHoverTarget(hitTestViewport(localEv.x, localEv.y), localEv)
            componentOnPointerUp?.invoke(localEv)
        }

        node.onClick = {
            val hit = if (hasLastPointer) {
                hitTestViewport(lastPointerX, lastPointerY) ?: lastDownElement
            } else {
                lastDownElement
            }
            lastDownElement = null
            dispatchElementClick(hit)
            componentOnClick?.invoke()
        }
    }

    override fun syncSvgScene(svg: Svg) {
        if (disposedFlag) return
        activeSvg = svg
        syncIntrinsicSizeToNode()
        val target = resolveTargetPixelSize()
        if (sceneDirty ||
            renderedBuffer == null ||
            renderedSceneVersion != svg.sceneVersion ||
            renderedWidthPx != target.width ||
            renderedHeightPx != target.height
        ) {
            ensureRasterized(target.width, target.height)
            node.markMeasureDirty()
            node.invalidateBounds()
        }
    }

    override fun invalidateSvgScene() {
        sceneDirty = true
        syncIntrinsicSizeToNode()
        if (!disposedFlag) {
            val target = resolveTargetPixelSize()
            ensureRasterized(target.width, target.height)
            node.markMeasureDirty()
            node.invalidateBounds()
        }
    }

    override fun hitTestViewport(viewportX: Float, viewportY: Float): SvgElement? {
        val size = resolveTargetPixelSize()
        return activeSvg.hitTest(
            viewportX = viewportX,
            viewportY = viewportY,
            viewportWidth = size.width.toFloat(),
            viewportHeight = size.height.toFloat()
        )
    }

    fun renderNow(): ImageBuffer? {
        if (disposedFlag) return renderedBuffer
        val target = resolveTargetPixelSize()
        return ensureRasterized(target.width, target.height)
    }

    override fun setDisplay(display: DisplayMode?) {
        super.setDisplay(display ?: DisplayMode.INLINE_BLOCK)
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<AlienImpl>.applyComponentBootstrap(bootstrap)
        if (bootstrap.display == null) {
            node.styleSnapshot = node.styleSnapshot.copy(display = DisplayMode.INLINE_BLOCK)
        }
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (!disposedFlag && size != null && size.width > 0 && size.height > 0) {
            val target = resolveTargetPixelSize()
            if (target.width != renderedWidthPx || target.height != renderedHeightPx || sceneDirty) {
                ensureRasterized(target.width, target.height)
            }
            node.invalidateBounds()
        }
    }

    override fun setHoveredState(hovered: Boolean) {
        val wasHovered = isHoveredState()
        super<AlienImpl>.setHoveredState(hovered)
        if (wasHovered && !hovered) {
            val ev = PointerEvent(x = lastPointerX, y = lastPointerY)
            hasLastPointer = false
            updateHoverTarget(null, ev)
            componentOnPointerLeave?.invoke(ev)
        } else if (!wasHovered && hovered) {
            componentOnPointerEnter?.invoke(PointerEvent(x = lastPointerX, y = lastPointerY))
        }
    }

    override fun setOnClick(listener: (() -> Unit)?) {
        componentOnClick = listener
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerDown = listener
    }

    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerMove = listener
    }

    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerUp = listener
    }

    override fun setOnPointerEnter(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerEnter = listener
    }

    override fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerLeave = listener
    }

    private fun ensureRasterized(targetWidthPx: Int, targetHeightPx: Int): ImageBuffer? {
        if (disposedFlag) return renderedBuffer
        val safeW = targetWidthPx.coerceIn(1, MAX_RASTER_DIM_PX)
        val safeH = targetHeightPx.coerceIn(1, MAX_RASTER_DIM_PX)
        val existing = renderedBuffer
        if (!sceneDirty &&
            existing != null &&
            renderedSceneVersion == activeSvg.sceneVersion &&
            renderedWidthPx == safeW &&
            renderedHeightPx == safeH
        ) {
            return existing
        }
        val rendered = activeSvg.renderToImageBuffer(safeW, safeH)
        renderedBuffer = rendered
        renderedWidthPx = safeW
        renderedHeightPx = safeH
        renderedSceneVersion = activeSvg.sceneVersion
        sceneDirty = false
        return rendered
    }

    private fun resolveNaturalSvgDimensions(): Triple<Float, Float, Float> {
        val vb = activeSvg.viewBox
        val vbRatio = vb?.takeIf { it.width > 0f && it.height > 0f }?.let { it.width / it.height }
        val rawSvgW = activeSvg.svgWidth?.takeIf { it > 0f }
        val rawSvgH = activeSvg.svgHeight?.takeIf { it > 0f }
        val natW = rawSvgW ?: if (rawSvgH != null && vbRatio != null) {
            rawSvgH * vbRatio
        } else {
            vb?.width?.takeIf { it > 0f } ?: DEFAULT_SVG_WIDTH
        }
        val natH = rawSvgH ?: if (rawSvgW != null && vbRatio != null) {
            rawSvgW / vbRatio
        } else {
            vb?.height?.takeIf { it > 0f } ?: DEFAULT_SVG_HEIGHT
        }
        val ratio = vbRatio ?: (natW / natH.coerceAtLeast(1e-4f))
        return Triple(natW, natH, ratio.coerceAtLeast(1e-4f))
    }

    private fun syncIntrinsicSizeToNode() {
        val (natW, natH, _) = resolveNaturalSvgDimensions()
        node.intrinsicWidth = natW
        node.intrinsicHeight = natH
    }

    private fun measureSvgContentSize(
        availW: Int,
        availH: Int,
        style: AlienResolvedVisualStyle
    ): Size {
        val (natW, natH, intrinsicRatio) = resolveNaturalSvgDimensions()
        val ratio = style.aspectRatio?.takeIf { it > 0f } ?: intrinsicRatio
        val pref = node.preferredSize

        val cssW = parseCssLengthPx(style.widthCss, style.fontSizePx, availW)?.takeIf { it > 0 }
            ?: pref?.width?.takeIf { it > 0 }
        val cssH = parseCssLengthPx(style.heightCss, style.fontSizePx, availH)?.takeIf { it > 0 }
            ?: pref?.height?.takeIf { it > 0 }
        val maxW = parseCssLengthPx(style.maxWidthCss, style.fontSizePx, availW)?.takeIf { it > 0 }
        val maxH = parseCssLengthPx(style.maxHeightCss, style.fontSizePx, availH)?.takeIf { it > 0 }

        var targetW: Int
        var targetH: Int
        when {
            cssW != null && cssH != null -> {
                targetW = cssW
                targetH = cssH
            }
            cssW != null -> {
                targetW = cssW
                targetH = (targetW / ratio).roundToInt().coerceAtLeast(1)
            }
            cssH != null -> {
                targetH = cssH
                targetW = (targetH * ratio).roundToInt().coerceAtLeast(1)
            }
            else -> {
                targetW = natW.roundToInt().coerceAtLeast(1)
                targetH = natH.roundToInt().coerceAtLeast(1)
            }
        }

        if (maxW != null && targetW > maxW) {
            targetW = maxW
            if (cssH == null) {
                targetH = (targetW / ratio).roundToInt().coerceAtLeast(1)
            }
        }
        if (maxH != null && targetH > maxH) {
            targetH = maxH
            if (cssW == null) {
                targetW = (targetH * ratio).roundToInt().coerceAtLeast(1)
            }
        }

        val padW = style.padding.left + style.padding.right
        val padH = style.padding.top + style.padding.bottom
        return Size(
            width = (targetW + padW).coerceAtLeast(1),
            height = (targetH + padH).coerceAtLeast(1)
        )
    }

    private fun resolveTargetPixelSize(): Size {
        val content = node.contentBounds
        if (content.width > 0 && content.height > 0) {
            return Size(
                width = content.width.coerceIn(1, MAX_RASTER_DIM_PX),
                height = content.height.coerceIn(1, MAX_RASTER_DIM_PX)
            )
        }
        val measured = measureSvgContentSize(0, 0, node.resolveVisualStyle())
        return Size(
            width = measured.width.coerceIn(1, MAX_RASTER_DIM_PX),
            height = measured.height.coerceIn(1, MAX_RASTER_DIM_PX)
        )
    }

    private fun parseCssLengthPx(raw: String?, fontSizePx: Float, percentBasePx: Int): Int? {
        if (raw.isNullOrBlank()) return null
        val t = raw.replace("!important", "", ignoreCase = true).trim().lowercase()
        if (t == "auto" || t == "none") return null
        if (t == "0" || t == "0px" || t == "0em" || t == "0rem") return 0
        val value = when {
            t.endsWith("px") -> t.removeSuffix("px").trim().toFloatOrNull()
            t.endsWith("rem") -> t.removeSuffix("rem").trim().toFloatOrNull()?.let { it * 16f }
            t.endsWith("em") -> t.removeSuffix("em").trim().toFloatOrNull()?.let { it * fontSizePx }
            t.endsWith("%") && percentBasePx > 0 ->
                t.removeSuffix("%").trim().toFloatOrNull()?.let { (it / 100f) * percentBasePx }
            else -> t.toFloatOrNull()
        }
        return value?.roundToInt()
    }

    private fun toLocalPointerEvent(event: PointerEvent): PointerEvent {
        val content = node.contentBounds
        val originX = if (content.width > 0 && content.height > 0) content.x else node.bounds.x
        val originY = if (content.width > 0 && content.height > 0) content.y else node.bounds.y
        return event.copy(
            x = event.x - originX,
            y = event.y - originY
        )
    }

    private fun handleLocalPointerMove(event: PointerEvent): SvgElement? {
        lastPointerX = event.x
        lastPointerY = event.y
        hasLastPointer = true
        val hit = hitTestViewport(event.x, event.y)
        updateHoverTarget(hit, event)
        return hit
    }

    private fun updateHoverTarget(newTarget: SvgElement?, event: PointerEvent) {
        val prevTarget = hoveredElement
        if (prevTarget === newTarget) return

        val prevChain = collectAncestorChain(prevTarget)
        val newChain = collectAncestorChain(newTarget)
        val newSet = newChain.toHashSet()
        val prevSet = prevChain.toHashSet()

        for (el in prevChain) {
            if (el !in newSet) {
                el.onPointerLeave?.invoke(event)
            }
        }
        for (i in newChain.indices.reversed()) {
            val el = newChain[i]
            if (el !in prevSet) {
                el.onPointerEnter?.invoke(event)
            }
        }
        hoveredElement = newTarget
    }

    private fun collectAncestorChain(leaf: SvgElement?): List<SvgElement> {
        if (leaf == null) return emptyList()
        val chain = ArrayList<SvgElement>()
        var cur: SvgElement? = leaf
        while (cur != null) {
            chain.add(cur)
            cur = cur.parentElement
        }
        return chain
    }

    private fun dispatchElementClick(target: SvgElement?) {
        var cur: SvgElement? = target
        while (cur != null) {
            cur.onClick?.invoke()
            cur = cur.parentElement
        }
    }

    override fun dispose() {
        if (disposedFlag) return
        disposedFlag = true
        hoveredElement = null
        lastDownElement = null
        renderedBuffer = null
        componentOnClick = null
        componentOnPointerDown = null
        componentOnPointerMove = null
        componentOnPointerUp = null
        componentOnPointerEnter = null
        componentOnPointerLeave = null
        super.dispose()
    }

    private companion object {
        const val DEFAULT_SVG_WIDTH = 300f
        const val DEFAULT_SVG_HEIGHT = 150f
        const val MAX_RASTER_DIM_PX = 8192
    }
}
