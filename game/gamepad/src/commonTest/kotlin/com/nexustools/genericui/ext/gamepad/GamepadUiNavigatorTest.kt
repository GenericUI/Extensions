package com.nexustools.genericui.ext.gamepad

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.action.ToggleButton
import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.RadioButton
import com.nexustools.genericui.component.form.RadioGroup
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.Switch
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.gamepad.ui.DefaultSpatialGamepadUiNavigationStrategy
import com.nexustools.genericui.ext.gamepad.ui.GamepadNavDirection
import com.nexustools.genericui.ext.gamepad.ui.GamepadUiNavigationStrategy
import com.nexustools.genericui.ext.gamepad.ui.GamepadUiNavigator
import com.nexustools.genericui.ext.gamepad.ui.enableGamepadUiNavigation
import com.nexustools.genericui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GamepadUiNavigatorTest {
    private fun padFrame(
        buttonsMask: Int = 0,
        leftStick: GamepadAxis2D = GamepadAxis2D.ZERO,
        rightStick: GamepadAxis2D = GamepadAxis2D.ZERO
    ): GamepadFrameSnapshot = GamepadFrameSnapshot(
        playerIndex = 0,
        connected = true,
        profile = ControllerHardwareProfile.XBOX_ONE_SERIES,
        pressedButtonsMask = buttonsMask,
        leftStick = leftStick,
        rightStick = rightStick
    )

    @Test
    fun zeroConfigDiscoverySpatialBoundsAndAllInteractiveWidgetsWorkOutOfTheBox() {
        val manager = GamepadManager()
        val window = Window("Game Menu")

        var playClicked = 0
        val playBtn = Button("Play").apply {
            setBounds(Rect(20, 20, 120, 40))
            onClick = { playClicked++ }
        }
        val toggleBtn = ToggleButton("Mute").apply {
            setBounds(Rect(180, 20, 120, 40))
        }
        val checkBox = CheckBox("Invert Y", checked = false).apply {
            setBounds(Rect(20, 80, 120, 30))
        }
        val switchCtrl = Switch("VSync", checked = true).apply {
            setBounds(Rect(180, 80, 120, 30))
        }
        val radioEasy = RadioButton("easy", "Easy", checked = true).apply { setBounds(Rect(20, 130, 80, 28)) }
        val radioHard = RadioButton("hard", "Hard", checked = false).apply { setBounds(Rect(120, 130, 80, 28)) }
        val radioGroup = RadioGroup().apply {
            addRadioButton(radioEasy)
            addRadioButton(radioHard)
        }
        val qualitySelect = Select(
            options = listOf(SelectOption("low"), SelectOption("high"), SelectOption("ultra")),
            selectedValue = "low"
        ).apply {
            setBounds(Rect(20, 180, 140, 34))
        }
        val volumeSlider = Slider(value = 50.0, min = 0.0, max = 100.0, step = 10.0).apply {
            setBounds(Rect(20, 230, 200, 32))
        }
        var lastDropdownChoice = ""
        val dropdown = DropdownButton("Actions").apply {
            setBounds(Rect(20, 280, 140, 36))
            addItem("save", "Save")
            addItem("load", "Load")
            onItemSelected = { lastDropdownChoice = it }
        }
        val scrollArea = ScrollView()
        val scrollInner = VStack()
        val footerBtn = Button("Back").apply { setBounds(Rect(20, 340, 120, 40)) }
        scrollInner.addChild(Label("Non-focusable Heading"))
        scrollInner.addChild(playBtn)
        scrollInner.addChild(toggleBtn)
        scrollInner.addChild(checkBox)
        scrollInner.addChild(switchCtrl)
        scrollInner.addChild(radioGroup)
        scrollInner.addChild(qualitySelect)
        scrollInner.addChild(volumeSlider)
        scrollInner.addChild(dropdown)
        scrollInner.addChild(footerBtn)
        scrollArea.addChild(scrollInner)

        val tabs = TabContainer()
        tabs.addTab(Tab(title = "General"), scrollArea)
        tabs.addTab(Tab(title = "Audio"), Label("Audio Panel"))
        val rootContainer = Container()
        rootContainer.addChild(tabs)
        window.setContent(rootContainer)

        val strategy = DefaultSpatialGamepadUiNavigationStrategy()
        val navigator = window.enableGamepadUiNavigation(
            gamepadManager = manager,
            strategy = strategy,
            autoStart = false
        )

        // First poll discovers first tab / focusable widget and highlights focus+hover
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        assertTrue(navigator.focusedComponent != null)
        assertTrue(navigator.focusedComponent!!.isFocused)
        assertTrue(navigator.focusedComponent!!.isHovered)

        // Bumper RB cycles TabContainer to Audio tab; LB cycles back to General
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.RIGHT_BUMPER.bitMask))
        navigator.step(1f / 60f)
        assertEquals(1, tabs.selectedIndex)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.LEFT_BUMPER.bitMask))
        navigator.step(1f / 60f)
        assertEquals(0, tabs.selectedIndex)

        // Explicitly focus Play button & move RIGHT spatially to ToggleButton, then LEFT back to Play
        navigator.setFocusedComponent(playBtn)
        assertSame(playBtn, navigator.focusedComponent)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.DPAD_RIGHT.bitMask))
        navigator.step(1f / 60f)
        assertSame(toggleBtn, navigator.focusedComponent)
        assertFalse(playBtn.isFocused)
        assertTrue(toggleBtn.isFocused)

        // Activate ToggleButton with A
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask))
        navigator.step(1f / 60f)
        assertTrue(toggleBtn.isSelected)

        // Move DOWN spatially to Switch, then press A -> flips checked state
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.DPAD_DOWN.bitMask))
        navigator.step(1f / 60f)
        assertSame(switchCtrl, navigator.focusedComponent)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask))
        navigator.step(1f / 60f)
        assertFalse(switchCtrl.isChecked)

        // Activate PlayButton with A
        navigator.setFocusedComponent(playBtn)
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask))
        navigator.step(1f / 60f)
        assertEquals(1, playClicked)

        // Activate RadioButton in RadioGroup -> updates selectedValue
        navigator.setFocusedComponent(radioHard)
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask))
        navigator.step(1f / 60f)
        assertEquals("hard", radioGroup.selectedValue)

        // Activate Select -> cycles option low -> high
        navigator.setFocusedComponent(qualitySelect)
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask))
        navigator.step(1f / 60f)
        assertEquals("high", qualitySelect.selectedValue)

        // Activate DropdownButton -> selects first item
        navigator.setFocusedComponent(dropdown)
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask))
        navigator.step(1f / 60f)
        assertEquals("save", lastDropdownChoice)

        // Slider focused: DPAD_RIGHT increases value 50 -> 60 without leaving Slider focus
        navigator.setFocusedComponent(volumeSlider)
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.DPAD_RIGHT.bitMask))
        navigator.step(1f / 60f)
        assertSame(volumeSlider, navigator.focusedComponent)
        assertEquals(60.0, volumeSlider.value, 1e-4)

        // Right Stick DOWN scrolls nearest ScrollView positively along Y
        manager.setSyntheticController(padFrame(rightStick = GamepadAxis2D(0f, -1f)))
        navigator.step(0.1f)
        assertTrue(strategy.trackedScrollOffset(scrollArea).y > 40f)
    }

    @Test
    fun modalDialogScopesFocusAndBButtonDismissesDialog() {
        val manager = GamepadManager()
        val window = Window("Main")
        val rootStack = VStack()
        val mainBtn = Button("Behind Modal")
        rootStack.addChild(mainBtn)
        window.setContent(rootStack)

        val dialog = DialogWindow(title = "Confirm", modal = true)
        var okCount = 0
        val okBtn = Button("OK").apply { onClick = { okCount++ } }
        dialog.addChild(okBtn)

        val navigator = GamepadUiNavigator(root = window, gamepadManager = manager)
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        assertSame(mainBtn, navigator.focusedComponent)

        // Open modal dialog -> focus automatically moves into dialog; behind-modal widgets unreachable
        dialog.open(window)
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.DPAD_DOWN.bitMask))
        navigator.step(1f / 60f)
        assertSame(okBtn, navigator.focusedComponent)
        assertEquals(listOf<AbstractComponent>(okBtn), navigator.strategy.discoverFocusableComponents(window))

        // Activate OK inside modal
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask))
        navigator.step(1f / 60f)
        assertEquals(1, okCount)

        // Press B -> closes modal dialog and restores focus back to mainBtn
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.B.bitMask))
        navigator.step(1f / 60f)
        assertFalse(dialog.isWindowShown)
        assertSame(mainBtn, navigator.focusedComponent)
    }

    @Test
    fun customUserStrategyReimplementationOverridesAllNavigationRules() {
        val manager = GamepadManager()
        val root = VStack()
        val b1 = Button("One")
        val b2 = Button("Two")
        val b3 = Button("Three")
        root.addChild(b1)
        root.addChild(b2)
        root.addChild(b3)

        var customActivatedCount = 0
        var customCancelCount = 0
        val customStrategy = object : GamepadUiNavigationStrategy {
            override fun discoverFocusableComponents(root: AbstractContainer): List<AbstractComponent> =
                listOf(b1, b2, b3)

            override fun resolveInitialFocus(
                root: AbstractContainer,
                candidates: List<AbstractComponent>
            ): AbstractComponent = b2

            override fun resolveNextFocus(
                current: AbstractComponent?,
                direction: GamepadNavDirection,
                candidates: List<AbstractComponent>
            ): AbstractComponent = when (direction) {
                GamepadNavDirection.LEFT -> b1
                GamepadNavDirection.RIGHT -> b3
                else -> current ?: b2
            }

            override fun onFocusChanged(
                previous: AbstractComponent?,
                current: AbstractComponent?,
                root: AbstractContainer
            ) {
                previous?.isFocused = false
                current?.isFocused = true
            }

            override fun onActivate(
                focused: AbstractComponent,
                root: AbstractContainer,
                frame: GamepadFrameSnapshot
            ): Boolean {
                customActivatedCount++
                return true
            }

            override fun onCancel(
                focused: AbstractComponent?,
                root: AbstractContainer,
                frame: GamepadFrameSnapshot
            ): Boolean {
                customCancelCount++
                return true
            }

            override fun onTabCycle(root: AbstractContainer, delta: Int, frame: GamepadFrameSnapshot): Boolean = false

            override fun onContinuousInput(
                focused: AbstractComponent?,
                root: AbstractContainer,
                frame: GamepadFrameSnapshot,
                deltaSeconds: Float
            ): Boolean = false
        }

        val navigator = GamepadUiNavigator(root = root, gamepadManager = manager, strategy = customStrategy)
        manager.setSyntheticController(padFrame())
        navigator.step(1f / 60f)
        assertSame(b2, navigator.focusedComponent)

        manager.setSyntheticController(padFrame(leftStick = GamepadAxis2D(-0.9f, 0f)))
        navigator.step(1f / 60f)
        assertSame(b1, navigator.focusedComponent)

        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.A.bitMask or GamepadButton.B.bitMask))
        navigator.step(1f / 60f)
        assertEquals(1, customActivatedCount)
        assertEquals(1, customCancelCount)
    }
}
