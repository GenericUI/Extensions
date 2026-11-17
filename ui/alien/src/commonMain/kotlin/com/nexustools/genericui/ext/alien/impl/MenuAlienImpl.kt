package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * Self-rendered Bootstrap 5.3 menu (`MenuAlienImpl`) implementing [MenuNativeImpl].
 */
class MenuAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "dropdown-menu",
    tagName = "div",
    session = session
), MenuNativeImpl<Any?> {

    private var labelValue: String = ""
    private var iconValue: String? = null

    init {
        node.customMeasure = { _, _, style ->
            val w = (labelValue.length * style.fontSizePx * 0.6f).toInt().coerceAtLeast(64) + 24
            Size(w, 32)
        }
        node.customPaint = { ctx, bounds, style ->
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = labelValue,
                contentBounds = bounds,
                style = style.copy(textAlign = "center"),
                verticalCenter = true,
                maxLines = 1
            )
        }
    }

    override fun setLabel(label: String) {
        if (labelValue != label) {
            labelValue = label
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getLabel(): String = labelValue

    override fun setIcon(icon: String?) {
        iconValue = icon
        node.invalidateBounds()
    }

    override fun getIcon(): String? = iconValue
}
