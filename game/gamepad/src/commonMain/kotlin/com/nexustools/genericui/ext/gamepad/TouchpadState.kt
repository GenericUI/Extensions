package com.nexustools.genericui.ext.gamepad

/**
 * DualShock 4 / DualSense 2-point capacitive touchpad snapshot with frame-to-frame swipe delta helpers.
 */
data class TouchpadState(
    val finger1: TouchpadFingerState = TouchpadFingerState(),
    val finger2: TouchpadFingerState = TouchpadFingerState(),
    val isTouchpadClicked: Boolean = false,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f
) {
    val activeFingerCount: Int
        get() = (if (finger1.active) 1 else 0) + (if (finger2.active) 1 else 0)

    val hasAnyTouch: Boolean
        get() = finger1.active || finger2.active

    fun computeSwipeDelta(previous: TouchpadState): GamepadAxis2D {
        if (!finger1.active || !previous.finger1.active) return GamepadAxis2D.ZERO
        if (finger1.touchId != -1 && previous.finger1.touchId != -1 && finger1.touchId != previous.finger1.touchId) {
            return GamepadAxis2D.ZERO
        }
        return GamepadAxis2D(
            x = finger1.x - previous.finger1.x,
            y = finger1.y - previous.finger1.y
        )
    }
}
