package com.nexustools.genericui.ext.engine.ui

import com.nexustools.genericui.component.content.ImageView
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.engine.svg.Skeleton2D
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.threading.GenericUiThreading
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Pure `:common` UI element synchronization module binding a [SceneEntity]'s spatial placement,
 * size, visibility, opacity, background color, and optional SVG [Skeleton2D] directly onto any
 * GenericUI [AbstractComponent] with automatic UI-thread marshaling via [GenericUiThreading.runOnUi].
 */
class UiElementBindingModule(
    val component: AbstractComponent,
    var widthPx: Int? = component.widthPx,
    var heightPx: Int? = component.heightPx,
    var backgroundColor: RgbaColor? = null,
    var opacity: Float = 1.0f,
    var useMarginPositioning: Boolean = false,
    var syncRotationAndScale: Boolean = true,
    var syncVisibility: Boolean = true,
    var skeleton2D: Skeleton2D? = null,
    var svgViewportWidth: Int = 256,
    var svgViewportHeight: Int = 256
) : EntityBehavior() {

    var uiSyncCount: Long = 0L
        private set

    var lastSyncExecutedOnUiThread: Boolean = false
        private set

    override fun onAttach(owner: SceneEntity) {
        syncNow()
    }

    override fun onLateTick(deltaSeconds: Float) {
        syncNow()
    }

    override fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        syncNow()
    }

    fun syncNow() {
        val owner = entity ?: return
        val x = owner.transform.position.x
        val y = owner.transform.position.y
        val rotDeg = (owner.transform.rotation2DRad * (180.0 / PI)).toFloat()
        val sx = owner.transform.scale.x
        val sy = owner.transform.scale.y
        val active = owner.isActive
        val targetW = widthPx
        val targetH = heightPx
        val targetBg = backgroundColor
        val targetOpacity = opacity.coerceIn(0f, 1f)
        val svgDataUri = skeleton2D?.let { skel ->
            val xml = skel.exportSvgMarkup(svgViewportWidth, svgViewportHeight)
            "data:image/svg+xml;utf8," + encodeSvgUriFragment(xml)
        }

        val applyBlock = {
            lastSyncExecutedOnUiThread = GenericUiThreading.isOnUiThread()
            uiSyncCount++

            val style = component.getStyle()
            if (useMarginPositioning) {
                component.setMargin(EdgeInsets(top = y.roundToInt(), left = x.roundToInt()))
            } else {
                style.setPosition(CssPosition.ABSOLUTE)
                style.setLeft(CssLength.px(x))
                style.setTop(CssLength.px(y))
            }

            if (targetW != null) {
                component.widthPx = targetW
                style.setWidthPx(targetW)
            }
            if (targetH != null) {
                component.heightPx = targetH
                style.setHeightPx(targetH)
            }
            if (targetBg != null) {
                style.setBackgroundColor(targetBg)
            }
            if (syncRotationAndScale) {
                style.setTransform(
                    CssTransform2D(
                        translateX = 0f,
                        translateY = 0f,
                        scaleX = sx,
                        scaleY = sy,
                        rotateDeg = rotDeg
                    )
                )
            }
            component.opacity = targetOpacity
            if (syncVisibility) {
                component.isVisible = active
                style.setVisibility(if (active) CssVisibility.VISIBLE else CssVisibility.HIDDEN)
            }
            if (svgDataUri != null && component is ImageView) {
                component.sourceUrl = svgDataUri
            }
        }

        if (GenericUiThreading.isSplitThreadingEnabled && !GenericUiThreading.isOnUiThread()) {
            GenericUiThreading.runOnUi(applyBlock)
        } else {
            applyBlock()
        }
    }

    private fun encodeSvgUriFragment(svg: String): String {
        val sb = StringBuilder(svg.length + 32)
        for (ch in svg) {
            when (ch) {
                '%' -> sb.append("%25")
                '#' -> sb.append("%23")
                '<' -> sb.append("%3C")
                '>' -> sb.append("%3E")
                '"' -> sb.append("%22")
                ' ' -> sb.append("%20")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
