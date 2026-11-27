package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.impl.AlienUnderlayExtensionSlotNode
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import kotlin.math.min

/**
 * Active rendering, layout, animation, and underlay-compositing session for a root [AlienContainer]
 * or [AlienFragment] on System targets.
 *
 * Owns:
 * - [hostFactory]: The underlying OS [NativeFactory] (`WindowsNativeFactory`, `KdeNativeFactory`, `GtkNativeFactory`, etc.).
 * - [alienFactory]: The [AlienFactory] provided to all child components via `resolveChildFactory()`.
 * - [offscreenOnly]: When `true`, renders exclusively into the offscreen [ImageBuffer] without
 *   attaching a host Canvas2D native widget or VBlank render hook (used by isolated headless hosts).
 * - [canvas2D]: The double-buffered `:extensions:canvas2d` [Canvas2D] surface backed by `:extensions:imageprocessing`'s `ImageBuffer`.
 * - [rootNode]: Root [AlienWidgetNode] of the Alien scene graph.
 * - [layoutEngine]: [AlienLayoutEngine] for two-pass measurement and arrangement.
 * - [repaintManager]: [AlienRepaintManager] with [AlienUpdateRegionTracker] for partial dirty-region updates.
 * - [eventDispatcher]: [AlienEventDispatcher] for reverse Z-order hit-testing and underlay mouse passthrough.
 * - [underlaySlots]: Registered [AlienUnderlayExtensionSlotNode]s (`UnalienContainer` and extension surfaces)
 *   attached to the host container **below** the [canvas2D].
 */
class AlienRenderSession(
    val hostFactory: NativeFactory,
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    val offscreenOnly: Boolean = false
) {
    var viewportWidth: Int = initialWidth.coerceAtLeast(1)
        private set

    var viewportHeight: Int = initialHeight.coerceAtLeast(1)
        private set

    val canvas2D: Canvas2D = object : Canvas2D(
        width = viewportWidth,
        height = viewportHeight,
        doubleBuffer = true,
        initialAntialias = true
    ) {
        override fun resolveActiveFactory(): NativeFactory? = hostFactory
    }

    val rootNode: AlienWidgetNode = AlienWidgetNode(
        componentClassPrefix = "alien-root",
        tagName = "div",
        session = this
    ).apply {
        bounds = Rect(0, 0, viewportWidth, viewportHeight)
    }

    val layoutEngine: AlienLayoutEngine = AlienLayoutEngine()
    val repaintManager: AlienRepaintManager = AlienRepaintManager()
    val eventDispatcher: AlienEventDispatcher = AlienEventDispatcher(this)
    val alienFactory: AlienFactory = AlienFactory(hostFactory = hostFactory, session = this)

    val styleSheets: MutableList<PrecompiledStyleSheet> = mutableListOf(AlienPrecompiledBootstrapStyleSheet)

    var insertApplicationStyleSheets: Int = 1

    val hasBootstrapStyleSheet: Boolean
        get() = styleSheets.any {
            it === AlienPrecompiledBootstrapStyleSheet ||
                it.cssResourcePath.equals(AlienPrecompiledBootstrapStyleSheet.cssResourcePath, ignoreCase = true) ||
                it.name.equals(AlienPrecompiledBootstrapStyleSheet.name, ignoreCase = true)
        }

    val customStyleSheets: List<PrecompiledStyleSheet>
        get() = styleSheets.filter { it !== AlienPrecompiledBootstrapStyleSheet }

    private val _underlaySlots = mutableListOf<AlienUnderlayExtensionSlotNode>()
    val underlaySlots: List<AlienUnderlayExtensionSlotNode>
        get() = _underlaySlots

    internal var onUnderlaySlotRegistered: ((AlienUnderlayExtensionSlotNode) -> Unit)? = null
    internal var onUnderlaySlotUnregistered: ((AlienUnderlayExtensionSlotNode) -> Unit)? = null

    var isLayoutDirty: Boolean = true
    private var fullTileSyncPending: Boolean = true
    private var nextStylePassSeq: Long = 1L
    internal var activeStylePassId: Long = -1L
        private set

    internal fun beginStylePass(): Long {
        val id = nextStylePassSeq++
        activeStylePassId = id
        return id
    }

    internal fun endStylePass() {
        activeStylePassId = -1L
    }

    /**
     * Current rendered offscreen RGBA [ImageBuffer] backing [canvas2D].
     * Automatically stays synchronized with [viewportWidth] x [viewportHeight].
     */
    val offscreenBuffer: ImageBuffer
        get() {
            val ctx = canvas2D.getContext()
            if (ctx.width != viewportWidth || ctx.height != viewportHeight) {
                ctx.resizeSurface(viewportWidth, viewportHeight)
            }
            return ctx.imageBuffer
        }

    init {
        // Wire Canvas2D input events to the AlienEventDispatcher
        canvas2D.onPointerMove = { event -> eventDispatcher.dispatchPointerMove(event) }
        canvas2D.onPointerDown = { event -> eventDispatcher.dispatchPointerDown(event) }
        canvas2D.onPointerUp = { event -> eventDispatcher.dispatchPointerUp(event) }
        canvas2D.onContextMenu = { event -> eventDispatcher.dispatchContextMenu(event) }
        canvas2D.onWheel = { event -> eventDispatcher.dispatchWheel(event) }
        canvas2D.onKeyDown = { event -> eventDispatcher.dispatchKeyDown(event) }
        canvas2D.onKeyUp = { event -> eventDispatcher.dispatchKeyUp(event) }

        if (!offscreenOnly) {
            // Register VBlank render loop for smooth CSS transitions and dirty region updates
            canvas2D.registerVBlankRender { _, deltaMs, _, _ ->
                repaintManager.stepAnimationsAndRepaint(this, deltaMs.toFloat())
            }

            // Attach the native Canvas2D implementation when registered so all rendering and input
            // listeners bind directly to the native Canvas2D surface.
            try {
                canvas2D.ensureNativeImpl()
            } catch (e: IllegalStateException) {
                val msg = e.message ?: ""
                if ("No Canvas2DNativeImpl provider registered" !in msg &&
                    "No active NativeFactory" !in msg
                ) {
                    throw e
                }
            }
        }
    }

    fun resizeViewport(newWidth: Int, newHeight: Int) {
        val safeW = newWidth.coerceAtLeast(1)
        val safeH = newHeight.coerceAtLeast(1)
        if (viewportWidth == safeW && viewportHeight == safeH &&
            rootNode.bounds.width == safeW && rootNode.bounds.height == safeH
        ) return
        viewportWidth = safeW
        viewportHeight = safeH
        rootNode.bounds = Rect(0, 0, safeW, safeH)
        canvas2D.widthPx = safeW
        canvas2D.heightPx = safeH
        canvas2D.getContext().resizeSurface(safeW, safeH)
        isLayoutDirty = true
        fullTileSyncPending = true
        repaintManager.markFullViewportDirty()
    }

    fun registerUnderlaySlot(slot: AlienUnderlayExtensionSlotNode) {
        if (slot !in _underlaySlots) {
            _underlaySlots.add(slot)
            onUnderlaySlotRegistered?.invoke(slot)
            requestLayoutAndRepaint(slot.node.bounds)
        }
    }

    fun unregisterUnderlaySlot(slot: AlienUnderlayExtensionSlotNode) {
        if (_underlaySlots.remove(slot)) {
            onUnderlaySlotUnregistered?.invoke(slot)
            repaintManager.markDirty(slot.node.bounds)
        }
    }

    fun requestLayoutAndRepaint(dirtyRect: Rect? = null) {
        isLayoutDirty = true
        if (dirtyRect != null && dirtyRect.width > 0 && dirtyRect.height > 0) {
            repaintManager.markDirty(dirtyRect)
        } else {
            repaintManager.markFullViewportDirty()
        }
    }

    fun repaintIfDirty(deltaMs: Float = 16.67f): List<Rect> =
        repaintManager.stepAnimationsAndRepaint(this, deltaMs)

    fun forceFullRepaint(deltaMs: Float = 0f): List<Rect> {
        isLayoutDirty = true
        fullTileSyncPending = true
        repaintManager.markFullViewportDirty()
        return repaintManager.stepAnimationsAndRepaint(this, deltaMs)
    }

    /**
     * Steps animations, layout, and dirty-region repainting by [deltaMs] and returns
     * the updated offscreen [ImageBuffer].
     */
    fun renderFrameToBuffer(deltaMs: Float = 0f): ImageBuffer {
        repaintManager.stepAnimationsAndRepaint(this, deltaMs)
        return offscreenBuffer
    }

    /**
     * Steps animations, layout, and dirty-region repainting by [deltaMs], then extracts tightly
     * packed RGBA [AlienDirtyTile]s covering all changed viewport regions (or the entire viewport
     * on the first call / after viewport resize).
     */
    fun consumeDirtyTiles(tileSize: Int = 128, deltaMs: Float = 0f): List<AlienDirtyTile> {
        val safeTileSize = tileSize.coerceAtLeast(1)
        val forceFull = fullTileSyncPending
        if (forceFull) {
            repaintManager.markFullViewportDirty()
        }
        val repainted = repaintManager.stepAnimationsAndRepaint(this, deltaMs)
        val effectiveRegions = if (forceFull) {
            fullTileSyncPending = false
            listOf(Rect(0, 0, viewportWidth, viewportHeight))
        } else {
            repainted
        }
        if (effectiveRegions.isEmpty()) return emptyList()

        val vw = viewportWidth
        val vh = viewportHeight
        val cols = (vw + safeTileSize - 1) / safeTileSize
        val rows = (vh + safeTileSize - 1) / safeTileSize
        val dirtyMask = BooleanArray(cols * rows)
        var anyTileDirty = false

        for (region in effectiveRegions) {
            val rx0 = region.x.coerceIn(0, vw)
            val ry0 = region.y.coerceIn(0, vh)
            val rx1 = region.right.coerceIn(0, vw)
            val ry1 = region.bottom.coerceIn(0, vh)
            if (rx1 <= rx0 || ry1 <= ry0) continue
            val minTx = rx0 / safeTileSize
            val maxTx = (rx1 - 1) / safeTileSize
            val minTy = ry0 / safeTileSize
            val maxTy = (ry1 - 1) / safeTileSize
            for (ty in minTy..maxTy) {
                val rowBase = ty * cols
                for (tx in minTx..maxTx) {
                    dirtyMask[rowBase + tx] = true
                    anyTileDirty = true
                }
            }
        }
        if (!anyTileDirty) return emptyList()

        val buffer = offscreenBuffer
        val srcRgba = buffer.directRgbaBytes()
        val bufW = buffer.width.coerceAtLeast(1)
        val bufH = buffer.height.coerceAtLeast(1)
        val canDirectCopy = srcRgba.size >= bufW * bufH * 4 && bufW == vw && bufH == vh
        val tiles = mutableListOf<AlienDirtyTile>()

        for (ty in 0 until rows) {
            val tileY = ty * safeTileSize
            val tileH = min(safeTileSize, vh - tileY)
            if (tileH <= 0) continue
            val rowBase = ty * cols
            for (tx in 0 until cols) {
                if (!dirtyMask[rowBase + tx]) continue
                val tileX = tx * safeTileSize
                val tileW = min(safeTileSize, vw - tileX)
                if (tileW <= 0) continue
                val rgba = if (canDirectCopy) {
                    val out = ByteArray(tileW * tileH * 4)
                    val rowBytes = tileW shl 2
                    for (ry in 0 until tileH) {
                        val srcOff = (((tileY + ry) * bufW) + tileX) shl 2
                        val dstOff = ry * rowBytes
                        srcRgba.copyInto(out, dstOff, srcOff, srcOff + rowBytes)
                    }
                    out
                } else {
                    buffer.readPixelsRgba(tileX, tileY, tileW, tileH)
                }
                tiles.add(
                    AlienDirtyTile(
                        x = tileX,
                        y = tileY,
                        width = tileW,
                        height = tileH,
                        rgbaBytes = rgba
                    )
                )
            }
        }
        return tiles
    }
}
