package com.nexustools.genericui.ext.gamepad

/**
 * Immutable frame snapshot of one controller slot (`playerIndex` `0..3`), containing canonical
 * Xbox-layout buttons/axes, edge transitions (`justPressed` / `justReleased`), 6-axis IMU motion,
 * and controller-family-specific telemetry.
 */
data class GamepadFrameSnapshot(
    val playerIndex: Int = 0,
    val connected: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = "",
    val vendorId: Int = 0,
    val productId: Int = 0,
    val profile: ControllerHardwareProfile = ControllerHardwareProfile.GENERIC_XINPUT,
    val batteryLevel: Float? = null,
    val pressedButtonsMask: Int = 0,
    val justPressedButtonsMask: Int = 0,
    val justReleasedButtonsMask: Int = 0,
    val leftStick: GamepadAxis2D = GamepadAxis2D.ZERO,
    val rightStick: GamepadAxis2D = GamepadAxis2D.ZERO,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val motion: SixAxisMotionState = SixAxisMotionState.UNAVAILABLE,
    val playStation: PlayStationControllerFeatures? = null,
    val switchFeatures: SwitchControllerFeatures? = null,
    val xbox: XboxControllerFeatures? = null
) {
    val family: ControllerFamily
        get() = profile.family

    fun isButtonDown(button: GamepadButton): Boolean =
        (pressedButtonsMask and button.bitMask) != 0

    fun isButtonJustPressed(button: GamepadButton): Boolean =
        (justPressedButtonsMask and button.bitMask) != 0

    fun isButtonJustReleased(button: GamepadButton): Boolean =
        (justReleasedButtonsMask and button.bitMask) != 0

    val dpadVector: GamepadAxis2D
        get() {
            var x = 0f
            var y = 0f
            if (isButtonDown(GamepadButton.DPAD_LEFT)) x -= 1f
            if (isButtonDown(GamepadButton.DPAD_RIGHT)) x += 1f
            if (isButtonDown(GamepadButton.DPAD_DOWN)) y -= 1f
            if (isButtonDown(GamepadButton.DPAD_UP)) y += 1f
            return GamepadAxis2D(x, y)
        }

    fun axisValue(axis: GamepadAxis): Float = when (axis) {
        GamepadAxis.LEFT_STICK_X -> leftStick.x
        GamepadAxis.LEFT_STICK_Y -> leftStick.y
        GamepadAxis.RIGHT_STICK_X -> rightStick.x
        GamepadAxis.RIGHT_STICK_Y -> rightStick.y
        GamepadAxis.LEFT_TRIGGER -> leftTrigger
        GamepadAxis.RIGHT_TRIGGER -> rightTrigger
    }

    fun resolveButtonLabel(button: GamepadButton): String =
        ControllerButtonLabels.resolveLabel(
            button = button,
            profile = profile,
            usePositionalSwitchLayout = switchFeatures?.usePositionalActionButtons ?: true
        )

    fun withTransitionFrom(previous: GamepadFrameSnapshot): GamepadFrameSnapshot {
        val prevMask = if (previous.connected) previous.pressedButtonsMask else 0
        val curMask = if (connected) pressedButtonsMask else 0
        return copy(
            justPressedButtonsMask = curMask and prevMask.inv(),
            justReleasedButtonsMask = prevMask and curMask.inv()
        )
    }

    companion object {
        fun disconnected(playerIndex: Int): GamepadFrameSnapshot =
            GamepadFrameSnapshot(playerIndex = playerIndex, connected = false)
    }
}
