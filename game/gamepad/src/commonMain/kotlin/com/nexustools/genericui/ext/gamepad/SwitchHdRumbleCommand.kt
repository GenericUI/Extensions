package com.nexustools.genericui.ext.gamepad

/**
 * Dual-band Linear Resonant Actuator (HD Rumble) command for Nintendo Switch Pro / Joy-Con controllers.
 */
data class SwitchHdRumbleCommand(
    val leftHighFreqHz: Float = 320f,
    val leftHighAmp: Float = 0f,
    val leftLowFreqHz: Float = 160f,
    val leftLowAmp: Float = 0f,
    val rightHighFreqHz: Float = 320f,
    val rightHighAmp: Float = 0f,
    val rightLowFreqHz: Float = 160f,
    val rightLowAmp: Float = 0f,
    val durationMs: Int = 150
) {
    fun clamped(): SwitchHdRumbleCommand = SwitchHdRumbleCommand(
        leftHighFreqHz = leftHighFreqHz.coerceIn(40f, 1252f),
        leftHighAmp = leftHighAmp.coerceIn(0f, 1f),
        leftLowFreqHz = leftLowFreqHz.coerceIn(40f, 1252f),
        leftLowAmp = leftLowAmp.coerceIn(0f, 1f),
        rightHighFreqHz = rightHighFreqHz.coerceIn(40f, 1252f),
        rightHighAmp = rightHighAmp.coerceIn(0f, 1f),
        rightLowFreqHz = rightLowFreqHz.coerceIn(40f, 1252f),
        rightLowAmp = rightLowAmp.coerceIn(0f, 1f),
        durationMs = durationMs.coerceIn(0, 60_000)
    )

    companion object {
        val STOP: SwitchHdRumbleCommand = SwitchHdRumbleCommand(durationMs = 0)
    }
}
