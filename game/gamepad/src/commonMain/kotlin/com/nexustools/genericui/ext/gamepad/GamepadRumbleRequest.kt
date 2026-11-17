package com.nexustools.genericui.ext.gamepad

/**
 * Dual-rumble + optional Xbox impulse-trigger vibration command (`0f..1f` normalized motor intensities).
 */
data class GamepadRumbleRequest(
    val lowFrequency: Float = 0f,
    val highFrequency: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val durationMs: Int = 150
) {
    fun clamped(): GamepadRumbleRequest = GamepadRumbleRequest(
        lowFrequency = lowFrequency.coerceIn(0f, 1f),
        highFrequency = highFrequency.coerceIn(0f, 1f),
        leftTrigger = leftTrigger.coerceIn(0f, 1f),
        rightTrigger = rightTrigger.coerceIn(0f, 1f),
        durationMs = durationMs.coerceIn(0, 60_000)
    )

    companion object {
        val STOP: GamepadRumbleRequest = GamepadRumbleRequest(0f, 0f, 0f, 0f, 0)
    }
}
