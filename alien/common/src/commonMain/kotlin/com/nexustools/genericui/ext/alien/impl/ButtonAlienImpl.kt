package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl

/**
 * Self-rendered Bootstrap 5.3 button (`ButtonAlienImpl`) implementing [ButtonNativeImpl].
 */
class ButtonAlienImpl(
    componentClassPrefix: String = "btn",
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = componentClassPrefix,
    tagName = "button",
    session = session
), ButtonNativeImpl<Any?> {

    private var textValue: String = ""
    private var iconValue: String? = null

    init {
        node.focusRingEnabled = true
        node.intrinsicWidth = 96f
        node.intrinsicHeight = 36f
        node.customMeasure = { _, _, style ->
            val displayLabel = buildDisplayLabel()
            val textW = (displayLabel.length * style.fontSizePx * 0.56f).toInt().coerceAtLeast(16)
            val w = maxOf(node.intrinsicWidth.toInt(), textW + style.padding.left + style.padding.right + 8)
            val h = maxOf(node.intrinsicHeight.toInt(), style.fontSizePx.toInt() + style.padding.top + style.padding.bottom + 8)
            Size(w, h)
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
                text = buildDisplayLabel(),
                contentBounds = inner,
                style = style.copy(textAlign = "center"),
                verticalCenter = true,
                maxLines = 1
            )
        }
    }

    override fun setText(text: String) {
        if (textValue != text) {
            textValue = text
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getText(): String = textValue

    override fun setIcon(icon: String?) {
        if (iconValue != icon) {
            iconValue = icon
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getIcon(): String? = iconValue

    private fun buildDisplayLabel(): String {
        val ic = iconValue
        return when {
            ic.isNullOrBlank() -> textValue
            textValue.isBlank() -> ic
            else -> "$ic $textValue"
        }
    }
}
