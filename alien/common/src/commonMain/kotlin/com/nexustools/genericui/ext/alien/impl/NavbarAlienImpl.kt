package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.navbar` header (`NavbarAlienImpl`) implementing [NavbarNativeImpl].
 */
class NavbarAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "navbar",
    tagName = "nav",
    session = session
), NavbarNativeImpl<Any?> {

    private var brandTitleValue: String = ""

    init {
        node.customMeasure = { availW, _, _ ->
            Size(width = if (availW > 0) availW else 640, height = 56)
        }
        node.customPaint = { ctx, bounds, style ->
            if (brandTitleValue.isNotEmpty()) {
                val brandRect = Rect(bounds.x + 16, bounds.y, (bounds.width - 32).coerceAtLeast(0), bounds.height)
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = brandTitleValue,
                    contentBounds = brandRect,
                    style = style.copy(fontSizePx = 20f, fontWeight = 500),
                    verticalCenter = true,
                    maxLines = 1
                )
            }
        }
    }

    override fun setBrandTitle(title: String) {
        if (brandTitleValue != title) {
            brandTitleValue = title
            node.invalidateBounds()
        }
    }

    override fun getBrandTitle(): String = brandTitleValue
}
