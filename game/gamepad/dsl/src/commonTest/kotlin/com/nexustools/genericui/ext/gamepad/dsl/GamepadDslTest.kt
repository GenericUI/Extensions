package com.nexustools.genericui.ext.gamepad.dsl

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.ext.dsl.button
import com.nexustools.genericui.ext.dsl.vstack
import com.nexustools.genericui.ext.gamepad.ControllerHardwareProfile
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.gamepad.GamepadFrameSnapshot
import com.nexustools.genericui.ext.gamepad.GamepadManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GamepadDslTest {
    @Test
    fun windowGamepadNavigationDslAttachesAndDrivesUiControls() {
        val manager = GamepadManager()
        val window = Window("Gamepad DSL Demo")
        var confirmed = 0

        val content = window.getOrCreateFragmentContainer()
        val rootStack = content.vstack {}
        val btnConfirm = rootStack.button("Confirm") {
            onClick = { confirmed++ }
        }

        var backCalls = 0
        val navigator = window.gamepadNavigation {
            gamepadManager = manager
            autoStart = false
            onCancelUnhandled = { backCalls++ }
        }

        manager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 0,
                connected = true,
                profile = ControllerHardwareProfile.PLAYSTATION_DUALSENSE,
                pressedButtonsMask = GamepadButton.A.bitMask
            )
        )
        navigator.step(1f / 60f)
        assertSame(btnConfirm, navigator.focusedComponent)
        assertEquals(1, confirmed)
        assertEquals("Cross", btnConfirm.gamepadButtonBadgeText(GamepadButton.A, gamepadManager = manager))

        manager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 0,
                connected = true,
                profile = ControllerHardwareProfile.PLAYSTATION_DUALSENSE,
                pressedButtonsMask = GamepadButton.B.bitMask
            )
        )
        navigator.step(1f / 60f)
        assertEquals(1, backCalls)
    }
}
