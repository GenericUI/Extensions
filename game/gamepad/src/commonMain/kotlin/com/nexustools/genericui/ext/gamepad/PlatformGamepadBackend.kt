package com.nexustools.genericui.ext.gamepad

/**
 * Per-target native controller polling and hardware actuator/LED/trigger/HID output bridge.
 */
expect object PlatformGamepadBackend {
    val isHardwarePollingSupported: Boolean

    fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot>

    fun setRumble(playerIndex: Int, request: GamepadRumbleRequest): Boolean

    fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean

    fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean

    fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean

    fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean

    fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean

    fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean

    fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean
}
