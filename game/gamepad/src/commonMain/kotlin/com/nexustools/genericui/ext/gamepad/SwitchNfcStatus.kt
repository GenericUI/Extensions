package com.nexustools.genericui.ext.gamepad

/**
 * Operational state of the Nintendo Switch Pro / Right Joy-Con integrated NFC reader.
 */
enum class SwitchNfcStatus {
    UNAVAILABLE,
    IDLE,
    POLLING,
    TAG_DETECTED
}
