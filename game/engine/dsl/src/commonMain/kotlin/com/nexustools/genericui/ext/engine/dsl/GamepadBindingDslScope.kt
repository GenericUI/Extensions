package com.nexustools.genericui.ext.engine.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gamepad.GamepadAxis
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.engine.scene.InputActionMap

/**
 * Fluent DSL scope for registering keyboard, pointer, and Xbox-mapped gamepad bindings onto an
 * [InputActionMap].
 */
@GenericUiDslMarker
class GamepadBindingDslScope(
    val actionMap: InputActionMap
) {
    fun button(
        actionName: String,
        button: GamepadButton,
        playerIndex: Int = 0
    ) {
        actionMap.bindGamepadButton(actionName, button, playerIndex)
    }

    fun axis1D(
        actionName: String,
        axis: GamepadAxis,
        playerIndex: Int = 0,
        scale: Float = 1f
    ) {
        actionMap.bindGamepadAxis1D(actionName, axis, playerIndex, scale)
    }

    fun axis2D(
        actionName: String,
        useRightStick: Boolean = false,
        includeDpad: Boolean = true,
        playerIndex: Int = 0
    ) {
        actionMap.bindGamepadAxis2D(actionName, useRightStick, includeDpad, playerIndex)
    }

    fun key(actionName: String, keyCode: String) {
        actionMap.bindKey(actionName, keyCode)
    }

    fun pointerButton(actionName: String, button: Int = 0) {
        actionMap.bindPointerButton(actionName, button)
    }
}
