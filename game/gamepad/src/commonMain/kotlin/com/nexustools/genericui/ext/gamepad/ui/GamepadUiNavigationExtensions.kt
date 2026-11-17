package com.nexustools.genericui.ext.gamepad.ui

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.gamepad.GamepadManager

/**
 * Attaches zero-configuration game controller navigation to this [AbstractContainer].
 */
fun AbstractContainer.enableGamepadUiNavigation(
    gamepadManager: GamepadManager = GamepadManager.shared,
    playerIndex: Int = 0,
    strategy: GamepadUiNavigationStrategy = DefaultSpatialGamepadUiNavigationStrategy(),
    autoStart: Boolean = GenericUI.activeFactory != null
): GamepadUiNavigator {
    val navigator = GamepadUiNavigator(
        root = this,
        gamepadManager = gamepadManager,
        playerIndex = playerIndex,
        strategy = strategy
    )
    if (autoStart) {
        navigator.start()
    }
    return navigator
}

/**
 * Attaches zero-configuration game controller navigation to this [Window] (including modal
 * [DialogWindow]s opened inside it).
 */
fun Window.enableGamepadUiNavigation(
    gamepadManager: GamepadManager = GamepadManager.shared,
    playerIndex: Int = 0,
    strategy: GamepadUiNavigationStrategy = DefaultSpatialGamepadUiNavigationStrategy(),
    autoStart: Boolean = GenericUI.activeFactory != null
): GamepadUiNavigator = (this as AbstractContainer).enableGamepadUiNavigation(
    gamepadManager = gamepadManager,
    playerIndex = playerIndex,
    strategy = strategy,
    autoStart = autoStart
)

/**
 * Attaches zero-configuration game controller navigation to this [DialogWindow].
 */
fun DialogWindow.enableGamepadUiNavigation(
    gamepadManager: GamepadManager = GamepadManager.shared,
    playerIndex: Int = 0,
    strategy: GamepadUiNavigationStrategy = DefaultSpatialGamepadUiNavigationStrategy(),
    autoStart: Boolean = GenericUI.activeFactory != null
): GamepadUiNavigator = (this as AbstractContainer).enableGamepadUiNavigation(
    gamepadManager = gamepadManager,
    playerIndex = playerIndex,
    strategy = strategy,
    autoStart = autoStart
)

/**
 * Attaches zero-configuration game controller navigation to [Application.mainWindow].
 */
fun Application.enableGamepadUiNavigation(
    gamepadManager: GamepadManager = GamepadManager.shared,
    playerIndex: Int = 0,
    strategy: GamepadUiNavigationStrategy = DefaultSpatialGamepadUiNavigationStrategy(),
    autoStart: Boolean = GenericUI.activeFactory != null
): GamepadUiNavigator = mainWindow.enableGamepadUiNavigation(
    gamepadManager = gamepadManager,
    playerIndex = playerIndex,
    strategy = strategy,
    autoStart = autoStart
)
