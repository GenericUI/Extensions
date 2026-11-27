package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.style.transition.CssTransform2D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reverse Z-order hit-testing, interactive pseudo-state (`:hover`, `:focus`, `:active`) management,
 * and underlay mouse/pointer/wheel passthrough router for an [AlienRenderSession].
 */
class AlienEventDispatcher(
    private val session: AlienRenderSession
) {
    var hoveredNode: AlienWidgetNode? = null
        private set

    var focusedNode: AlienWidgetNode? = null
        private set

    var activePressedNode: AlienWidgetNode? = null
        private set

    /**
     * Maximum pointer travel distance (in pixels) between `pointerDown` and `pointerUp` before a
     * release is treated as a drag gesture rather than a click (`onClick` / `dispatchClickBubble`).
     */
    var dragClickSuppressionThresholdPx: Float = 6f

    private var pointerDownX: Float = 0f
    private var pointerDownY: Float = 0f
    private var hasActivePointerDown: Boolean = false
    private var pointerDraggedBeyondThreshold: Boolean = false

    /**
     * Finds the topmost visible, pointer-enabled [AlienWidgetNode] at `(x, y)` in reverse paint order,
     * applying inverse `transform2D` (`scale`, `rotate`, `translate`) and `overflow: hidden` clipping
     * at each level of the hierarchy.
     */
    fun hitTest(x: Float, y: Float): AlienWidgetNode? {
        return hitTestNode(session.rootNode, x, y)
    }

    fun elementFromPoint(x: Float, y: Float): AlienWidgetNode? {
        val hit = hitTest(x, y)
        return if (hit === session.rootNode) null else hit
    }

    private fun hitTestNode(
        node: AlienWidgetNode,
        parentX: Float,
        parentY: Float,
        inheritedPointerEvents: Boolean = true
    ): AlienWidgetNode? {
        if (!node.visible) return null
        val style = node.resolveVisualStyle()
        if (style.display == "none") return null

        val effectivePointerEvents = if (node.tagName == "#text") {
            inheritedPointerEvents
        } else {
            style.pointerEventsEnabled
        }

        val transform = style.transform2D
        if (transform.scaleX == 0f || transform.scaleY == 0f) return null

        val (localX, localY) = inverseTransformPoint(node, style, transform, parentX, parentY)

        val clipsX = style.overflow != OverflowBehavior.VISIBLE || style.overflowX != OverflowBehavior.VISIBLE
        val clipsY = style.overflow != OverflowBehavior.VISIBLE || style.overflowY != OverflowBehavior.VISIBLE
        if (clipsX && (localX < node.bounds.x || localX >= node.bounds.right)) {
            return null
        }
        if (clipsY && (localY < node.bounds.y || localY >= node.bounds.bottom)) {
            return null
        }

        if (node.children.isNotEmpty()) {
            val sortedChildren = node.children
                .filter { it.visible }
                .sortedBy { it.resolveVisualStyle().zIndex }
            for (i in sortedChildren.indices.reversed()) {
                val hitChild = hitTestNode(sortedChildren[i], localX, localY, effectivePointerEvents)
                if (hitChild != null) return hitChild
            }
        }

        if (style.visible && effectivePointerEvents && node.bounds.contains(localX.toInt(), localY.toInt())) {
            return node
        }
        return null
    }

    private fun inverseTransformPoint(
        node: AlienWidgetNode,
        style: AlienResolvedVisualStyle,
        transform: CssTransform2D,
        px: Float,
        py: Float
    ): Pair<Float, Float> {
        if (transform == CssTransform2D.IDENTITY) return px to py
        val isPositioned = style.position == "absolute" || style.position == "fixed"
        val m = transform.resolveAffineMatrix(node.bounds.width, node.bounds.height)
        val a = m[0]
        val b = m[1]
        val c = m[2]
        val d = m[3]
        val tx = if (isPositioned && !transform.hasMatrix) 0f else m[4]
        val ty = if (isPositioned && !transform.hasMatrix) 0f else m[5]
        if (tx == 0f && ty == 0f && a == 1f && b == 0f && c == 0f && d == 1f) {
            return px to py
        }
        val det = a * d - b * c
        if (kotlin.math.abs(det) < 1e-6f) return Float.NaN to Float.NaN

        val ox = node.bounds.x + (style.transformOriginXPxOffset
            ?: ((style.transformOriginXPercent / 100f) * node.bounds.width))
        val oy = node.bounds.y + (style.transformOriginYPxOffset
            ?: ((style.transformOriginYPercent / 100f) * node.bounds.height))

        val rx = px - (ox + tx)
        val ry = py - (oy + ty)
        val invX = (d * rx - c * ry) / det
        val invY = (-b * rx + a * ry) / det
        return (invX + ox) to (invY + oy)
    }

    /**
     * Returns `true` when `(x, y)` hits an uncovered [AlienWidgetNode.isUnderlayExtensionSlot]
     * (`UnalienContainer` or extension widget below the `Canvas2D`), meaning mouse/pointer/wheel
     * events should pass through the `Canvas2D` to the underlay native component.
     */
    fun isPointPassthroughToUnderlay(x: Float, y: Float): Boolean {
        val hit = hitTest(x, y) ?: return false
        return hit.isUnderlayExtensionSlot
    }

    /**
     * Returns the underlay [AlienWidgetNode] at `(x, y)` if and only if no higher-Z Alien component
     * covers `(x, y)`.
     */
    fun findUncoveredUnderlaySlotAt(x: Float, y: Float): AlienWidgetNode? {
        val hit = hitTest(x, y) ?: return null
        return if (hit.isUnderlayExtensionSlot) hit else null
    }

    fun dispatchPointerMove(event: PointerEvent): Boolean {
        if (hasActivePointerDown && !pointerDraggedBeyondThreshold) {
            val dx = event.x - pointerDownX
            val dy = event.y - pointerDownY
            if (sqrt(dx * dx + dy * dy) > dragClickSuppressionThresholdPx) {
                pointerDraggedBeyondThreshold = true
            }
        }

        val hit = hitTest(event.x, event.y)
        if (hit !== hoveredNode) {
            val prev = hoveredNode
            hoveredNode = hit
            if (prev != null) {
                prev.ownerImpl?.setHoveredState(false) ?: run {
                    prev.hoveredState = false
                    prev.invalidateBounds()
                }
            }
            if (hit != null && hit.enabled) {
                hit.ownerImpl?.setHoveredState(true) ?: run {
                    hit.hoveredState = true
                    hit.invalidateBounds()
                }
            }
        }

        if (hit != null && hit.isUnderlayExtensionSlot) {
            hit.onPointerMove?.invoke(toLocalPointerEvent(event, hit))
            return true
        }

        var cur = hit
        while (cur != null) {
            cur.onPointerMove?.let {
                it.invoke(event)
                session.repaintIfDirty()
                return true
            }
            cur = cur.parent
        }
        session.repaintIfDirty()
        return false
    }

    fun dispatchPointerDown(event: PointerEvent): Boolean {
        pointerDownX = event.x
        pointerDownY = event.y
        hasActivePointerDown = true
        pointerDraggedBeyondThreshold = false

        val hit = hitTest(event.x, event.y)

        if (hit !== focusedNode) {
            val prevFocus = focusedNode
            focusedNode = hit?.takeIf { it.enabled }
            if (prevFocus != null) {
                prevFocus.ownerImpl?.setFocusedState(false) ?: run {
                    prevFocus.focusedState = false
                    prevFocus.invalidateBounds()
                }
            }
            val nextFocus = focusedNode
            if (nextFocus != null) {
                nextFocus.ownerImpl?.setFocusedState(true) ?: run {
                    nextFocus.focusedState = true
                    nextFocus.invalidateBounds()
                }
            }
        }

        if (activePressedNode != null && activePressedNode !== hit) {
            val prevPressed = activePressedNode!!
            prevPressed.ownerImpl?.setActivePressedState(false) ?: run {
                prevPressed.activePressedState = false
                prevPressed.invalidateBounds()
            }
        }
        if (hit != null && hit.enabled) {
            activePressedNode = hit
            hit.ownerImpl?.setActivePressedState(true) ?: run {
                hit.activePressedState = true
                hit.invalidateBounds()
            }
        }

        if (hit != null && hit.isUnderlayExtensionSlot) {
            hit.onPointerDown?.invoke(toLocalPointerEvent(event, hit))
            session.repaintIfDirty()
            return true
        }

        var cur = hit
        while (cur != null) {
            if (cur.enabled && cur.onPointerDown != null) {
                cur.onPointerDown!!.invoke(event)
                session.repaintIfDirty()
                return true
            }
            cur = cur.parent
        }
        session.repaintIfDirty()
        return hit != null
    }

    fun dispatchPointerUp(event: PointerEvent): Boolean {
        val wasDragged = if (hasActivePointerDown) {
            val dx = event.x - pointerDownX
            val dy = event.y - pointerDownY
            pointerDraggedBeyondThreshold || sqrt(dx * dx + dy * dy) > dragClickSuppressionThresholdPx
        } else {
            false
        }
        hasActivePointerDown = false
        pointerDraggedBeyondThreshold = false

        val hit = hitTest(event.x, event.y)
        val pressed = activePressedNode
        if (pressed != null) {
            activePressedNode = null
            pressed.ownerImpl?.setActivePressedState(false) ?: run {
                pressed.activePressedState = false
                pressed.invalidateBounds()
            }
        }

        if (hit != null && hit.isUnderlayExtensionSlot) {
            val localEvent = toLocalPointerEvent(event, hit)
            hit.onPointerUp?.invoke(localEvent)
            if (!wasDragged && pressed === hit) {
                hit.onClick?.invoke()
            }
            session.repaintIfDirty()
            return true
        }

        var cur = hit
        while (cur != null) {
            if (cur.enabled && cur.onPointerUp != null) {
                cur.onPointerUp!!.invoke(event)
                break
            }
            cur = cur.parent
        }

        if (!wasDragged && hit != null && hit.enabled && (pressed === hit || isDescendantOf(hit, pressed))) {
            dispatchClickBubble(hit, event.clickCount)
        }

        session.repaintIfDirty()
        return hit != null
    }

    fun dispatchContextMenu(event: PointerEvent): Boolean {
        val hit = hitTest(event.x, event.y) ?: return false
        if (hit.isUnderlayExtensionSlot) {
            hit.onContextMenu?.invoke(toLocalPointerEvent(event, hit))
            return true
        }
        var cur: AlienWidgetNode? = hit
        while (cur != null) {
            if (cur.enabled && cur.onContextMenu != null) {
                cur.onContextMenu!!.invoke(event)
                session.repaintIfDirty()
                return true
            }
            cur = cur.parent
        }
        return false
    }

    fun dispatchWheel(event: WheelEvent): Boolean {
        val hit = hitTest(event.x, event.y) ?: return false
        if (hit.isUnderlayExtensionSlot) {
            val localWheel = event.copy(
                x = event.x - hit.bounds.x,
                y = event.y - hit.bounds.y
            )
            hit.onWheel?.invoke(localWheel)
            return true
        }
        var cur: AlienWidgetNode? = hit
        while (cur != null) {
            if (cur.onWheel != null) {
                cur.onWheel!!.invoke(event)
                session.repaintIfDirty()
                return true
            }
            val cs = cur.resolveVisualStyle()
            val canScrollY = (cs.overflowY == OverflowBehavior.SCROLL || cs.overflowY == OverflowBehavior.AUTO ||
                cs.overflow == OverflowBehavior.SCROLL || cs.overflow == OverflowBehavior.AUTO) &&
                cur.scrollHeight > cur.bounds.height
            val canScrollX = (cs.overflowX == OverflowBehavior.SCROLL || cs.overflowX == OverflowBehavior.AUTO ||
                cs.overflow == OverflowBehavior.SCROLL || cs.overflow == OverflowBehavior.AUTO) &&
                cur.scrollWidth > cur.bounds.width
            if (canScrollY || canScrollX) {
                val maxX = if (canScrollX) (cur.scrollWidth - cur.bounds.width).coerceAtLeast(0) else cur.scrollOffsetX
                val maxY = if (canScrollY) (cur.scrollHeight - cur.bounds.height).coerceAtLeast(0) else cur.scrollOffsetY
                val nextX = if (canScrollX) (cur.scrollOffsetX + event.deltaX.toInt()).coerceIn(0, maxX) else cur.scrollOffsetX
                val nextY = if (canScrollY) (cur.scrollOffsetY + event.deltaY.toInt()).coerceIn(0, maxY) else cur.scrollOffsetY
                if (nextX != cur.scrollOffsetX || nextY != cur.scrollOffsetY) {
                    cur.scrollOffsetX = nextX
                    cur.scrollOffsetY = nextY
                    cur.markLayoutDirty()
                    session.repaintIfDirty()
                    return true
                }
            }
            cur = cur.parent
        }
        return false
    }

    internal fun syncProgrammaticFocus(targetNode: AlienWidgetNode, focused: Boolean) {
        if (focused) {
            if (!targetNode.enabled) return
            if (focusedNode !== targetNode) {
                val prev = focusedNode
                focusedNode = targetNode
                if (prev != null && prev.focusedState) {
                    prev.ownerImpl?.setFocusedState(false) ?: run {
                        prev.focusedState = false
                        prev.invalidateBounds()
                    }
                }
            }
        } else if (focusedNode === targetNode) {
            focusedNode = null
        }
    }

    fun dispatchKeyDown(event: KeyEvent): Boolean {
        val target = focusedNode ?: session.rootNode
        var cur: AlienWidgetNode? = target
        while (cur != null) {
            if (cur.enabled && cur.onKeyDown != null) {
                cur.onKeyDown!!.invoke(event)
                session.repaintIfDirty()
                return true
            }
            cur = cur.parent
        }
        return false
    }

    fun dispatchKeyUp(event: KeyEvent): Boolean {
        val target = focusedNode ?: session.rootNode
        var cur: AlienWidgetNode? = target
        while (cur != null) {
            if (cur.enabled && cur.onKeyUp != null) {
                cur.onKeyUp!!.invoke(event)
                session.repaintIfDirty()
                return true
            }
            cur = cur.parent
        }
        return false
    }

    private fun dispatchClickBubble(startNode: AlienWidgetNode, clickCount: Int) {
        var cur: AlienWidgetNode? = startNode
        while (cur != null) {
            if (cur.enabled) {
                if (clickCount >= 2 && cur.onDoubleClick != null) {
                    cur.onDoubleClick!!.invoke()
                    return
                }
                if (cur.onClick != null) {
                    cur.onClick!!.invoke()
                    return
                }
            }
            cur = cur.parent
        }
    }

    private fun isDescendantOf(node: AlienWidgetNode, ancestor: AlienWidgetNode?): Boolean {
        if (ancestor == null) return false
        var cur: AlienWidgetNode? = node.parent
        while (cur != null) {
            if (cur === ancestor) return true
            cur = cur.parent
        }
        return false
    }

    private fun toLocalPointerEvent(event: PointerEvent, slotNode: AlienWidgetNode): PointerEvent =
        event.copy(
            x = event.x - slotNode.bounds.x,
            y = event.y - slotNode.bounds.y
        )
}
