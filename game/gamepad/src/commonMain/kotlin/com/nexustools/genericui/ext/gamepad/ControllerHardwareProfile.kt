package com.nexustools.genericui.ext.gamepad

/**
 * Detailed controller hardware profile inferred from USB/Bluetooth vendor & product IDs or driver metadata.
 */
enum class ControllerHardwareProfile(
    val family: ControllerFamily
) {
    XBOX_ONE_SERIES(ControllerFamily.XBOX),
    XBOX_ELITE(ControllerFamily.XBOX),
    PLAYSTATION_DUALSHOCK4(ControllerFamily.PLAYSTATION),
    PLAYSTATION_DUALSENSE(ControllerFamily.PLAYSTATION),
    NINTENDO_SWITCH_PRO(ControllerFamily.NINTENDO_SWITCH),
    NINTENDO_JOYCON_PAIR(ControllerFamily.NINTENDO_SWITCH),
    GENERIC_XINPUT(ControllerFamily.GENERIC)
}
