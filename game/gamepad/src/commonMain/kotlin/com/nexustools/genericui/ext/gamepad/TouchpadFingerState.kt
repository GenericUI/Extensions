package com.nexustools.genericui.ext.gamepad

/**
 * Single capacitive contact point reported by a DualShock 4 or DualSense touchpad (`x`, `y` in `0f..1f`).
 */
data class TouchpadFingerState(
    val touchId: Int = -1,
    val active: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f
)
