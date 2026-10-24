package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.image.ImageBuffer

/**
 * Specification for a printable RPG handout card (`DCGame`'s `CharGenView.HandoutCard`),
 * rendered in a multi-up grid (default `2x2` per page) by [PrintableCanvasDocument].
 */
data class CanvasHandoutCardSpec(
    val title: String,
    val categoryTag: String = "",
    val subHeader: String = "",
    val description: String = "",
    val overlayBoxText: String = "",
    val backgroundImage: ImageBuffer? = null,
    val iconImage: ImageBuffer? = null,
    val sideImage: ImageBuffer? = null,
    val overlayBoxImage: ImageBuffer? = null
)
