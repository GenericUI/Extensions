package com.nexustools.genericui.ext.overlay

/**
 * Placement of the seek timeline inside [YoutubePlayerOverlay.bottomBar] relative to [YoutubePlayerOverlay.controlsRow].
 */
enum class YoutubeTimelinePlacement {
    /** Timeline sits above the controls row (standard Desktop/Web & wide layout). */
    ABOVE_CONTROLS,

    /** Timeline sits below the controls row (mobile & compact portrait layout). */
    BELOW_CONTROLS
}
