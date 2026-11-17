package com.nexustools.genericui.ext.engine.svg

/**
 * Visual SVG vector part bound to [boneName] and sorted by [zOrder] during rendering/export.
 */
data class SvgBonePart(
    val boneName: String,
    val primitive: SvgShapePrimitive,
    val fillHex: String = "#4A90D9",
    val strokeHex: String? = null,
    val strokeWidth: Float = 1.5f,
    val zOrder: Int = 0
)
