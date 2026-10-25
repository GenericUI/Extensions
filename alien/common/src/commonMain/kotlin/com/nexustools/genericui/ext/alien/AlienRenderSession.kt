package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.impl.AlienUnderlayExtensionSlotNode
import com.nexustools.genericui.geometry.Rect

/**
 * Active rendering, layout, animation, and underlay-compositing session for a root [AlienContainer]
 * or [AlienFragment] on System targets.
 *
 * Owns:
 * - [hostFactory]: The underlying OS [NativeFactory] (`WindowsNativeFactory`, `QtNativeFactory`, `GtkNativeFactory`, etc.).
 * - [alienFactory]: The [AlienFactory] provided to all child components via `resolveChildFactory()`.
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
    initialHeight: Int = 600
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

    private val _underlaySlots = mutableListOf<AlienUnderlayExtensionSlotNode>()
    val underlaySlots: List<AlienUnderlayExtensionSlotNode>
        get() = _underlaySlots

    internal var onUnderlaySlotRegistered: ((AlienUnderlayExtensionSlotNode) -> Unit)? = null
    internal var onUnderlaySlotUnregistered: ((AlienUnderlayExtensionSlotNode) -> Unit)? = null

    var isLayoutDirty: Boolean = true

    init {
        // Wire Canvas2D input events to the AlienEventDispatcher
        canvas2D.onPointerMove = { event -> eventDispatcher.dispatchPointerMove(event) }
        canvas2D.onPointerDown = { event -> eventDispatcher.dispatchPointerDown(event) }
        canvas2D.onPointerUp = { event -> eventDispatcher.dispatchPointerUp(event) }
        canvas2D.onContextMenu = { event -> eventDispatcher.dispatchContextMenu(event) }
        canvas2D.onWheel = { event -> eventDispatcher.dispatchWheel(event) }
        canvas2D.onKeyDown = { event -> eventDispatcher.dispatchKeyDown(event) }
        canvas2D.onKeyUp = { event -> eventDispatcher.dispatchKeyUp(event) }

        // Register VBlank render loop for smooth CSS transitions and dirty region updates
        canvas2D.registerVBlankRender { _, deltaMs, _, _ ->
            repaintManager.stepAnimationsAndRepaint(this, deltaMs.toFloat())
        }

        // Attach the native Canvas2D implementation when registered so all rendering and input
        // listeners bind directly to the native Canvas2D surface.
        runCatching { canvas2D.ensureNativeImpl() }
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
        repaintManager.markFullViewportDirty()
        return repaintManager.stepAnimationsAndRepaint(this, deltaMs)
    }
}
