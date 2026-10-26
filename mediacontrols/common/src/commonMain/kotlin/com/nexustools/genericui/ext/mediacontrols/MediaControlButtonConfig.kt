package com.nexustools.genericui.ext.mediacontrols

/**
 * Configuration of enabled transport buttons and localized tooltips for system transport controls
 * and Windows taskbar thumbnail preview toolbars (`ITaskbarList3`).
 */
data class MediaControlButtonConfig(
    val playPauseEnabled: Boolean = true,
    val previousEnabled: Boolean = true,
    val nextEnabled: Boolean = true,
    val stopEnabled: Boolean = false,
    val seekEnabled: Boolean = true,
    val previousTooltip: String = "Previous",
    val playTooltip: String = "Play",
    val pauseTooltip: String = "Pause",
    val nextTooltip: String = "Next",
    val stopTooltip: String = "Stop"
)
