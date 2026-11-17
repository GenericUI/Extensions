package com.nexustools.genericui.ext.gamepad

/**
 * Hardware state unique to Nintendo Switch Pro Controller and Joy-Con pairs:
 * Capture button state, 1..4 player indicator LEDs, Home LED pattern, NFC status,
 * HD Rumble state, and positional vs printed action-button layout preference.
 */
data class SwitchControllerFeatures(
    val isCapturePressed: Boolean = false,
    val playerLedMask: Int = 0,
    val homeLedPattern: SwitchHomeLedPattern = SwitchHomeLedPattern.OFF,
    val nfcStatus: SwitchNfcStatus = SwitchNfcStatus.UNAVAILABLE,
    val usePositionalActionButtons: Boolean = true,
    val lastHdRumble: SwitchHdRumbleCommand? = null
)
