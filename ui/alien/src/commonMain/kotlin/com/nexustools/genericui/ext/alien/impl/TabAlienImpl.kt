package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.nav-link` tab header (`TabAlienImpl`) implementing [TabNativeImpl].
 */
class TabAlienImpl(
    initialClosable: Boolean = false,
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "nav-link",
    tagName = "button",
    session = session
), TabNativeImpl<Any?> {

    var tabId: String = ""
        private set
    var titleText: String = ""
        private set
    var iconText: String? = null
        private set
    var closableState: Boolean = initialClosable
        private set
    var dirtyState: Boolean = false
        private set
    var pinnedState: Boolean = false
        private set
    var editableState: Boolean = false
        private set
    var badgeText: String? = null
        private set
    var badgeVariantCss: String? = null
        private set
    var selectedState: Boolean = false
        private set
    var customHeaderImpl: NativeImpl<*>? = null
        private set
    var onCloseClickedListener: (() -> Unit)? = null
        private set
    var onTitleEditedListener: ((String) -> Unit)? = null
        private set

    init {
        node.customMeasure = { _, _, style ->
            val label = formatDisplayHeader()
            val textW = (label.length * style.fontSizePx * 0.56f).toInt().coerceAtLeast(48)
            Size(width = textW + 24, height = 38)
        }
        node.customPaint = { ctx, bounds, style ->
            if (selectedState) {
                ctx.setStrokeColor(AlienBootstrapThemeTokens.PRIMARY)
                ctx.setLineWidth(2f)
                ctx.beginPath()
                ctx.moveTo(bounds.x.toFloat(), bounds.bottom.toFloat() - 1f)
                ctx.lineTo(bounds.right.toFloat(), bounds.bottom.toFloat() - 1f)
                ctx.stroke()
            }
            val inner = Rect(bounds.x + 8, bounds.y, (bounds.width - 16).coerceAtLeast(0), bounds.height)
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = formatDisplayHeader(),
                contentBounds = inner,
                style = style.copy(
                    textColor = if (selectedState) AlienBootstrapThemeTokens.PRIMARY else style.textColor,
                    fontWeight = if (selectedState) 600 else 400,
                    textAlign = "center"
                ),
                verticalCenter = true,
                maxLines = 1
            )
        }
    }

    fun formatDisplayHeader(): String {
        val sb = StringBuilder()
        if (pinnedState) sb.append("📌 ")
        if (!iconText.isNullOrBlank()) sb.append(iconText).append(' ')
        sb.append(titleText)
        if (dirtyState) sb.append(" •")
        if (!badgeText.isNullOrBlank()) sb.append(" [").append(badgeText).append(']')
        if (closableState && !pinnedState) sb.append(" ×")
        return sb.toString()
    }

    override fun setTabIdNative(id: String) {
        tabId = id
    }

    override fun setTitleNative(title: String) {
        titleText = title
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setIconNative(icon: String?) {
        iconText = icon
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setTooltipNative(tooltip: String?) {
        setTooltipText(tooltip)
    }

    override fun setClosableNative(closable: Boolean) {
        closableState = closable
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setDirtyNative(dirty: Boolean) {
        dirtyState = dirty
        node.invalidateBounds()
    }

    override fun setPinnedNative(pinned: Boolean) {
        pinnedState = pinned
        node.invalidateBounds()
    }

    override fun setEditableNative(editable: Boolean) {
        editableState = editable
    }

    override fun setBadgeNative(badgeText: String?, variantCss: String?) {
        this.badgeText = badgeText
        this.badgeVariantCss = variantCss
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setSelectedNative(selected: Boolean) {
        selectedState = selected
        node.invalidateBounds()
    }

    override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {
        customHeaderImpl = customImpl
        if (customImpl != null) {
            addChildNative(customImpl, -1)
        }
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setOnCloseClicked(listener: (() -> Unit)?) {
        onCloseClickedListener = listener
    }

    override fun setOnTitleEdited(listener: ((String) -> Unit)?) {
        onTitleEditedListener = listener
    }
}
