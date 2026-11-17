package com.nexustools.genericui.ext.gamepad.dsl

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.gamepad.GamepadManager
import com.nexustools.genericui.ext.gamepad.ui.GamepadUiNavigator

/**
 * Configures and attaches a [GamepadUiNavigator] directly onto this [AbstractContainer].
 */
fun AbstractContainer.gamepadNavigation(
    block: (@GenericUiDslMarker GamepadUiNavigatorDslScope.() -> Unit) = {}
): GamepadUiNavigator {
    val scope = GamepadUiNavigatorDslScope(root = this)
    scope.block()
    return scope.build()
}

/**
 * Configures and attaches a [GamepadUiNavigator] onto this [Window] and its modal dialogs.
 */
fun Window.gamepadNavigation(
    block: (@GenericUiDslMarker GamepadUiNavigatorDslScope.() -> Unit) = {}
): GamepadUiNavigator = (this as AbstractContainer).gamepadNavigation(block)

/**
 * Configures and attaches a [GamepadUiNavigator] onto this [DialogWindow].
 */
fun DialogWindow.gamepadNavigation(
    block: (@GenericUiDslMarker GamepadUiNavigatorDslScope.() -> Unit) = {}
): GamepadUiNavigator = (this as AbstractContainer).gamepadNavigation(block)

/**
 * Configures and attaches a [GamepadUiNavigator] onto [Application.mainWindow].
 */
fun Application.gamepadNavigation(
    block: (@GenericUiDslMarker GamepadUiNavigatorDslScope.() -> Unit) = {}
): GamepadUiNavigator = mainWindow.gamepadNavigation(block)

/**
 * Attaches a hardware-accurate button prompt label to any [AbstractContainer] via [GamepadManager].
 */
fun com.nexustools.genericui.core.AbstractComponent.gamepadButtonBadgeText(
    button: GamepadButton,
    playerIndex: Int = 0,
    gamepadManager: GamepadManager = GamepadManager.shared
): String = gamepadManager.snapshot(playerIndex).resolveButtonLabel(button)
