package com.nexustools.genericui.ext.overlay

/**
 * Describes a selectable media or overlay track (such as an audio or subtitle track).
 */
data class OverlayTrackItem(
    val id: String,
    val label: String,
    val language: String = "",
    val selected: Boolean = false
)
