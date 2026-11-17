package com.nexustools.genericui.ext.gamepad

/**
 * Hardware-to-canonical-Xbox mapping table describing raw button indices, raw axis indices,
 * axis inversions, half-trigger calibration, POV hat bindings, and Switch positional/printed behavior.
 */
data class GamepadHardwareMapping(
    val profileName: String,
    val profile: ControllerHardwareProfile,
    val vendorId: Int = 0,
    val productId: Int = 0,
    val buttonIndices: IntArray = IntArray(GamepadButton.entries.size) { it },
    val axisIndices: IntArray = IntArray(GamepadAxis.entries.size) { it },
    val axisInverted: BooleanArray = BooleanArray(GamepadAxis.entries.size) { false },
    val triggersSignedMinusOneToOne: Boolean = false,
    val usesPositionalSwitchSwap: Boolean = (profile.family == ControllerFamily.NINTENDO_SWITCH)
) {
    fun rawButtonFor(button: GamepadButton): Int =
        buttonIndices.getOrElse(button.standardIndex) { UNBOUND }

    fun axisIndexFor(axis: GamepadAxis): Int =
        axisIndices.getOrElse(axis.standardIndex) { -1 }

    fun isAxisInverted(axis: GamepadAxis): Boolean =
        axisInverted.getOrElse(axis.standardIndex) { false }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GamepadHardwareMapping) return false
        return profileName == other.profileName &&
            profile == other.profile &&
            vendorId == other.vendorId &&
            productId == other.productId &&
            buttonIndices.contentEquals(other.buttonIndices) &&
            axisIndices.contentEquals(other.axisIndices) &&
            axisInverted.contentEquals(other.axisInverted) &&
            triggersSignedMinusOneToOne == other.triggersSignedMinusOneToOne &&
            usesPositionalSwitchSwap == other.usesPositionalSwitchSwap
    }

    override fun hashCode(): Int {
        var result = profileName.hashCode()
        result = 31 * result + profile.hashCode()
        result = 31 * result + vendorId
        result = 31 * result + productId
        result = 31 * result + buttonIndices.contentHashCode()
        result = 31 * result + axisIndices.contentHashCode()
        result = 31 * result + axisInverted.contentHashCode()
        result = 31 * result + triggersSignedMinusOneToOne.hashCode()
        result = 31 * result + usesPositionalSwitchSwap.hashCode()
        return result
    }

    companion object {
        const val UNBOUND: Int = -1
        private const val HAT_ENCODING_BASE: Int = 1000
        private const val AXIS_BUTTON_ENCODING_BASE: Int = 2000

        fun encodeHatMask(hatMask: Int): Int = HAT_ENCODING_BASE + hatMask
        fun decodeHatMask(encoded: Int): Int? =
            if (encoded in HAT_ENCODING_BASE until AXIS_BUTTON_ENCODING_BASE) encoded - HAT_ENCODING_BASE else null

        fun encodeAxisButton(axisIndex: Int): Int = AXIS_BUTTON_ENCODING_BASE + axisIndex
        fun decodeAxisButton(encoded: Int): Int? =
            if (encoded >= AXIS_BUTTON_ENCODING_BASE) encoded - AXIS_BUTTON_ENCODING_BASE else null

        /**
         * Canonical Xbox 360 / Xbox One / Xbox Series raw XInput / standard W3C order.
         */
        fun standardXbox(
            profile: ControllerHardwareProfile = ControllerHardwareProfile.XBOX_ONE_SERIES
        ): GamepadHardwareMapping = GamepadHardwareMapping(
            profileName = "Xbox Standard",
            profile = profile,
            vendorId = 0x045E,
            usesPositionalSwitchSwap = false
        )

        /**
         * Sony DualShock 4 / DualSense HID raw button & axis mapping (Cross->A, Circle->B,
         * Square->X, Triangle->Y, L2/R2 analog on axes 4/5 or 3/4, triggers signed -1..1).
         */
        fun sonyDualSenseHid(
            profile: ControllerHardwareProfile = ControllerHardwareProfile.PLAYSTATION_DUALSENSE
        ): GamepadHardwareMapping = GamepadHardwareMapping(
            profileName = if (profile == ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4) {
                "Sony DualShock 4"
            } else {
                "Sony DualSense"
            },
            profile = profile,
            vendorId = 0x054C,
            buttonIndices = IntArray(GamepadButton.entries.size) { UNBOUND }.apply {
                this[GamepadButton.X.standardIndex] = 0 // Square (physical West)
                this[GamepadButton.A.standardIndex] = 1 // Cross (physical South)
                this[GamepadButton.B.standardIndex] = 2 // Circle (physical East)
                this[GamepadButton.Y.standardIndex] = 3 // Triangle (physical North)
                this[GamepadButton.LEFT_BUMPER.standardIndex] = 4 // L1
                this[GamepadButton.RIGHT_BUMPER.standardIndex] = 5 // R1
                this[GamepadButton.LEFT_TRIGGER_BUTTON.standardIndex] = 6 // L2 digital
                this[GamepadButton.RIGHT_TRIGGER_BUTTON.standardIndex] = 7 // R2 digital
                this[GamepadButton.VIEW.standardIndex] = 8 // Create / Share
                this[GamepadButton.MENU.standardIndex] = 9 // Options
                this[GamepadButton.LEFT_STICK.standardIndex] = 10 // L3
                this[GamepadButton.RIGHT_STICK.standardIndex] = 11 // R3
                this[GamepadButton.GUIDE.standardIndex] = 12 // PS
                this[GamepadButton.SHARE.standardIndex] = 13 // Touchpad click / Mute
                this[GamepadButton.DPAD_UP.standardIndex] = encodeHatMask(1)
                this[GamepadButton.DPAD_RIGHT.standardIndex] = encodeHatMask(2)
                this[GamepadButton.DPAD_DOWN.standardIndex] = encodeHatMask(4)
                this[GamepadButton.DPAD_LEFT.standardIndex] = encodeHatMask(8)
            },
            axisIndices = intArrayOf(0, 1, 2, 5, 3, 4),
            triggersSignedMinusOneToOne = true,
            usesPositionalSwitchSwap = false
        )

        /**
         * Nintendo Switch Pro Controller / Joy-Con pair mapping.
         * In positional mode (`usePositionalLayout = true`, default): physical South (`B` printed on Switch)
         * acts as canonical `A`, physical East (`A` printed) acts as `B`, physical West (`Y`) acts as `X`,
         * and physical North (`X`) acts as `Y`. When `usePositionalLayout = false`, printed labels stay matched
         * to `A`/`B`/`X`/`Y`.
         */
        fun nintendoSwitchPro(
            usePositionalLayout: Boolean = true,
            profile: ControllerHardwareProfile = ControllerHardwareProfile.NINTENDO_SWITCH_PRO
        ): GamepadHardwareMapping = GamepadHardwareMapping(
            profileName = if (profile == ControllerHardwareProfile.NINTENDO_JOYCON_PAIR) {
                "Nintendo Joy-Con Pair"
            } else {
                "Nintendo Switch Pro"
            },
            profile = profile,
            vendorId = 0x057E,
            buttonIndices = IntArray(GamepadButton.entries.size) { UNBOUND }.apply {
                if (usePositionalLayout) {
                    // Raw USB HID button order on Switch Pro: 0=B(South), 1=A(East), 2=Y(West), 3=X(North)
                    this[GamepadButton.A.standardIndex] = 0
                    this[GamepadButton.B.standardIndex] = 1
                    this[GamepadButton.X.standardIndex] = 2
                    this[GamepadButton.Y.standardIndex] = 3
                } else {
                    // Printed-label match: printed A(idx 1)->A, B(idx 0)->B, X(idx 3)->X, Y(idx 2)->Y
                    this[GamepadButton.A.standardIndex] = 1
                    this[GamepadButton.B.standardIndex] = 0
                    this[GamepadButton.X.standardIndex] = 3
                    this[GamepadButton.Y.standardIndex] = 2
                }
                this[GamepadButton.LEFT_BUMPER.standardIndex] = 4 // L
                this[GamepadButton.RIGHT_BUMPER.standardIndex] = 5 // R
                this[GamepadButton.LEFT_TRIGGER_BUTTON.standardIndex] = 6 // ZL
                this[GamepadButton.RIGHT_TRIGGER_BUTTON.standardIndex] = 7 // ZR
                this[GamepadButton.VIEW.standardIndex] = 8 // Minus
                this[GamepadButton.MENU.standardIndex] = 9 // Plus
                this[GamepadButton.LEFT_STICK.standardIndex] = 10
                this[GamepadButton.RIGHT_STICK.standardIndex] = 11
                this[GamepadButton.GUIDE.standardIndex] = 12 // Home
                this[GamepadButton.SHARE.standardIndex] = 13 // Capture
                this[GamepadButton.DPAD_UP.standardIndex] = encodeHatMask(1)
                this[GamepadButton.DPAD_RIGHT.standardIndex] = encodeHatMask(2)
                this[GamepadButton.DPAD_DOWN.standardIndex] = encodeHatMask(4)
                this[GamepadButton.DPAD_LEFT.standardIndex] = encodeHatMask(8)
            },
            axisIndices = intArrayOf(0, 1, 2, 3, -1, -1),
            triggersSignedMinusOneToOne = false,
            usesPositionalSwitchSwap = usePositionalLayout
        )
    }
}
