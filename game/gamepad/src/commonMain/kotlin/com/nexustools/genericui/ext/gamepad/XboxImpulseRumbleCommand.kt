package com.nexustools.genericui.ext.gamepad

/**
 * Quad-motor vibration command for Xbox One / Series / Elite controllers (grip rumble motors plus
 * independent left and right impulse-trigger motors).
 */
data class XboxImpulseRumbleCommand(
    val lowFrequencyLeft: Float = 0f,
    val highFrequencyRight: Float = 0f,
    val leftTriggerMotor: Float = 0f,
    val rightTriggerMotor: Float = 0f,
    val durationMs: Int = 150
) {
    fun toGamepadRumbleRequest(): GamepadRumbleRequest = GamepadRumbleRequest(
        lowFrequency = lowFrequencyLeft,
        highFrequency = highFrequencyRight,
        leftTrigger = leftTriggerMotor,
        rightTrigger = rightTriggerMotor,
        durationMs = durationMs
    ).clamped()

    companion object {
        val STOP: XboxImpulseRumbleCommand = XboxImpulseRumbleCommand(0f, 0f, 0f, 0f, 0)
    }
}
