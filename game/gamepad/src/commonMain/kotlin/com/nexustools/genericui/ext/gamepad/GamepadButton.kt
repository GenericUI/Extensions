package com.nexustools.genericui.ext.gamepad

/**
 * Canonical 18 digital controller buttons normalized to a standard Xbox-style layout
 * regardless of the underlying vendor hardware (DualShock/DualSense, Switch Pro/Joy-Con,
 * Xbox Series/Elite, 8BitDo, Logitech, or generic XInput/HID).
 */
enum class GamepadButton(
    val standardIndex: Int,
    val defaultXboxLabel: String
) {
    A(0, "A"),
    B(1, "B"),
    X(2, "X"),
    Y(3, "Y"),
    LEFT_BUMPER(4, "LB"),
    RIGHT_BUMPER(5, "RB"),
    LEFT_TRIGGER_BUTTON(6, "LT"),
    RIGHT_TRIGGER_BUTTON(7, "RT"),
    VIEW(8, "View"),
    MENU(9, "Menu"),
    LEFT_STICK(10, "LS"),
    RIGHT_STICK(11, "RS"),
    DPAD_UP(12, "D-Up"),
    DPAD_DOWN(13, "D-Down"),
    DPAD_LEFT(14, "D-Left"),
    DPAD_RIGHT(15, "D-Right"),
    GUIDE(16, "Guide"),
    SHARE(17, "Share");

    val bitMask: Int
        get() = 1 shl standardIndex

    companion object {
        const val STANDARD_BUTTON_COUNT: Int = 18
    }
}
