package com.nexustools.genericui.ext.gamepad

/**
 * Force-feedback trigger effect profiles supported on Sony DualSense (`L2`/`R2`) controllers.
 */
sealed interface AdaptiveTriggerEffect {
    data object Off : AdaptiveTriggerEffect

    data class ContinuousResistance(
        val startPosition: Float,
        val force: Float
    ) : AdaptiveTriggerEffect

    data class SectionResistance(
        val startPosition: Float,
        val endPosition: Float,
        val force: Float
    ) : AdaptiveTriggerEffect

    data class VibrationPulse(
        val startPosition: Float,
        val amplitude: Float,
        val frequencyHz: Float
    ) : AdaptiveTriggerEffect
}
