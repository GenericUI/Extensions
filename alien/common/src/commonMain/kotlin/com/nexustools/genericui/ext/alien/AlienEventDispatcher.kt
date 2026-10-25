package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent

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
     * Finds the topmost visible, pointer-enabled [AlienWidgetNode] at `(x, y)` in reverse paint order.
     */
    fun hitTest(x: Float, y: Float): AlienWidgetNode? {
        val px = x.toInt()
        val py = y.toInt()
        val ordered = mutableListOf<AlienWidgetNode>()
        collectVisiblePaintOrder(session.rootNode, ordered)
        for (i in ordered.indices.reversed()) {
            val candidate = ordered[i]
            if (candidate === session.rootNode) continue
            val style = candidate.resolveVisualStyle()
            if (!style.pointerEventsEnabled) continue
            if (candidate.bounds.contains(px, py)) {
                return candidate
            }
        }
        return if (session.rootNode.bounds.contains(px, py)) session.rootNode else null
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
        val hit = hitTest(event.x, event.y)
        if (hit !== hoveredNode) {
            val prev = hoveredNode
            hoveredNode = hit
            if (prev != null) {
                prev.hoveredState = false
                prev.invalidateBounds()
            }
            if (hit != null && hit.enabled) {
                hit.hoveredState = true
                hit.invalidateBounds()
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
        val hit = hitTest(event.x, event.y)

        if (hit !== focusedNode) {
            val prevFocus = focusedNode
            focusedNode = hit?.takeIf { it.enabled }
            if (prevFocus != null) {
                prevFocus.focusedState = false
                prevFocus.invalidateBounds()
            }
            if (focusedNode != null) {
                focusedNode!!.focusedState = true
                focusedNode!!.invalidateBounds()
            }
        }

        if (activePressedNode != null && activePressedNode !== hit) {
            activePressedNode!!.activePressedState = false
            activePressedNode!!.invalidateBounds()
        }
        if (hit != null && hit.enabled) {
            activePressedNode = hit
            hit.activePressedState = true
            hit.invalidateBounds()
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
        val hit = hitTest(event.x, event.y)
        val pressed = activePressedNode
        if (pressed != null) {
            pressed.activePressedState = false
            pressed.invalidateBounds()
            activePressedNode = null
        }

        if (hit != null && hit.isUnderlayExtensionSlot) {
            val localEvent = toLocalPointerEvent(event, hit)
            hit.onPointerUp?.invoke(localEvent)
            if (pressed === hit) {
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

        if (hit != null && hit.enabled && (pressed === hit || isDescendantOf(hit, pressed))) {
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
            cur = cur.parent
        }
        return false
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

    private fun collectVisiblePaintOrder(node: AlienWidgetNode, out: MutableList<AlienWidgetNode>) {
        if (!node.visible) return
        val style = node.resolveVisualStyle()
        if (style.display == "none" || style.opacity <= 0.001f ||
            style.transform2D.scaleX == 0f || style.transform2D.scaleY == 0f
        ) {
            return
        }
        out.add(node)
        if (node.children.isEmpty()) return
        val sortedChildren = node.children
            .filter { it.visible }
            .sortedBy { it.resolveVisualStyle().zIndex }
        for (child in sortedChildren) {
            collectVisiblePaintOrder(child, out)
        }
    }
}
