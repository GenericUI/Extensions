package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl

/**
 * Self-rendered Bootstrap 5.3 bottom navigation bar (`BottomNavigationBarAlienImpl`)
 * implementing [BottomNavigationBarNativeImpl].
 */
class BottomNavigationBarAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "navbar",
    tagName = "nav",
    session = session
), BottomNavigationBarNativeImpl<Any?> {

    private var navItems: List<Triple<String, String, String?>> = emptyList()
    private var selectedId: String? = null
    private var onItemSelectedListener: ((String) -> Unit)? = null

    init {
        node.customMeasure = { availW, _, _ ->
            Size(width = if (availW > 0) availW else 480, height = 56)
        }
        node.customPaint = { ctx, bounds, style ->
            if (navItems.isNotEmpty()) {
                val itemW = (bounds.width / navItems.size).coerceAtLeast(1)
                for ((idx, item) in navItems.withIndex()) {
                    val (id, label, icon) = item
                    val isSel = id == selectedId
                    val itemRect = Rect(bounds.x + idx * itemW, bounds.y, itemW, bounds.height)
                    val display = if (!icon.isNullOrBlank()) "$icon $label" else label
                    AlienBootstrapPainter.paintText(
                        ctx = ctx,
                        text = display,
                        contentBounds = itemRect,
                        style = style.copy(
                            textColor = if (isSel) AlienBootstrapThemeTokens.PRIMARY else style.textColor,
                            fontWeight = if (isSel) 600 else 400,
                            textAlign = "center"
                        ),
                        verticalCenter = true,
                        maxLines = 1
                    )
                }
            }
        }
        node.onPointerDown = { ev ->
            if (navItems.isNotEmpty() && node.bounds.width > 0) {
                val itemW = (node.bounds.width / navItems.size).coerceAtLeast(1)
                val idx = ((ev.x.toInt() - node.bounds.x) / itemW).coerceIn(0, navItems.lastIndex)
                val chosenId = navItems[idx].first
                selectedId = chosenId
                onItemSelectedListener?.invoke(chosenId)
                node.invalidateBounds()
            }
        }
    }

    override fun setItems(items: List<Triple<String, String, String?>>) {
        navItems = items.toList()
        if (selectedId == null && navItems.isNotEmpty()) {
            selectedId = navItems.first().first
        }
        node.invalidateBounds()
    }

    override fun setSelectedItemId(itemId: String) {
        if (selectedId != itemId) {
            selectedId = itemId
            node.invalidateBounds()
        }
    }

    override fun getSelectedItemId(): String? = selectedId

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }
}
