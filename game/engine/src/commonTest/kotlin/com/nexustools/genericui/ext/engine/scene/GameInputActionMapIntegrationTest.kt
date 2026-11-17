package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.gamepad.ControllerHardwareProfile
import com.nexustools.genericui.ext.gamepad.GamepadAxis
import com.nexustools.genericui.ext.gamepad.GamepadAxis2D
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.gamepad.GamepadFrameSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameInputActionMapIntegrationTest {
    @Test
    fun actionMapReadsXboxDualSenseAndSwitchInputsInsideGameSceneTick() {
        val scene = GameScene("InputIntegration")
        scene.actionMap
            .bindKey("jump", "Space")
            .bindGamepadButton("jump", GamepadButton.A)
            .bindGamepadAxis2D("move", useRightStick = false, includeDpad = true)
            .bindGamepadAxis1D("accelerate", GamepadAxis.RIGHT_TRIGGER)

        // Inject synthetic DualSense Cross press (canonical A) + right trigger + left stick right
        scene.gamepadManager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 0,
                connected = true,
                deviceName = "DualSense Wireless Controller",
                profile = ControllerHardwareProfile.PLAYSTATION_DUALSENSE,
                pressedButtonsMask = GamepadButton.A.bitMask,
                leftStick = GamepadAxis2D(0.75f, 0f),
                rightTrigger = 0.85f
            )
        )
        val acc = FixedStepAccumulator(fixedStepSeconds = 1f / 60f)
        scene.stepLogicTick(1f / 60f, acc)

        assertTrue(scene.actionMap.isActionActive("jump", scene.input))
        assertTrue(scene.actionMap.wasActionJustPressed("jump", scene.input))
        assertEquals(0.85f, scene.actionMap.readAxis1D("accelerate", scene.input), 1e-4f)
        assertEquals(0.75f, scene.actionMap.readAxis2D("move", scene.input).x, 1e-4f)

        // Release on next tick -> no longer active or justPressed
        scene.gamepadManager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 0,
                connected = true,
                deviceName = "DualSense Wireless Controller",
                profile = ControllerHardwareProfile.PLAYSTATION_DUALSENSE
            )
        )
        scene.stepLogicTick(1f / 60f, acc)
        assertFalse(scene.actionMap.isActionActive("jump", scene.input))
        assertFalse(scene.actionMap.wasActionJustPressed("jump", scene.input))
    }
}
