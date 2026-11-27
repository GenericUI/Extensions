package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
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
        val contentImpl: NativeImpl<*>?,
        var closeRect: Rect? = null,
        var tabRect: Rect = Rect(0, 0, 0, 0)
    )

    private val entries = mutableListOf<TabEntry>()
    private var selectedIdx: Int = 0
    private var tabPosition: TabPosition = TabPosition.TOP
    private var tabBarStyle: TabBarStyle = TabBarStyle.TABS
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null
    private var userPointerDownListener: ((PointerEvent) -> Unit)? = null

    private fun computeEntryTabWidth(entry: TabEntry): Int {
        val tab = entry.tabImpl as? TabAlienImpl
        val body = tab?.formatDisplayHeaderBody()?.takeIf { it.isNotBlank() } ?: entry.title
        val iconW = if (tab?.hasVisualIcon() == true) (tab.iconSizePx.coerceIn(12, 24) + 6) else 0
        val closeW = if (tab != null && tab.closableState && !tab.pinnedState) 18 else 0
        return (body.length * 9 + iconW + closeW + 24).coerceAtLeast(72)
    }

    init {
        node.padding = EdgeInsets(top = 42, left = 4, right = 4, bottom = 4)
        node.customMeasure = { _, _, style ->
            val tabsW = 8 + entries.sumOf { computeEntryTabWidth(it) + 4 }
            var maxChildW = 0
            var maxChildH = 0
            for (child in node.children) {
                if (child.visible) {
                    maxChildW = maxOf(maxChildW, child.measuredSize.width)
                    maxChildH = maxOf(maxChildH, child.measuredSize.height)
                }
            }
            val padW = style.padding.left + style.padding.right
            val padH = style.padding.top + style.padding.bottom
            Size(
                width = maxOf(tabsW, maxChildW + padW).coerceAtLeast(120),
                height = maxOf(42 + maxChildH, padH + maxChildH)
            )
        }
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
                val tab = entry.tabImpl as? TabAlienImpl
                val tabW = computeEntryTabWidth(entry)
                val tabRect = Rect(tabX, bounds.y + 2, tabW, barH - 2)
                entry.tabRect = tabRect
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
                if (tab != null) {
                    entry.closeRect = tab.paintTabHeaderContent(
                        ctx = ctx,
                        bounds = tabRect,
                        textColor = textColor,
                        style = style,
                        isSelected = isSelected
                    )
                } else {
                    entry.closeRect = null
                    AlienBootstrapPainter.paintText(
                        ctx = ctx,
                        text = entry.title,
                        contentBounds = tabRect,
                        style = style.copy(
                            textColor = textColor,
                            fontWeight = if (isSelected) 600 else 400,
                            textAlign = "center"
                        ),
                        verticalCenter = true,
                        maxLines = 1
                    )
                }
                tabX += tabW + 4
            }
            ctx.restore()
        }
        wirePointerDown()
    }

    private fun wirePointerDown() {
        node.onPointerDown = { ev ->
            val barH = 40
            var handledTabInteraction = false
            if (ev.y <= node.bounds.y + barH) {
                var tabX = node.bounds.x + 4
                val px = ev.x.toInt()
                val py = ev.y.toInt()
                for ((idx, entry) in entries.withIndex()) {
                    val tab = entry.tabImpl as? TabAlienImpl
                    val tabW = computeEntryTabWidth(entry)
                    val tabRect = if (entry.tabRect.width > 0) entry.tabRect else Rect(tabX, node.bounds.y + 2, tabW, barH - 2)
                    val closeRect = entry.closeRect ?: if (tab != null && tab.closableState && !tab.pinnedState && tabRect.width > 24) {
                        Rect(tabRect.right - 20, tabRect.y + ((tabRect.height - 16) / 2).coerceAtLeast(0), 16, 16)
                    } else null
                    if (closeRect != null &&
                        px in closeRect.x until closeRect.right &&
                        py in closeRect.y until closeRect.bottom
                    ) {
                        handledTabInteraction = true
                        val listener = tab?.onCloseClickedListener
                        if (listener != null) {
                            listener.invoke()
                        } else if (onTabCloseRequestedListener?.invoke(idx) != false) {
                            removeTabNative(idx)
                        }
                        break
                    }
                    if (px in tabRect.x until tabRect.right) {
                        handledTabInteraction = true
                        setSelectedTabIndex(idx, fadeTransition = true)
                        onTabSelectedListener?.invoke(idx)
                        break
                    }
                    tabX += tabW + 4
                }
            }
            if (!handledTabInteraction) {
                userPointerDownListener?.invoke(ev)
            }
        }
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        userPointerDownListener = listener
        wirePointerDown()
    }

    override fun setPadding(padding: EdgeInsets) {
        super.setPadding(
            EdgeInsets(
                top = maxOf(padding.top, 42),
                right = padding.right,
                bottom = padding.bottom,
                left = padding.left
            )
        )
    }

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        // Tab headers and content panes are mounted exclusively via addTabNative / addButtonTabNative.
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        // Tab headers and content panes are removed exclusively via removeTabNative / clearChildrenNative.
    }

    override fun clearChildrenNative() {
        entries.clear()
        selectedIdx = 0
        super.clearChildrenNative()
    }

    override fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int) {
        val alienTab = tabImpl as? TabAlienImpl
        alienTab?.onHeaderUpdated = {
            node.markLayoutDirty()
            node.invalidateBounds()
        }
        val title = alienTab?.titleText.orEmpty()
        val entry = TabEntry(title = title, tabImpl = tabImpl, contentImpl = contentImpl)
        if (index < 0 || index >= entries.size) {
            entries.add(entry)
        } else {
            entries.add(index, entry)
        }
        super.addChildNative(contentImpl, -1)
        syncActiveTabVisibility()
    }

    override fun addButtonTabNative(tabImpl: TabNativeImpl<*>, index: Int) {
        val alienTab = tabImpl as? TabAlienImpl
        alienTab?.onHeaderUpdated = {
            node.markLayoutDirty()
            node.invalidateBounds()
        }
        val title = alienTab?.titleText.orEmpty()
        val entry = TabEntry(title = title, tabImpl = tabImpl, contentImpl = null)
        if (index < 0 || index >= entries.size) {
            entries.add(entry)
        } else {
            entries.add(index, entry)
        }
        syncActiveTabVisibility()
    }

    override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {
        val entry = TabEntry(title = title, tabImpl = null, contentImpl = contentImpl)
        if (index < 0 || index >= entries.size) {
            entries.add(entry)
        } else {
            entries.add(index, entry)
        }
        super.addChildNative(contentImpl, -1)
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
            removed.contentImpl?.let { super.removeChildNative(it) }
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
        val clamped = index.coerceIn(0, entries.lastIndex)
        if (entries[clamped].contentImpl == null && entries[clamped].tabImpl != null) {
            return
        }
        selectedIdx = clamped
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
            val isSel = (idx == selectedIdx) && entry.contentImpl != null
            entry.contentImpl?.setVisible(isSel)
            (entry.tabImpl as? TabAlienImpl)?.setSelectedNative(isSel)
        }
        node.markLayoutDirty()
        node.invalidateBounds()
    }
}
