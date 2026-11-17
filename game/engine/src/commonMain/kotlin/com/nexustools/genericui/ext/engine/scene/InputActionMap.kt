package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.gamepad.GamepadAxis
import com.nexustools.genericui.ext.gamepad.GamepadAxis2D
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.engine.math.Vec2f

/**
 * Named input action registry combining keyboard, pointer, and Xbox-mapped gamepad bindings
 * under unified digital and analog queries.
 */
class InputActionMap {
    private val bindings = mutableMapOf<String, MutableList<InputBindingSpec>>()

    fun bindKey(actionName: String, keyCode: String): InputActionMap {
        bindings.getOrPut(actionName) { mutableListOf() }
            .add(InputBindingSpec(kind = InputBindingKind.KEY, keyCode = keyCode))
        return this
    }

    fun bindPointerButton(actionName: String, button: Int = 0): InputActionMap {
        bindings.getOrPut(actionName) { mutableListOf() }
            .add(InputBindingSpec(kind = InputBindingKind.POINTER_BUTTON, pointerButton = button))
        return this
    }

    fun bindGamepadButton(
        actionName: String,
        button: GamepadButton,
        playerIndex: Int = 0
    ): InputActionMap {
        bindings.getOrPut(actionName) { mutableListOf() }
            .add(
                InputBindingSpec(
                    kind = InputBindingKind.GAMEPAD_BUTTON,
                    gamepadPlayerIndex = playerIndex,
                    gamepadButton = button
                )
            )
        return this
    }

    fun bindGamepadAxis1D(
        actionName: String,
        axis: GamepadAxis,
        playerIndex: Int = 0,
        scale: Float = 1f
    ): InputActionMap {
        bindings.getOrPut(actionName) { mutableListOf() }
            .add(
                InputBindingSpec(
                    kind = InputBindingKind.GAMEPAD_AXIS_1D,
                    gamepadPlayerIndex = playerIndex,
                    gamepadAxis = axis,
                    scale = scale
                )
            )
        return this
    }

    fun bindGamepadAxis2D(
        actionName: String,
        useRightStick: Boolean = false,
        includeDpad: Boolean = true,
        playerIndex: Int = 0
    ): InputActionMap {
        val list = bindings.getOrPut(actionName) { mutableListOf() }
        list.add(
            InputBindingSpec(
                kind = if (useRightStick) InputBindingKind.GAMEPAD_STICK_RIGHT_2D else InputBindingKind.GAMEPAD_STICK_LEFT_2D,
                gamepadPlayerIndex = playerIndex
            )
        )
        if (includeDpad) {
            list.add(
                InputBindingSpec(
                    kind = InputBindingKind.GAMEPAD_DPAD_2D,
                    gamepadPlayerIndex = playerIndex
                )
            )
        }
        return this
    }

    fun clearBindings(actionName: String) {
        bindings.remove(actionName)
    }

    fun isActionActive(actionName: String, input: GameInputState): Boolean {
        val list = bindings[actionName] ?: return false
        for (spec in list) {
            val active = when (spec.kind) {
                InputBindingKind.KEY -> input.isKeyDown(spec.keyCode)
                InputBindingKind.POINTER_BUTTON -> input.isPointerButtonDown(spec.pointerButton)
                InputBindingKind.GAMEPAD_BUTTON -> spec.gamepadButton?.let {
                    input.gamepadManager.isDown(spec.gamepadPlayerIndex, it)
                } ?: false
                InputBindingKind.GAMEPAD_AXIS_1D -> spec.gamepadAxis?.let {
                    kotlin.math.abs(input.gamepadManager.state(spec.gamepadPlayerIndex).axisValue(it)) > 0.1f
                } ?: false
                InputBindingKind.GAMEPAD_STICK_LEFT_2D ->
                    input.gamepadManager.leftStick(spec.gamepadPlayerIndex).lengthSquared > 0.01f
                InputBindingKind.GAMEPAD_STICK_RIGHT_2D ->
                    input.gamepadManager.rightStick(spec.gamepadPlayerIndex).lengthSquared > 0.01f
                InputBindingKind.GAMEPAD_DPAD_2D ->
                    input.gamepadManager.dpadVector(spec.gamepadPlayerIndex).lengthSquared > 0.01f
            }
            if (active) return true
        }
        return false
    }

    fun wasActionJustPressed(actionName: String, input: GameInputState): Boolean {
        val list = bindings[actionName] ?: return false
        for (spec in list) {
            val triggered = when (spec.kind) {
                InputBindingKind.KEY -> input.wasKeyJustPressed(spec.keyCode)
                InputBindingKind.POINTER_BUTTON -> input.wasPointerButtonJustPressed(spec.pointerButton)
                InputBindingKind.GAMEPAD_BUTTON -> spec.gamepadButton?.let {
                    input.gamepadManager.wasJustPressed(spec.gamepadPlayerIndex, it)
                } ?: false
                else -> false
            }
            if (triggered) return true
        }
        return false
    }

    fun readAxis1D(actionName: String, input: GameInputState): Float {
        val list = bindings[actionName] ?: return 0f
        var best = 0f
        for (spec in list) {
            val v = when (spec.kind) {
                InputBindingKind.KEY -> if (input.isKeyDown(spec.keyCode)) spec.scale else 0f
                InputBindingKind.POINTER_BUTTON -> if (input.isPointerButtonDown(spec.pointerButton)) spec.scale else 0f
                InputBindingKind.GAMEPAD_BUTTON -> {
                    val down = spec.gamepadButton?.let { input.gamepadManager.isDown(spec.gamepadPlayerIndex, it) } ?: false
                    if (down) spec.scale else 0f
                }
                InputBindingKind.GAMEPAD_AXIS_1D -> spec.gamepadAxis?.let {
                    input.gamepadManager.state(spec.gamepadPlayerIndex).axisValue(it) * spec.scale
                } ?: 0f
                else -> 0f
            }
            if (kotlin.math.abs(v) > kotlin.math.abs(best)) {
                best = v
            }
        }
        return best
    }

    fun readAxis2D(actionName: String, input: GameInputState): Vec2f {
        val list = bindings[actionName] ?: return Vec2f.ZERO
        var accX = 0f
        var accY = 0f
        for (spec in list) {
            val axisVec = when (spec.kind) {
                InputBindingKind.GAMEPAD_STICK_LEFT_2D -> input.gamepadManager.leftStick(spec.gamepadPlayerIndex)
                InputBindingKind.GAMEPAD_STICK_RIGHT_2D -> input.gamepadManager.rightStick(spec.gamepadPlayerIndex)
                InputBindingKind.GAMEPAD_DPAD_2D -> input.gamepadManager.dpadVector(spec.gamepadPlayerIndex)
                else -> GamepadAxis2D.ZERO
            }
            accX += axisVec.x * spec.scale
            accY += axisVec.y * spec.scale
        }
        val combined = Vec2f(accX, accY)
        val len = combined.length()
        return if (len > 1f) combined / len else combined
    }
}
