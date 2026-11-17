package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.component.dock.DockHeaderAction
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.window.TitleBar

/**
 * Self-rendered Bootstrap 5.3 `.card` dock panel (`DockPanelAlienImpl`) implementing [DockPanelNativeImpl].
 */
class DockPanelAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "card",
    tagName = "section",
    session = session
), DockPanelNativeImpl<Any?> {

    private var titleValue: String = ""
    private var iconValue: String? = null
    private var collapsedState: Boolean = false
    private var onCollapsedChangedListener: ((Boolean) -> Unit)? = null
    private var floatingState: Boolean = false
    private var headerActionsList: List<DockHeaderAction> = emptyList()
    private var titleBarInstructionsValue: TitleBar? = null

    init {
        node.customPaint = { ctx, bounds, style ->
            val headerH = 34.coerceAtMost(bounds.height)
            val headerBg = if (style.darkTheme) {
                AlienBootstrapThemeTokens.TERTIARY_BG_DARK
            } else {
                AlienBootstrapThemeTokens.TERTIARY_BG_LIGHT
            }
            ctx.save()
            ctx.setFillColor(headerBg)
            AlienBootstrapPainter.fillRoundedRect(
                ctx,
                bounds.x.toFloat(),
                bounds.y.toFloat(),
                bounds.width.toFloat(),
                headerH.toFloat(),
                style.borderRadiusPx
            )
            ctx.restore()

            val displayTitle = if (!iconValue.isNullOrBlank()) "$iconValue $titleValue" else titleValue
            val titleRect = Rect(bounds.x + 12, bounds.y, (bounds.width - 24).coerceAtLeast(0), headerH)
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = displayTitle,
                contentBounds = titleRect,
                style = style.copy(fontSizePx = 14f, fontWeight = 600),
                verticalCenter = true,
                maxLines = 1
            )
        }
    }

    override fun setTitle(title: String) {
        if (titleValue != title) {
            titleValue = title
            node.invalidateBounds()
        }
    }

    override fun getTitle(): String = titleValue

    override fun setIcon(icon: String?) {
        iconValue = icon
        node.invalidateBounds()
    }

    override fun setCollapsed(collapsed: Boolean) {
        if (collapsedState != collapsed) {
            collapsedState = collapsed
            onCollapsedChangedListener?.invoke(collapsed)
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun isCollapsed(): Boolean = collapsedState

    override fun setOnCollapsedChanged(listener: ((Boolean) -> Unit)?) {
        onCollapsedChangedListener = listener
    }

    override fun setFloating(floating: Boolean) {
        floatingState = floating
        node.invalidateBounds()
    }

    override fun isFloating(): Boolean = floatingState

    override fun setHeaderActions(actions: List<DockHeaderAction>) {
        headerActionsList = actions.toList()
        node.invalidateBounds()
    }

    override fun getHeaderActions(): List<DockHeaderAction> = headerActionsList

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        titleBarInstructionsValue = titleBar
        node.invalidateBounds()
    }

    override fun getTitleBarInstructions(): TitleBar? = titleBarInstructionsValue
}
