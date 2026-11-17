package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl

/**
 * Self-rendered Bootstrap 5.3 context menu (`ContextMenuAlienImpl`) implementing [ContextMenuNativeImpl].
 */
class ContextMenuAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "dropdown-menu",
    tagName = "div",
    session = session
), ContextMenuNativeImpl<Any?> {

    var isShown: Boolean = false
        private set

    init {
        node.visible = false
        node.customMeasure = { _, _, _ ->
            var maxW = 180
            var totalH = 12
            for (child in node.children) {
                if (!child.visible) continue
                val isSeparator = child.tagName == "hr" || child.componentClassPrefix == "hr" ||
                    "dropdown-divider" in child.styleSnapshot.customClasses
                if (isSeparator) {
                    totalH += 9
                } else {
                    maxW = maxOf(maxW, child.measuredSize.width.coerceAtLeast(180))
                    totalH += child.measuredSize.height.takeIf { it > 0 } ?: 32
                }
            }
            Size(maxW, totalH.coerceAtLeast(44))
        }
        node.customPaint = { ctx, bounds, style ->
            var cursorY = bounds.y + 6
            for (child in node.children) {
                if (!child.visible) continue
                val isSeparator = child.tagName == "hr" || child.componentClassPrefix == "hr" ||
                    "dropdown-divider" in child.styleSnapshot.customClasses
                val itemH = if (isSeparator) 9 else (child.measuredSize.height.takeIf { it > 0 } ?: 32)
                if (child.bounds.width <= 0 || child.bounds.height <= 0) {
                    child.bounds = Rect(bounds.x + 1, cursorY, (bounds.width - 2).coerceAtLeast(1), itemH)
                }
                if (isSeparator) {
                    val lineColor = if (style.darkTheme) {
                        AlienBootstrapThemeTokens.BORDER_COLOR_DARK
                    } else {
                        AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
                    }
                    val midY = child.bounds.y + child.bounds.height * 0.5f
                    ctx.save()
                    ctx.setStrokeColor(lineColor)
                    ctx.setLineWidth(1f)
                    ctx.beginPath()
                    ctx.moveTo((bounds.x + 8).toFloat(), midY)
                    ctx.lineTo((bounds.right - 8).toFloat(), midY)
                    ctx.stroke()
                    ctx.restore()
                }
                cursorY += itemH
            }
        }
    }

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        if (childImpl is DividerAlienImpl) {
            childImpl.node.tagName = "hr"
            childImpl.addClass("dropdown-divider")
            childImpl.node.customMeasure = { availW, _, _ ->
                Size(width = if (availW > 0) availW else 180, height = 9)
            }
        }
        super.addChildNative(childImpl, index)
    }

    override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) {
        isShown = true
        node.visible = true
        val anchorNode = (anchor?.nativeWidget as? AlienWidgetNode) ?: (anchor as? AlienImpl)?.node
        if (node.parent == null && anchorNode?.session != null) {
            anchorNode.session?.rootNode?.addChild(node)
        }
        val anchorBounds = anchor?.getBounds()
        val px = if (anchorBounds != null) anchorBounds.x + x else x
        val py = if (anchorBounds != null) anchorBounds.y + y else y
        val measured = node.customMeasure?.invoke(180, 600, node.resolveVisualStyle()) ?: Size(180, 96)
        node.bounds = Rect(px, py, maxOf(node.bounds.width, measured.width), maxOf(node.bounds.height, measured.height))
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun hide() {
        isShown = false
        node.visible = false
        node.invalidateBounds()
    }
}
