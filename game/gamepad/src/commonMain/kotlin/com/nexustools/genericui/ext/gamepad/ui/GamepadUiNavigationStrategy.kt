package com.nexustools.genericui.ext.gamepad.ui

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.gamepad.GamepadFrameSnapshot

/**
 * Pluggable strategy controlling how [GamepadUiNavigator] discovers interactive widgets, selects
 * initial and directional focus, highlights controls, activates/adjusts/scrolls widgets, closes modals,
 * and cycles tabs. Developers may replace this interface completely or subclass
 * [DefaultSpatialGamepadUiNavigationStrategy] to customize individual behaviors.
 */
interface GamepadUiNavigationStrategy {
    fun discoverFocusableComponents(root: AbstractContainer): List<AbstractComponent>

    fun resolveInitialFocus(
        root: AbstractContainer,
        candidates: List<AbstractComponent>
    ): AbstractComponent?

    fun resolveNextFocus(
        current: AbstractComponent?,
        direction: GamepadNavDirection,
        candidates: List<AbstractComponent>
    ): AbstractComponent?

    fun onFocusChanged(
        previous: AbstractComponent?,
        current: AbstractComponent?,
        root: AbstractContainer
    )

    fun onActivate(
        focused: AbstractComponent,
        root: AbstractContainer,
        frame: GamepadFrameSnapshot
    ): Boolean

    fun onCancel(
        focused: AbstractComponent?,
        root: AbstractContainer,
        frame: GamepadFrameSnapshot
    ): Boolean

    fun onTabCycle(
        root: AbstractContainer,
        delta: Int,
        frame: GamepadFrameSnapshot
    ): Boolean

    fun onContinuousInput(
        focused: AbstractComponent?,
        root: AbstractContainer,
        frame: GamepadFrameSnapshot,
        deltaSeconds: Float
    ): Boolean
}
