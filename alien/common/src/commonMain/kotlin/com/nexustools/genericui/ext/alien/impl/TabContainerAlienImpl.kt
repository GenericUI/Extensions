package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.nav-tabs` / `.nav-pills` + `.tab-content` container
 * (`TabContainerAlienImpl`) implementing [TabContainerNativeImpl].
 */
class TabContainerAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "tab-container",
    tagName = "div",
    session = session
), TabContainerNativeImpl<Any?> {

    private class TabEntry(
        var title: String,
        var tabImpl: TabNativeImpl<*>?,
        val contentImpl: NativeImpl<*>
    )

    private val entries = mutableListOf<TabEntry>()
    private var selectedIdx: Int = 0
    private var tabPosition: TabPosition = TabPosition.TOP
    private var tabBarStyle: TabBarStyle = TabBarStyle.TABS
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null

    init {
        node.customPaint = { ctx, bounds, style ->
            val barH = 40.coerceAtMost(bounds.height)
            val borderColor = if (style.darkTheme) {
                AlienBootstrapThemeTokens.BORDER_COLOR_DARK
            } else {
                AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            }
            ctx.save()
            ctx.setStrokeColor(borderColor)
            ctx.setLineWidth(1f)
            ctx.beginPath()
            ctx.moveTo(bounds.x.toFloat(), (bounds.y + barH).toFloat())
            ctx.lineTo(bounds.right.toFloat(), (bounds.y + barH).toFloat())
            ctx.stroke()

            var tabX = bounds.x + 4
            for ((idx, entry) in entries.withIndex()) {
                val label = (entry.tabImpl as? TabAlienImpl)?.formatDisplayHeader()?.takeIf { it.isNotBlank() }
                    ?: entry.title
                val tabW = (label.length * 9 + 28).coerceAtLeast(72)
                val tabRect = Rect(tabX, bounds.y + 2, tabW, barH - 2)
                val isSelected = idx == selectedIdx
                if (isSelected) {
                    if (tabBarStyle == TabBarStyle.PILLS) {
                        ctx.setFillColor(AlienBootstrapThemeTokens.PRIMARY)
                        AlienBootstrapPainter.fillRoundedRect(
                            ctx,
                            tabRect.x.toFloat(),
                            tabRect.y.toFloat() + 2f,
                            tabRect.width.toFloat(),
                            (tabRect.height - 4).toFloat(),
                            6f
                        )
                    } else {
                        ctx.setStrokeColor(AlienBootstrapThemeTokens.PRIMARY)
                        ctx.setLineWidth(2f)
                        ctx.beginPath()
                        ctx.moveTo(tabRect.x.toFloat(), (bounds.y + barH - 1).toFloat())
                        ctx.lineTo(tabRect.right.toFloat(), (bounds.y + barH - 1).toFloat())
                        ctx.stroke()
                    }
                }
                val textColor = when {
                    isSelected && tabBarStyle == TabBarStyle.PILLS -> com.nexustools.genericui.style.color.RgbaColor.WHITE
                    isSelected -> AlienBootstrapThemeTokens.PRIMARY
                    else -> style.textColor
                }
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = label,
                    contentBounds = tabRect,
                    style = style.copy(
                        textColor = textColor,
                        fontWeight = if (isSelected) 600 else 400,
                        textAlign = "center"
                    ),
                    verticalCenter = true,
                    maxLines = 1
                )
                tabX += tabW + 4
            }
            ctx.restore()
        }
        node.onPointerDown = { ev ->
            val barH = 40
            if (ev.y <= node.bounds.y + barH) {
                var tabX = node.bounds.x + 4
                for ((idx, entry) in entries.withIndex()) {
                    val label = (entry.tabImpl as? TabAlienImpl)?.formatDisplayHeader()?.takeIf { it.isNotBlank() }
                        ?: entry.title
                    val tabW = (label.length * 9 + 28).coerceAtLeast(72)
                    if (ev.x.toInt() in tabX until (tabX + tabW)) {
                        setSelectedTabIndex(idx, fadeTransition = true)
                        onTabSelectedListener?.invoke(idx)
                        break
                    }
                    tabX += tabW + 4
                }
            }
        }
    }

    override fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int) {
        val title = (tabImpl as? TabAlienImpl)?.titleText.orEmpty()
        val entry = TabEntry(title = title, tabImpl = tabImpl, contentImpl = contentImpl)
        if (index < 0 || index >= entries.size) {
            entries.add(entry)
        } else {
            entries.add(index, entry)
        }
        addChildNative(contentImpl, -1)
        syncActiveTabVisibility()
    }

    override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {
        val entry = TabEntry(title = title, tabImpl = null, contentImpl = contentImpl)
        if (index < 0 || index >= entries.size) {
            entries.add(entry)
        } else {
            entries.add(index, entry)
        }
        addChildNative(contentImpl, -1)
        syncActiveTabVisibility()
    }

    override fun updateTabNative(index: Int, tabImpl: TabNativeImpl<*>) {
        if (index in entries.indices) {
            entries[index].tabImpl = tabImpl
            (tabImpl as? TabAlienImpl)?.let { entries[index].title = it.titleText }
            node.invalidateBounds()
        }
    }

    override fun removeTabNative(index: Int) {
        if (index in entries.indices) {
            val removed = entries.removeAt(index)
            removeChildNative(removed.contentImpl)
            if (selectedIdx >= entries.size) {
                selectedIdx = (entries.size - 1).coerceAtLeast(0)
            }
            syncActiveTabVisibility()
        }
    }

    override fun setTabTitleNative(index: Int, title: String) {
        if (index in entries.indices) {
            entries[index].title = title
            (entries[index].tabImpl as? TabAlienImpl)?.setTitleNative(title)
            node.invalidateBounds()
        }
    }

    override fun setTabPositionNative(position: TabPosition) {
        tabPosition = position
        node.invalidateBounds()
    }

    override fun setTabBarStyleNative(style: TabBarStyle) {
        tabBarStyle = style
        node.invalidateBounds()
    }

    override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) {
        if (entries.isEmpty()) return
        selectedIdx = index.coerceIn(0, entries.lastIndex)
        syncActiveTabVisibility()
    }

    override fun getSelectedTabIndex(): Int = selectedIdx

    override fun setOnTabSelected(listener: ((Int) -> Unit)?) {
        onTabSelectedListener = listener
    }

    override fun setOnTabCloseRequested(listener: ((Int) -> Boolean)?) {
        onTabCloseRequestedListener = listener
    }

    private fun syncActiveTabVisibility() {
        for ((idx, entry) in entries.withIndex()) {
            val isSel = idx == selectedIdx
            entry.contentImpl.setVisible(isSel)
            (entry.tabImpl as? TabAlienImpl)?.setSelectedNative(isSel)
        }
        node.markLayoutDirty()
        node.invalidateBounds()
    }
}
