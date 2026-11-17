package com.nexustools.genericui.ext.gamepad

/**
 * Interactive controller handle for player slot [playerIndex], exposing unified state alongside
 * PlayStation (touchpad/lightbar/adaptive triggers/mute LED), Nintendo Switch (HD rumble/player LEDs/
 * Home LED/positional vs printed toggle), Xbox (4-motor impulse trigger rumble), and 6-axis IMU calibration.
 */
class GamepadControllerHandle internal constructor(
    val playerIndex: Int,
    private val manager: GamepadManager
) {
    val snapshot: GamepadFrameSnapshot
        get() = manager.snapshot(playerIndex)

    val connected: Boolean
        get() = snapshot.connected

    val profile: ControllerHardwareProfile
        get() = snapshot.profile

    val family: ControllerFamily
        get() = snapshot.family

    val motion: SixAxisMotionState
        get() = snapshot.motion

    val playStationFeatures: PlayStationControllerFeatures?
        get() = snapshot.playStation

    val switchFeatures: SwitchControllerFeatures?
        get() = snapshot.switchFeatures

    val xboxFeatures: XboxControllerFeatures?
        get() = snapshot.xbox

    fun isButtonDown(button: GamepadButton): Boolean = snapshot.isButtonDown(button)
    fun isButtonJustPressed(button: GamepadButton): Boolean = snapshot.isButtonJustPressed(button)
    fun isButtonJustReleased(button: GamepadButton): Boolean = snapshot.isButtonJustReleased(button)

    fun setRumble(
        lowFrequency: Float,
        highFrequency: Float,
        durationMs: Int = 150,
        leftTrigger: Float = 0f,
        rightTrigger: Float = 0f
    ): Boolean = manager.setRumble(
        playerIndex = playerIndex,
        request = GamepadRumbleRequest(
            lowFrequency = lowFrequency,
            highFrequency = highFrequency,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger,
            durationMs = durationMs
        )
    )

    fun stopRumble(): Boolean = manager.setRumble(playerIndex, GamepadRumbleRequest.STOP)

    fun setLightbarColor(r: Int, g: Int, b: Int): Boolean =
        manager.setPlayStationLightbarColor(playerIndex, r, g, b)

    fun setPlayerIndicatorLedMask(mask: Int): Boolean =
        manager.setPlayStationPlayerIndicatorLedMask(playerIndex, mask)

    fun setMicrophoneMuteLed(state: MuteLedState): Boolean =
        manager.setPlayStationMuteLed(playerIndex, state)

    fun setAdaptiveTrigger(side: AdaptiveTriggerSide, effect: AdaptiveTriggerEffect): Boolean =
        manager.setPlayStationAdaptiveTrigger(playerIndex, side, effect)

    fun setHdRumble(command: SwitchHdRumbleCommand): Boolean =
        manager.setSwitchHdRumble(playerIndex, command)

    fun setPlayerLeds(mask: Int): Boolean =
        manager.setSwitchPlayerLeds(playerIndex, mask)

    fun setHomeLedPattern(pattern: SwitchHomeLedPattern): Boolean =
        manager.setSwitchHomeLedPattern(playerIndex, pattern)

    fun setUsePositionalActionButtons(usePositional: Boolean) {
        manager.setSwitchUsePositionalActionButtons(playerIndex, usePositional)
    }

    fun setImpulseRumble(command: XboxImpulseRumbleCommand): Boolean =
        manager.setXboxImpulseRumble(playerIndex, command)

    fun calibrateGyroBias(biasX: Float, biasY: Float, biasZ: Float) {
        manager.calibrateGyroBias(playerIndex, biasX, biasY, biasZ)
    }

    fun calibrateGyroFromStationarySamples(
        samplesGyroX: FloatArray,
        samplesGyroY: FloatArray,
        samplesGyroZ: FloatArray
    ) {
        manager.calibrateGyroFromStationarySamples(playerIndex, samplesGyroX, samplesGyroY, samplesGyroZ)
    }

    fun resetMotionOrientation() {
        manager.resetMotionOrientation(playerIndex)
    }
}
