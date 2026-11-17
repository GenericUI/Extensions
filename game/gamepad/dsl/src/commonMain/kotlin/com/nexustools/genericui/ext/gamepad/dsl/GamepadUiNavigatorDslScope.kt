package com.nexustools.genericui.ext.gamepad.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gamepad.GamepadManager
import com.nexustools.genericui.ext.gamepad.ui.DefaultSpatialGamepadUiNavigationStrategy
import com.nexustools.genericui.ext.gamepad.ui.GamepadUiNavigationStrategy
import com.nexustools.genericui.ext.gamepad.ui.GamepadUiNavigator

/**
 * DSL configuration scope for configuring [GamepadUiNavigator] on a `Window`, `DialogWindow`,
 * `Application`, or any `AbstractContainer`.
 */
@GenericUiDslMarker
class GamepadUiNavigatorDslScope(
    val root: AbstractContainer,
    var gamepadManager: GamepadManager = GamepadManager.shared,
    var playerIndex: Int = 0,
    var strategy: GamepadUiNavigationStrategy = DefaultSpatialGamepadUiNavigationStrategy(),
    var stickThreshold: Float = 0.55f,
    var initialRepeatDelaySeconds: Float = 0.35f,
    var repeatIntervalSeconds: Float = 0.12f,
    var autoStart: Boolean = GenericUI.activeFactory != null
) {
    var onCancelUnhandled: (() -> Unit)? = null

    fun build(): GamepadUiNavigator {
        val chosenStrategy = strategy
        if (chosenStrategy is DefaultSpatialGamepadUiNavigationStrategy && onCancelUnhandled != null) {
            chosenStrategy.onUnhandledCancel = onCancelUnhandled
        }
        val navigator = GamepadUiNavigator(
            root = root,
            gamepadManager = gamepadManager,
            playerIndex = playerIndex,
            strategy = chosenStrategy,
            stickThreshold = stickThreshold,
            initialRepeatDelaySeconds = initialRepeatDelaySeconds,
            repeatIntervalSeconds = repeatIntervalSeconds
        )
        if (autoStart) {
            navigator.start()
        }
        return navigator
    }
}
