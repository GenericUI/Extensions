package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.dropdown-item` menu item (`MenuItemAlienImpl`) implementing [MenuItemNativeImpl].
 */
class MenuItemAlienImpl(
    val checkable: Boolean = false,
    val radio: Boolean = false,
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "dropdown-item",
    tagName = "button",
    session = session
), MenuItemNativeImpl<Any?> {

    private var labelValue: String = ""
    private var iconValue: String? = null
    private var shortcutValue: KeyShortcut? = null

    init {
        node.customMeasure = { availW, _, style ->
            val w = if (availW > 0) availW else (labelValue.length * style.fontSizePx * 0.6f).toInt() + 48
            Size(w.coerceAtLeast(120), 32)
        }
        node.customPaint = { ctx, bounds, style ->
            val prefix = when {
                node.checkedState && radio -> "● "
                node.checkedState -> "✓ "
                !iconValue.isNullOrBlank() -> "$iconValue "
                else -> ""
            }
            val inner = Rect(bounds.x + 12, bounds.y, (bounds.width - 24).coerceAtLeast(0), bounds.height)
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = prefix + labelValue,
                contentBounds = inner,
                style = style,
                verticalCenter = true,
                maxLines = 1
            )
        }
    }

    override fun setLabel(label: String) {
        labelValue = label
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun getLabel(): String = labelValue

    override fun setIcon(icon: String?) {
        iconValue = icon
        node.invalidateBounds()
    }

    override fun getIcon(): String? = iconValue

    override fun setShortcut(shortcut: KeyShortcut?) {
        shortcutValue = shortcut
        node.invalidateBounds()
    }

    override fun getShortcut(): KeyShortcut? = shortcutValue

    override fun setChecked(checked: Boolean) {
        node.checkedState = checked
        node.invalidateBounds()
    }

    override fun isChecked(): Boolean = node.checkedState
}
