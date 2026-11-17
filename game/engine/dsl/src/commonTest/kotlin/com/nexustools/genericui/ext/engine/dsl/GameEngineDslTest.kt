package com.nexustools.genericui.ext.engine.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.scene.FixedStepAccumulator
import com.nexustools.genericui.ext.gamepad.GamepadAxis
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.gamepad.GamepadFrameSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameEngineDslTest {
    @Test
    fun engineDslBuildsSceneEntitiesUiStageHostSkeletonAndGamepadBindings() {
        val root = Container()
        val label = Label("Player")
        var ticksObserved = 0
        var animProgress = 0f

        val host = root.uiStageHost(name = "DslScene", autoStart = false) {
            gamepadBindings {
                button("fire", GamepadButton.A)
                axis1D("throttle", GamepadAxis.RIGHT_TRIGGER)
                axis2D("steer")
                key("fire", "Enter")
            }

            animateLambda(durationSeconds = 1f, curve = EasingFunction.LINEAR) { p ->
                animProgress = p
            }

            entity(name = "Hero", tag = "Player") {
                position2D(120f, 80f)
                val rig = skeleton2D {
                    bone("root", lengthPx = 20f)
                    bone("arm", parentName = "root", lengthPx = 18f)
                }
                assertNotNull(rig.getBone("arm"))
                uiBinding(label, skeleton2D = rig)
                behavior {
                    onTick { _ ->
                        ticksObserved++
                    }
                }
            }
        }

        host.scene.gamepadManager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 0,
                connected = true,
                pressedButtonsMask = GamepadButton.A.bitMask,
                rightTrigger = 0.6f
            )
        )
        host.scene.stepLogicTick(1f / 60f, FixedStepAccumulator(1f / 60f))
        host.stepVBlankFrame(1L, 0.0)
        host.stepVBlankFrame(2L, 500.0)

        assertEquals(1, ticksObserved)
        assertTrue(host.scene.actionMap.isActionActive("fire", host.scene.input))
        assertEquals(0.6f, host.scene.actionMap.readAxis1D("throttle", host.scene.input), 1e-4f)
        assertEquals(0.5f, animProgress, 0.02f)
        assertEquals(1, root.children.size)
        host.dispose()
    }
}
