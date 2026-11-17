package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl

/**
 * Self-rendered Bootstrap 5.3 dropdown (`DropdownAlienImpl`) implementing [DropdownNativeImpl].
 */
class DropdownAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "btn",
    tagName = "div",
    session = session
), DropdownNativeImpl<Any?> {

    private var labelValue: String = ""
    private var itemsList: List<Pair<String, String>> = emptyList()
    private var onItemSelectedListener: ((String) -> Unit)? = null
    var menuOpen: Boolean = false
        private set

    init {
        node.focusRingEnabled = true
        node.customMeasure = { _, _, style ->
            val display = if (labelValue.isBlank()) "Dropdown v" else "$labelValue v"
            val textW = (display.length * style.fontSizePx * 0.58f).toInt().coerceAtLeast(64)
            Size(
                width = textW + style.padding.left + style.padding.right + 16,
                height = style.fontSizePx.toInt() + style.padding.top + style.padding.bottom + 8
            )
        }
        node.customPaint = { ctx, bounds, style ->
            val inner = Rect(
                x = bounds.x + style.padding.left,
                y = bounds.y + style.padding.top,
                width = (bounds.width - style.padding.left - style.padding.right).coerceAtLeast(0),
                height = (bounds.height - style.padding.top - style.padding.bottom).coerceAtLeast(0)
            )
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = if (labelValue.isBlank()) "Dropdown" else labelValue,
                contentBounds = inner,
                style = style,
                verticalCenter = true,
                maxLines = 1
            )
        }
        node.onClick = {
            menuOpen = !menuOpen
            if (itemsList.isNotEmpty()) {
                onItemSelectedListener?.invoke(itemsList.first().first)
            }
            node.invalidateBounds()
        }
    }

    override fun setLabel(label: String) {
        if (labelValue != label) {
            labelValue = label
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    fun getLabel(): String = labelValue

    override fun setItems(items: List<Pair<String, String>>) {
        itemsList = items.toList()
        node.invalidateBounds()
    }

    fun getItems(): List<Pair<String, String>> = itemsList

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }

    fun selectItemById(itemId: String) {
        onItemSelectedListener?.invoke(itemId)
        node.invalidateBounds()
    }
}
