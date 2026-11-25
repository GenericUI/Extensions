package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.StackConstraint
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.css.CssLength

/**
 * Safely moves an [AbstractComponent] from its original parent [AbstractContainer] into a
 * PiP host container while inserting a non-intrusive placeholder at its original index, then
 * restores the exact component instance back to its original parent and slot index on exit.
 */
class ComponentReparentSlot {

    private var savedTarget: AbstractComponent? = null
    private var savedOriginalParent: AbstractContainer? = null
    private var savedOriginalIndex: Int = -1
    private var savedStackConstraint: StackConstraint? = null
    private var savedLayerConstraint: LayerConstraint? = null
    private var activePlaceholder: Container? = null
    private var activeHostContainer: AbstractContainer? = null

    val isMoved: Boolean
        get() = savedTarget != null

    val movedComponent: AbstractComponent?
        get() = savedTarget

    fun moveInto(target: AbstractComponent, hostContainer: AbstractContainer): Boolean {
        if (target === hostContainer || isAncestor(ancestor = target, node = hostContainer)) {
            return false
        }
        if (savedTarget === target && activeHostContainer === hostContainer) {
            return true
        }
        if (isMoved) {
            restore()
        }

        val originalParent = target.parent as? AbstractContainer
        var originalIndex = -1
        var stackConstraint: StackConstraint? = null
        var layerConstraint: LayerConstraint? = null
        var placeholder: Container? = null

        if (originalParent != null) {
            originalIndex = originalParent.children.indexOfFirst { it === target }
            if (originalIndex < 0) {
                originalIndex = originalParent.children.size
            }
            stackConstraint = when (originalParent) {
                is VStack -> originalParent.getChildConstraint(target)
                is HStack -> originalParent.getChildConstraint(target)
                else -> null
            }
            layerConstraint = (originalParent as? LayeredContainer)?.getLayerConstraint(target)

            val createdPlaceholder = createPlaceholderFor(target)
            originalParent.removeChild(target)
            val insertIndex = originalIndex.coerceIn(0, originalParent.children.size)
            insertIntoParent(
                parent = originalParent,
                child = createdPlaceholder,
                index = insertIndex,
                stackConstraint = stackConstraint,
                layerConstraint = layerConstraint
            )
            placeholder = createdPlaceholder
        }

        when (hostContainer) {
            is LayeredContainer -> hostContainer.addLayer(target, LayerConstraint.fill(zIndex = 0))
            else -> hostContainer.addChild(target)
        }

        savedTarget = target
        savedOriginalParent = originalParent
        savedOriginalIndex = originalIndex
        savedStackConstraint = stackConstraint
        savedLayerConstraint = layerConstraint
        activePlaceholder = placeholder
        activeHostContainer = hostContainer
        return true
    }

    fun restore(): Boolean {
        val target = savedTarget ?: return false
        val originalParent = savedOriginalParent
        val originalIndex = savedOriginalIndex
        val stackConstraint = savedStackConstraint
        val layerConstraint = savedLayerConstraint
        val placeholder = activePlaceholder
        val hostContainer = activeHostContainer

        clearSavedState()

        if (hostContainer != null && target.parent === hostContainer) {
            hostContainer.removeChild(target)
        }

        if (originalParent != null && !originalParent.isDisposed) {
            val placeholderIdx = if (placeholder != null) {
                originalParent.children.indexOfFirst { it === placeholder }
            } else {
                -1
            }
            if (placeholder != null && placeholderIdx >= 0) {
                originalParent.removeChild(placeholder)
            }
            placeholder?.dispose()

            val restoreIndex = if (placeholderIdx >= 0) {
                placeholderIdx.coerceIn(0, originalParent.children.size)
            } else if (originalIndex >= 0) {
                originalIndex.coerceIn(0, originalParent.children.size)
            } else {
                originalParent.children.size
            }
            insertIntoParent(
                parent = originalParent,
                child = target,
                index = restoreIndex,
                stackConstraint = stackConstraint,
                layerConstraint = layerConstraint
            )
        } else {
            placeholder?.dispose()
        }

        return true
    }

    private fun clearSavedState() {
        savedTarget = null
        savedOriginalParent = null
        savedOriginalIndex = -1
        savedStackConstraint = null
        savedLayerConstraint = null
        activePlaceholder = null
        activeHostContainer = null
    }

    private fun createPlaceholderFor(target: AbstractComponent): Container {
        val bounds = target.getBounds()
        val preferred = target.getPreferredSize()
        return Container("genericui:pip-placeholder", "pip-reparent-placeholder").apply {
            addClass("pip-reparent-placeholder")
            setAttribute("data-pip-placeholder", "true")
            val w = when {
                preferred != null && preferred.width > 0 -> preferred.width
                bounds.width > 0 -> bounds.width
                else -> 0
            }
            val h = when {
                preferred != null && preferred.height > 0 -> preferred.height
                bounds.height > 0 -> bounds.height
                else -> 0
            }
            if (w > 0 && h > 0) {
                setPreferredSize(Size(w, h))
            } else {
                target.style.width?.let { style.width = it }
                target.style.height?.let { style.height = it }
            }
        }
    }

    private fun insertIntoParent(
        parent: AbstractContainer,
        child: AbstractComponent,
        index: Int,
        stackConstraint: StackConstraint?,
        layerConstraint: LayerConstraint?
    ) {
        when {
            parent is VStack && stackConstraint != null -> {
                parent.addChild(child, stackConstraint, index)
            }
            parent is HStack && stackConstraint != null -> {
                parent.addChild(child, stackConstraint, index)
            }
            else -> {
                parent.addChild(child, index)
                if (parent is LayeredContainer && layerConstraint != null) {
                    parent.setLayerConstraint(child, layerConstraint)
                }
            }
        }
    }

    private fun isAncestor(ancestor: AbstractComponent, node: AbstractComponent): Boolean {
        var cur = node.parent
        while (cur != null) {
            if (cur === ancestor) return true
            cur = cur.parent
        }
        return false
    }
}
