package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.shell.SplitPaneNativeImpl

/**
 * Self-rendered resizable split pane (`SplitPaneAlienImpl`) implementing [SplitPaneNativeImpl].
 */
class SplitPaneAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "split-pane",
    tagName = "div",
    session = session
), SplitPaneNativeImpl<Any?> {

    private var orientationValue: Orientation = Orientation.HORIZONTAL
    private var dividerRatio: Float = 0.5f

    init {
        node.attributes["data-orientation"] = orientationValue.name
        node.attributes["data-divider-ratio"] = dividerRatio.toString()
        node.customPaint = { ctx, bounds, style ->
            val handleColor = if (style.darkTheme) {
                AlienBootstrapThemeTokens.BORDER_COLOR_DARK
            } else {
                AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            }
            ctx.save()
            ctx.setFillColor(handleColor)
            if (orientationValue == Orientation.HORIZONTAL) {
                val splitX = bounds.x + ((bounds.width - 6) * dividerRatio)
                ctx.fillRect(splitX, bounds.y.toFloat(), 6f, bounds.height.toFloat())
            } else {
                val splitY = bounds.y + ((bounds.height - 6) * dividerRatio)
                ctx.fillRect(bounds.x.toFloat(), splitY, bounds.width.toFloat(), 6f)
            }
            ctx.restore()
        }
    }

    override fun setOrientation(orientation: Orientation) {
        if (orientationValue != orientation) {
            orientationValue = orientation
            node.attributes["data-orientation"] = orientation.name
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getOrientation(): Orientation = orientationValue

    override fun setDividerPosition(ratio: Float) {
        val clamped = ratio.coerceIn(0.05f, 0.95f)
        if (dividerRatio != clamped) {
            dividerRatio = clamped
            node.attributes["data-divider-ratio"] = clamped.toString()
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getDividerPosition(): Float = dividerRatio
}
