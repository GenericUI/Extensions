package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.gamepad.GamepadAxis
import com.nexustools.genericui.ext.gamepad.GamepadButton

/**
 * Single physical input binding contributing to a named action in [InputActionMap].
 */
data class InputBindingSpec(
    val kind: InputBindingKind,
    val keyCode: String = "",
    val pointerButton: Int = 0,
    val gamepadPlayerIndex: Int = 0,
    val gamepadButton: GamepadButton? = null,
    val gamepadAxis: GamepadAxis? = null,
    val scale: Float = 1f
)
