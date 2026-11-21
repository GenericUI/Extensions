package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.ext.overlay.res.R
import com.nexustools.genericui.resources.ResourceID

/**
 * Resolves `:extensions:ui:overlay`'s scoped [R.drawable] vector icon [ResourceID]s for [OverlayIconType].
 */
object OverlayIcons {

    fun resourceFor(type: OverlayIconType): ResourceID = when (type) {
        OverlayIconType.PLAY -> R.drawable.ic_play
        OverlayIconType.PAUSE -> R.drawable.ic_pause
        OverlayIconType.REWIND -> R.drawable.ic_rewind
        OverlayIconType.FAST_FORWARD -> R.drawable.ic_fast_forward
        OverlayIconType.STOP -> R.drawable.ic_stop
        OverlayIconType.NEXT -> R.drawable.ic_next
        OverlayIconType.PREVIOUS -> R.drawable.ic_previous
        OverlayIconType.FULLSCREEN_ENTER -> R.drawable.ic_fullscreen_enter
        OverlayIconType.FULLSCREEN_EXIT -> R.drawable.ic_fullscreen_exit
    }

    fun uriFor(type: OverlayIconType): String =
        resourceFor(type).toResourceUri()
}
