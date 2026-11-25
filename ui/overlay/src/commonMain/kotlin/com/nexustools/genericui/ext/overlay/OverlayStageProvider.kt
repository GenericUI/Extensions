package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.AbstractComponent

/**
 * Decoupled stage contract implemented by media/stage fragments (such as `VideoFragment`)
 * so sibling extensions like `:extensions:ui:pip` can integrate with stage playback, overlays,
 * title, and aspect ratio without a compile-time dependency on `:extensions:media:media`.
 */
interface OverlayStageProvider {
    val stageOverlayHost: OverlayHost
    val stageTargetComponent: AbstractComponent
    val isStagePlaying: Boolean
    val stageTitle: String
    val stageAspectRatio: Pair<Int, Int>
    fun toggleStagePlayPause()
    fun pauseStagePlayback()
    fun toggleStageFullscreen()
}
