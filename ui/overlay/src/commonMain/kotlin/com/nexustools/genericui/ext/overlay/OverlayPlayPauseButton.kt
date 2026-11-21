package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter

/**
 * Stateful play/pause [OverlayControlButton] switching between `R.drawable.ic_play`
 * and `R.drawable.ic_pause` via [isPlaying].
 */
open class OverlayPlayPauseButton(
    isPlaying: Boolean = false,
    variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
    accentColor: RgbaColor = DEFAULT_ACCENT_COLOR,
    backdropFilter: CssFilter? = null,
    tooltipText: String? = null,
    var onPlayPauseToggle: (() -> Unit)? = null
) : OverlayControlButton(
    iconType = if (isPlaying) OverlayIconType.PAUSE else OverlayIconType.PLAY,
    variant = variant,
    accentColor = accentColor,
    backdropFilter = backdropFilter,
    tooltipText = tooltipText
) {

    var isPlaying: Boolean = isPlaying
        set(value) {
            if (field != value) {
                field = value
                syncPlayPauseIcon()
            }
        }

    init {
        syncPlayPauseIcon()
        onClick = {
            onPlayPauseToggle?.invoke()
        }
    }

    private fun syncPlayPauseIcon() {
        val targetIcon = if (isPlaying) OverlayIconType.PAUSE else OverlayIconType.PLAY
        if (iconType != targetIcon) {
            iconType = targetIcon
        }
        setAttribute("aria-pressed", isPlaying.toString())
        setAttribute("data-playing", isPlaying.toString())
    }
}
