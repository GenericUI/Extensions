package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.gamepad.GamepadManager
import com.nexustools.genericui.ext.engine.math.Vec2f
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent

/**
 * Aggregated per-tick keyboard, pointer, wheel, and Xbox-mapped gamepad input snapshot for a [GameScene].
 */
class GameInputState(
    val gamepadManager: GamepadManager = GamepadManager()
) {
    private val pressedKeys = mutableSetOf<String>()
    private val justPressedKeys = mutableSetOf<String>()
    private val justReleasedKeys = mutableSetOf<String>()

    private val pressedPointerButtons = mutableSetOf<Int>()
    private val justPressedPointerButtons = mutableSetOf<Int>()
    private val justReleasedPointerButtons = mutableSetOf<Int>()

    var pointerPosition: Vec2f = Vec2f.ZERO
        private set

    var wheelDelta: Vec2f = Vec2f.ZERO
        private set

    fun notifyKeyDown(event: KeyEvent) {
        val code = normalizeKey(event.code.ifEmpty { event.key })
        if (pressedKeys.add(code)) {
            justPressedKeys.add(code)
        }
    }

    fun notifyKeyUp(event: KeyEvent) {
        val code = normalizeKey(event.code.ifEmpty { event.key })
        if (pressedKeys.remove(code)) {
            justReleasedKeys.add(code)
        }
    }

    fun notifyKeyDown(keyCode: String) {
        val code = normalizeKey(keyCode)
        if (pressedKeys.add(code)) {
            justPressedKeys.add(code)
        }
    }

    fun notifyKeyUp(keyCode: String) {
        val code = normalizeKey(keyCode)
        if (pressedKeys.remove(code)) {
            justReleasedKeys.add(code)
        }
    }

    fun notifyPointerDown(event: PointerEvent) {
        pointerPosition = Vec2f(event.x, event.y)
        if (pressedPointerButtons.add(event.button)) {
            justPressedPointerButtons.add(event.button)
        }
    }

    fun notifyPointerMove(event: PointerEvent) {
        pointerPosition = Vec2f(event.x, event.y)
    }

    fun notifyPointerUp(event: PointerEvent) {
        pointerPosition = Vec2f(event.x, event.y)
        if (pressedPointerButtons.remove(event.button)) {
            justReleasedPointerButtons.add(event.button)
        }
    }

    fun notifyWheel(event: WheelEvent) {
        wheelDelta = Vec2f(wheelDelta.x + event.deltaX, wheelDelta.y + event.deltaY)
    }

    fun isKeyDown(keyCode: String): Boolean = normalizeKey(keyCode) in pressedKeys

    fun wasKeyJustPressed(keyCode: String): Boolean = normalizeKey(keyCode) in justPressedKeys

    fun wasKeyJustReleased(keyCode: String): Boolean = normalizeKey(keyCode) in justReleasedKeys

    fun isPointerButtonDown(button: Int = 0): Boolean = button in pressedPointerButtons

    fun wasPointerButtonJustPressed(button: Int = 0): Boolean = button in justPressedPointerButtons

    fun wasPointerButtonJustReleased(button: Int = 0): Boolean = button in justReleasedPointerButtons

    fun beginNextTick() {
        justPressedKeys.clear()
        justReleasedKeys.clear()
        justPressedPointerButtons.clear()
        justReleasedPointerButtons.clear()
        wheelDelta = Vec2f.ZERO
    }

    fun reset() {
        pressedKeys.clear()
        justPressedKeys.clear()
        justReleasedKeys.clear()
        pressedPointerButtons.clear()
        justPressedPointerButtons.clear()
        justReleasedPointerButtons.clear()
        pointerPosition = Vec2f.ZERO
        wheelDelta = Vec2f.ZERO
    }

    private fun normalizeKey(key: String): String = key.trim().lowercase()
}
