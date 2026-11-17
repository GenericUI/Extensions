package com.nexustools.genericui.ext.gamepad

/**
 * Hardware state unique to PlayStation DualShock 4 and DualSense controllers:
 * 2-point capacitive touchpad, RGB lightbar color, player indicator LED mask, mute LED,
 * and DualSense adaptive trigger states.
 */
data class PlayStationControllerFeatures(
    val touchpad: TouchpadState = TouchpadState(),
    val lightbarR: Int = 0,
    val lightbarG: Int = 0,
    val lightbarB: Int = 255,
    val playerIndicatorLedMask: Int = 0,
    val muteLedState: MuteLedState = MuteLedState.OFF,
    val leftAdaptiveTrigger: AdaptiveTriggerEffect = AdaptiveTriggerEffect.Off,
    val rightAdaptiveTrigger: AdaptiveTriggerEffect = AdaptiveTriggerEffect.Off
)
