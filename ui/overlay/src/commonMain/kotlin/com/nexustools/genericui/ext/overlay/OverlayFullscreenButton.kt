package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import kotlin.jvm.JvmName

/**
 * Stateful fullscreen toggle [OverlayControlButton] switching between
 * `R.drawable.ic_fullscreen_enter` (`isFullscreen == false`, 4 outward diagonal arrows) and
 * `R.drawable.ic_fullscreen_exit` (`isFullscreen == true`, 4 inward diagonal arrows).
 */
open class OverlayFullscreenButton(
    isFullscreen: Boolean = false,
    variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
    accentColor: RgbaColor = DEFAULT_ACCENT_COLOR,
    backdropFilter: CssFilter? = null,
    tooltipText: String? = null,
    var onFullscreenToggle: ((Boolean) -> Unit)? = null
) : OverlayControlButton(
    iconType = if (isFullscreen) OverlayIconType.FULLSCREEN_EXIT else OverlayIconType.FULLSCREEN_ENTER,
    variant = variant,
    accentColor = accentColor,
    backdropFilter = backdropFilter,
    tooltipText = tooltipText
) {

    @get:JvmName("isFullscreenProperty")
    @set:JvmName("setFullscreenProperty")
    var isFullscreen: Boolean = isFullscreen
        set(value) {
            if (field != value) {
                field = value
                syncFullscreenIcon()
            }
        }

    init {
        addClass("gui-overlay-control-btn")
        syncFullscreenIcon()
        onClick = {
            toggle()
        }
    }

    fun toggle(): Boolean {
        val next = !isFullscreen
        isFullscreen = next
        onFullscreenToggle?.invoke(next)
        return next
    }

    private fun syncFullscreenIcon() {
        val targetIcon = if (isFullscreen) {
            OverlayIconType.FULLSCREEN_EXIT
        } else {
            OverlayIconType.FULLSCREEN_ENTER
        }
        if (iconType != targetIcon) {
            iconType = targetIcon
        }
        setAttribute("aria-pressed", isFullscreen.toString())
        setAttribute("data-fullscreen", isFullscreen.toString())
    }
}
