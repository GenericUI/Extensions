package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Self-rendered hierarchical tree view (`TreeViewAlienImpl`) implementing [TreeViewNativeImpl].
 */
class TreeViewAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "tree-view",
    tagName = "div",
    session = session
), TreeViewNativeImpl<Any?> {

    private var rootNodesList: List<TreeNode<*>> = emptyList()
    private var selectedId: String? = null
    private var onNodeSelectedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeDoubleClickedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeContextMenuListener: ((TreeNode<*>, Float, Float) -> Unit)? = null

    init {
        node.customMeasure = { availW, _, _ ->
            val visibleRows = flattenVisibleNodes().size.coerceAtLeast(1)
            Size(width = if (availW > 0) availW else 260, height = visibleRows * 28 + 8)
        }
        node.customPaint = { ctx, bounds, style ->
            val rows = flattenVisibleNodes()
            val rowH = 28
            for ((idx, pair) in rows.withIndex()) {
                val (treeNode, depth) = pair
                val ry = bounds.y + idx * rowH
                if (ry + rowH > bounds.bottom) break
                val isSelected = treeNode.id == selectedId
                if (isSelected) {
                    ctx.setFillColor(AlienBootstrapThemeTokens.PRIMARY)
                    AlienBootstrapPainter.fillRoundedRect(
                        ctx,
                        bounds.x.toFloat(),
                        ry.toFloat(),
                        bounds.width.toFloat(),
                        rowH.toFloat(),
                        4f
                    )
                }
                val prefix = when {
                    treeNode.children.isEmpty() -> "  "
                    treeNode.isExpanded -> "▾ "
                    else -> "▸ "
                }
                val indentPx = depth * 18 + 8
                val rowRect = Rect(bounds.x + indentPx, ry, (bounds.width - indentPx).coerceAtLeast(0), rowH)
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = prefix + treeNode.label,
                    contentBounds = rowRect,
                    style = style.copy(
                        textColor = if (isSelected) RgbaColor.WHITE else style.textColor
                    ),
                    verticalCenter = true,
                    maxLines = 1
                )
            }
        }
        node.onPointerDown = { ev ->
            val rows = flattenVisibleNodes()
            val rowIdx = ((ev.y.toInt() - node.bounds.y) / 28)
            if (rowIdx in rows.indices) {
                val hitNode = rows[rowIdx].first
                selectedId = hitNode.id
                onNodeSelectedListener?.invoke(hitNode)
                if (ev.clickCount >= 2) {
                    onNodeDoubleClickedListener?.invoke(hitNode)
                }
                node.invalidateBounds()
            }
        }
    }

    private fun flattenVisibleNodes(): List<Pair<TreeNode<*>, Int>> {
        val out = mutableListOf<Pair<TreeNode<*>, Int>>()
        fun visit(n: TreeNode<*>, depth: Int) {
            out.add(n to depth)
            if (n.isExpanded) {
                for (child in n.children) {
                    visit(child, depth + 1)
                }
            }
        }
        for (r in rootNodesList) {
            visit(r, 0)
        }
        return out
    }

    override fun setRootNodes(roots: List<TreeNode<*>>) {
        rootNodesList = roots.toList()
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setSelectedNodeId(nodeId: String?) {
        selectedId = nodeId
        node.invalidateBounds()
    }

    override fun setOnNodeSelected(listener: ((TreeNode<*>) -> Unit)?) {
        onNodeSelectedListener = listener
    }

    override fun setOnNodeDoubleClicked(listener: ((TreeNode<*>) -> Unit)?) {
        onNodeDoubleClickedListener = listener
    }

    override fun setOnNodeContextMenu(listener: ((TreeNode<*>, Float, Float) -> Unit)?) {
        onNodeContextMenuListener = listener
    }
}
