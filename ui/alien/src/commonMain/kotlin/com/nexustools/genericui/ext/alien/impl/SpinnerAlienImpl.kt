package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.ext.alien.AlienBootstrapThemeTokens
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import kotlin.math.min

/**
 * Self-rendered Bootstrap 5.3 `.spinner-border` and `.spinner-grow` indicator (`SpinnerAlienImpl`)
 * implementing [SpinnerNativeImpl].
 */
class SpinnerAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "spinner-border",
    tagName = "div",
    session = session
), SpinnerNativeImpl<Any?> {

    private var growStyleState: Boolean = false
    private var labelValue: String? = null

    init {
        node.hasContinuousAnimation = true
        node.customMeasure = { _, _, _ ->
            val dim = if (node.styleSnapshot.size == BootstrapSize.SMALL) 16 else 32
            Size(dim, dim)
        }
        node.customPaint = { ctx, bounds, _ ->
            val color = AlienBootstrapThemeTokens.baseColorForType(node.styleSnapshot.type ?: BootstrapType.PRIMARY)
            val cx = bounds.x + bounds.width * 0.5f
            val cy = bounds.y + bounds.height * 0.5f
            val radius = (min(bounds.width, bounds.height) * 0.5f - 3f).coerceAtLeast(4f)
            val phase = node.transitionAnimator.continuousAnimationPhaseRad

            ctx.save()
            if (growStyleState) {
                ctx.setFillColor(color.withAlpha(0.75f))
                ctx.beginPath()
                ctx.arc(cx, cy, radius, 0f, 6.2831855f)
                ctx.fill()
            } else {
                ctx.setStrokeColor(color)
                ctx.setLineWidth(3f)
                ctx.beginPath()
                ctx.arc(cx, cy, radius, phase, phase + 4.712389f)
                ctx.stroke()
            }
            ctx.restore()
        }
    }

    override fun setGrowStyle(grow: Boolean) {
        growStyleState = grow
        node.invalidateBounds()
    }

    override fun setLabel(label: String?) {
        labelValue = label
    }
}
