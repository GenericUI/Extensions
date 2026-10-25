package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.transition.ContainerAnimationFrameState

/**
 * Retained scene-graph node (`W` in `NativeImpl<AlienWidgetNode>`) for an [AlienImpl] or
 * [com.nexustools.genericui.ext.alien.impl.AlienUnderlayExtensionSlotNode].
 *
 * Holds the node's layout bounds, Bootstrap 5.3 style snapshot, `ComputedStyle`, interactive states,
 * [AlienTransitionAnimator], child node list, and custom painting/measurement delegates.
 */
class AlienWidgetNode(
    val componentClassPrefix: String = "",
    var tagName: String = "div",
    var session: AlienRenderSession? = null
) {
    var ownerImpl: AlienImpl? = null
    var parent: AlienWidgetNode? = null

    private val _children = mutableListOf<AlienWidgetNode>()
    val children: List<AlienWidgetNode>
        get() = _children

    /**
     * When non-null, this node represents a non-Alien underlay component (`UnalienContainer` or
     * extension widget such as `GLCanvas`, `VKCanvas`, `VideoPlayer`, `WebView`) attached to the
     * host container **below** the `Canvas2D`.
     */
    var wrappedUnderlayNativeImpl: NativeImpl<*>? = null
    var underlayLayerMode: AlienExtensionLayerMode = AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE

    // Style & State
    var styleSnapshot: BootstrapStyleSnapshot = BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)
    var computedStyle: ComputedStyle? = null
    var visible: Boolean = true
    var enabled: Boolean = true
    var hoveredState: Boolean = false
    var focusedState: Boolean = false
    var activePressedState: Boolean = false
    var checkedState: Boolean = false
    var focusRingEnabled: Boolean = false
    var opacity: Float = 1.0f
    var tooltipText: String? = null
    val attributes: MutableMap<String, String> = linkedMapOf()
    var preferredSize: Size? = null
    var padding: EdgeInsets = EdgeInsets.ZERO
    var margin: EdgeInsets = EdgeInsets.ZERO
    var tint: RgbaColor? = null
    var backgroundGradient: Gradient? = null

    // Layout & Bounds
    var bounds: Rect = Rect(0, 0, 0, 0)
    var measuredSize: Size = Size(0, 0)
    var intrinsicWidth: Float = 0f
    var intrinsicHeight: Float = 0f
    val layoutWidth: Float
        get() = bounds.width.toFloat()
    val layoutHeight: Float
        get() = bounds.height.toFloat()
    var scrollOffsetX: Int = 0
    var scrollOffsetY: Int = 0
    var layoutDirty: Boolean = true

    // Transitions, Animations & Offscreen Container Cache
    val transitionAnimator: AlienTransitionAnimator = AlienTransitionAnimator()
    var hasContinuousAnimation: Boolean = false
    var hardwareLayerCacheEnabled: Boolean = false
    var cachedOffscreenBuffer: ImageBuffer? = null
    var animatedFrameState: ContainerAnimationFrameState? = null

    // Event Listeners
    var onClick: (() -> Unit)? = null
    var onDoubleClick: (() -> Unit)? = null
    var onContextMenu: ((PointerEvent) -> Unit)? = null
    var onPointerDown: ((PointerEvent) -> Unit)? = null
    var onPointerMove: ((PointerEvent) -> Unit)? = null
    var onPointerUp: ((PointerEvent) -> Unit)? = null
    var onWheel: ((WheelEvent) -> Unit)? = null
    var onKeyDown: ((KeyEvent) -> Unit)? = null
    var onKeyUp: ((KeyEvent) -> Unit)? = null

    // Component-Specific Measure & Paint Hooks
    var customMeasure: ((availableWidth: Int, availableHeight: Int, style: AlienResolvedVisualStyle) -> Size)? = null
    var customPaint: ((ctx: ImageBuffer2DContext, bounds: Rect, style: AlienResolvedVisualStyle) -> Unit)? = null

    val isUnderlayExtensionSlot: Boolean
        get() = wrappedUnderlayNativeImpl != null

    val contentBounds: Rect
        get() {
            val style = resolveVisualStyle()
            val pad = style.padding
            val cx = bounds.x + pad.left
            val cy = bounds.y + pad.top
            val cw = (bounds.width - pad.left - pad.right).coerceAtLeast(0)
            val ch = (bounds.height - pad.top - pad.bottom).coerceAtLeast(0)
            return Rect(cx, cy, cw, ch)
        }

    fun resolveVisualStyle(): AlienResolvedVisualStyle {
        val target = AlienCssStyleResolver.resolve(this)
        return transitionAnimator.resolveVisualStyleForFrame(target)
    }

    fun addChild(child: AlienWidgetNode, index: Int = -1) {
        if (child.parent != null && child.parent !== this) {
            child.parent?.removeChild(child)
        }
        child.parent = this
        child.propagateSession(session)
        if (index < 0 || index >= _children.size) {
            _children.add(child)
        } else {
            _children.add(index, child)
        }
        markLayoutDirty()
        invalidateBounds()
    }

    fun removeChild(child: AlienWidgetNode): Boolean {
        val oldBounds = child.bounds
        val removed = _children.remove(child)
        if (removed) {
            child.parent = null
            markLayoutDirty()
            session?.repaintManager?.markDirty(oldBounds)
            invalidateBounds()
        }
        return removed
    }

    fun clearChildren() {
        for (c in _children) {
            c.parent = null
        }
        _children.clear()
        markLayoutDirty()
        invalidateBounds()
    }

    fun propagateSession(newSession: AlienRenderSession?) {
        this.session = newSession
        for (child in _children) {
            child.propagateSession(newSession)
        }
    }

    fun markLayoutDirty() {
        layoutDirty = true
        parent?.layoutDirty = true
        session?.requestLayoutAndRepaint(bounds)
    }

    fun invalidateBounds() {
        session?.repaintManager?.markDirty(bounds)
    }
}
