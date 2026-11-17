package com.nexustools.genericui.ext.gamepad

import kotlin.math.hypot

/**
 * Lightweight 2D floating-point axis/coordinate pair used for thumbsticks and touchpad deltas
 * without pulling in full graphics or physics math modules.
 */
data class GamepadAxis2D(
    val x: Float = 0f,
    val y: Float = 0f
) {
    val length: Float
        get() = hypot(x, y)

    val lengthSquared: Float
        get() = x * x + y * y

    fun normalized(): GamepadAxis2D {
        val len = length
        return if (len > 1e-6f) GamepadAxis2D(x / len, y / len) else ZERO
    }

    companion object {
        val ZERO: GamepadAxis2D = GamepadAxis2D(0f, 0f)
    }
}
