package com.nexustools.genericui.ext.gamepad

/**
 * Canonical 6 analog axes exposed on normalized game controllers (`-1..1` for sticks with
 * `+Y` pointing upward, `0..1` for triggers).
 */
enum class GamepadAxis(
    val standardIndex: Int
) {
    LEFT_STICK_X(0),
    LEFT_STICK_Y(1),
    RIGHT_STICK_X(2),
    RIGHT_STICK_Y(3),
    LEFT_TRIGGER(4),
    RIGHT_TRIGGER(5);

    companion object {
        const val STANDARD_AXIS_COUNT: Int = 6
    }
}
