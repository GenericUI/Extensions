package com.nexustools.genericui.ext.gamepad

/**
 * Resolves hardware-accurate UI button prompts (Xbox glyphs, PlayStation Cross/Circle/Square/Triangle,
 * or Nintendo Switch positional vs printed face button labels) for any [GamepadButton].
 */
object ControllerButtonLabels {
    fun resolveLabel(
        button: GamepadButton,
        profile: ControllerHardwareProfile,
        usePositionalSwitchLayout: Boolean = true
    ): String = when (profile) {
        ControllerHardwareProfile.XBOX_ONE_SERIES,
        ControllerHardwareProfile.XBOX_ELITE -> when (button) {
            GamepadButton.GUIDE -> "Xbox"
            else -> button.defaultXboxLabel
        }

        ControllerHardwareProfile.GENERIC_XINPUT -> button.defaultXboxLabel

        ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4,
        ControllerHardwareProfile.PLAYSTATION_DUALSENSE -> when (button) {
            GamepadButton.A -> "Cross"
            GamepadButton.B -> "Circle"
            GamepadButton.X -> "Square"
            GamepadButton.Y -> "Triangle"
            GamepadButton.LEFT_BUMPER -> "L1"
            GamepadButton.RIGHT_BUMPER -> "R1"
            GamepadButton.LEFT_TRIGGER_BUTTON -> "L2"
            GamepadButton.RIGHT_TRIGGER_BUTTON -> "R2"
            GamepadButton.VIEW ->
                if (profile == ControllerHardwareProfile.PLAYSTATION_DUALSENSE) "Create" else "Share"
            GamepadButton.MENU -> "Options"
            GamepadButton.LEFT_STICK -> "L3"
            GamepadButton.RIGHT_STICK -> "R3"
            GamepadButton.GUIDE -> "PS"
            GamepadButton.SHARE ->
                if (profile == ControllerHardwareProfile.PLAYSTATION_DUALSENSE) "Mute" else "Touchpad"
            else -> button.defaultXboxLabel
        }

        ControllerHardwareProfile.NINTENDO_SWITCH_PRO,
        ControllerHardwareProfile.NINTENDO_JOYCON_PAIR -> when (button) {
            GamepadButton.A -> if (usePositionalSwitchLayout) "B" else "A"
            GamepadButton.B -> if (usePositionalSwitchLayout) "A" else "B"
            GamepadButton.X -> if (usePositionalSwitchLayout) "Y" else "X"
            GamepadButton.Y -> if (usePositionalSwitchLayout) "X" else "Y"
            GamepadButton.LEFT_BUMPER -> "L"
            GamepadButton.RIGHT_BUMPER -> "R"
            GamepadButton.LEFT_TRIGGER_BUTTON -> "ZL"
            GamepadButton.RIGHT_TRIGGER_BUTTON -> "ZR"
            GamepadButton.VIEW -> "-"
            GamepadButton.MENU -> "+"
            GamepadButton.LEFT_STICK -> "LS"
            GamepadButton.RIGHT_STICK -> "RS"
            GamepadButton.GUIDE -> "Home"
            GamepadButton.SHARE -> "Capture"
            else -> button.defaultXboxLabel
        }
    }
}
