package com.nexustools.genericui.ext.gamepad

/**
 * Lighting pulse pattern for the illuminated Home ring LED on Nintendo Switch Pro / Right Joy-Con.
 */
data class SwitchHomeLedPattern(
    val baseIntensity: Float = 0f,
    val maxIntensity: Float = 1f,
    val pulseRateHz: Float = 1f,
    val cycleCount: Int = 0
) {
    fun clamped(): SwitchHomeLedPattern = SwitchHomeLedPattern(
        baseIntensity = baseIntensity.coerceIn(0f, 1f),
        maxIntensity = maxIntensity.coerceIn(0f, 1f),
        pulseRateHz = pulseRateHz.coerceIn(0f, 16f),
        cycleCount = cycleCount.coerceIn(0, 15)
    )

    companion object {
        val OFF: SwitchHomeLedPattern = SwitchHomeLedPattern(0f, 0f, 0f, 0)
        val SOLID_ON: SwitchHomeLedPattern = SwitchHomeLedPattern(1f, 1f, 0f, 0)
        val SLOW_BREATHE: SwitchHomeLedPattern = SwitchHomeLedPattern(0.15f, 1f, 0.8f, 0)
    }
}
