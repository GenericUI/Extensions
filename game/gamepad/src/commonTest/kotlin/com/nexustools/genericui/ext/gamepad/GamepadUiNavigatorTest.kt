package com.nexustools.genericui.ext.gamepad

import com.nexustools.genericui.animation.Animator
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
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.gamepad.ui.DefaultSpatialGamepadUiNavigationStrategy
import com.nexustools.genericui.ext.gamepad.ui.GamepadNavDirection
import com.nexustools.genericui.ext.gamepad.ui.GamepadUiNavigationStrategy
import com.nexustools.genericui.ext.gamepad.ui.GamepadUiNavigator
import com.nexustools.genericui.ext.gamepad.ui.enableGamepadUiNavigation
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GamepadUiNavigatorTest {

    @BeforeTest
    @AfterTest
    fun cleanAnimator() {
        Animator.cancelAllAnimations()
    }
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
        val previousFactory = GenericUI.activeFactory
        GenericUI.initialize(FakeGamepadDialogTestFactory())
        try {
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
        } finally {
            GenericUI.resetForTests()
            GenericUI.activeFactory = previousFactory
        }
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

    @Test
    fun vblankDrivenStartAndStopRepeatsNavigationWhenRunningAndStaysIdleWhenStopped() {
        val manager = GamepadManager()
        val root = VStack()
        val b1 = Button("One").apply { setBounds(Rect(0, 0, 100, 40)) }
        val b2 = Button("Two").apply { setBounds(Rect(0, 50, 100, 40)) }
        val b3 = Button("Three").apply { setBounds(Rect(0, 100, 100, 40)) }
        root.addChild(b1)
        root.addChild(b2)
        root.addChild(b3)

        val navigator = GamepadUiNavigator(root = root, gamepadManager = manager)
        assertFalse(navigator.isRunning)
        assertFalse(navigator.isWatchingVBlank)

        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.DPAD_DOWN.bitMask))
        Animator.advanceTimeBy(64.0)
        assertSame(null, navigator.focusedComponent, "Stopped navigator must stay idle on VBlank")

        manager.setSyntheticController(padFrame())
        navigator.start()
        assertTrue(navigator.isRunning)
        assertTrue(navigator.isWatchingVBlank)

        // Connected neutral tick resolves initial focus onto b1
        Animator.advanceTimeBy(32.0)
        assertSame(b1, navigator.focusedComponent)

        // Pressing & holding DPAD_DOWN moves b1 -> b2, and holding through repeat interval advances b2 -> b3
        manager.setSyntheticController(padFrame(buttonsMask = GamepadButton.DPAD_DOWN.bitMask))
        Animator.advanceTimeBy(32.0)
        assertSame(b2, navigator.focusedComponent)

        Animator.advanceTimeBy(400.0)
        assertSame(b3, navigator.focusedComponent)

        navigator.stop()
        assertFalse(navigator.isRunning)
        assertFalse(navigator.isWatchingVBlank)

        // While stopped, VBlank ticks do no work (0% CPU idle)
        Animator.advanceTimeBy(300.0)
        assertSame(b3, navigator.focusedComponent)

        navigator.detach()
        assertFalse(navigator.isWatchingVBlank)
    }

    private open class StubNativeImpl(val tag: String) : NativeImpl<Any> {
        override val nativeWidget: Any = tag
        override val componentClassPrefix: String = tag
        override var isDisposed: Boolean = false
        private var className: String = ""
        private var type: BootstrapType? = null
        private var outline: Boolean = false
        private var size: BootstrapSize? = null
        private var bgType: BootstrapType? = null
        private var textColorType: BootstrapType? = null
        private var borderType: BootstrapType? = null
        private var textAlign: TextAlignment? = null
        private var fontWeight: FontWeight? = null
        private var textTransform: TextTransform? = null
        private var textOverflow: TextOverflow? = null
        private var borderRadius: BorderRadiusStyle? = null
        private var shadow: ShadowElevation? = null
        private var display: DisplayMode? = null
        private var flexDir: FlexDirection? = null
        private var flexWrap: FlexWrap? = null
        private var justify: JustifyContent? = null
        private var alignItems: AlignItems? = null
        private var alignSelf: AlignSelf? = null
        private var overflow: OverflowBehavior? = null
        private var position: PositionMode? = null
        private var marginAll: SpacingScale? = null
        private var paddingAll: SpacingScale? = null
        private var visible: Boolean = true
        private var enabled: Boolean = true
        private var opacity: Float = 1f
        private var tooltip: String? = null

        override fun dispose() { isDisposed = true }
        override fun setClass(className: String) { this.className = className }
        override fun getClass(): String = className
        override fun addClass(className: String) {}
        override fun removeClass(className: String) {}
        override fun hasClass(className: String): Boolean = false
        override fun setType(type: BootstrapType?) { this.type = type }
        override fun getType(): BootstrapType? = type
        override fun setOutline(outline: Boolean) { this.outline = outline }
        override fun isOutline(): Boolean = outline
        override fun setSize(size: BootstrapSize?) { this.size = size }
        override fun getSize(): BootstrapSize? = size
        override fun setBackgroundType(type: BootstrapType?) { bgType = type }
        override fun getBackgroundType(): BootstrapType? = bgType
        override fun setTextColorType(type: BootstrapType?) { textColorType = type }
        override fun getTextColorType(): BootstrapType? = textColorType
        override fun setBorderType(type: BootstrapType?) { borderType = type }
        override fun getBorderType(): BootstrapType? = borderType
        override fun setTextAlignment(alignment: TextAlignment?) { textAlign = alignment }
        override fun getTextAlignment(): TextAlignment? = textAlign
        override fun setFontWeight(weight: FontWeight?) { fontWeight = weight }
        override fun getFontWeight(): FontWeight? = fontWeight
        override fun setTextTransform(transform: TextTransform?) { textTransform = transform }
        override fun getTextTransform(): TextTransform? = textTransform
        override fun setTextOverflow(overflow: TextOverflow?) { textOverflow = overflow }
        override fun getTextOverflow(): TextOverflow? = textOverflow
        override fun setBorderRadius(radius: BorderRadiusStyle?) { borderRadius = radius }
        override fun getBorderRadius(): BorderRadiusStyle? = borderRadius
        override fun setShadow(shadow: ShadowElevation?) { this.shadow = shadow }
        override fun getShadow(): ShadowElevation? = shadow
        override fun setDisplay(display: DisplayMode?) { this.display = display }
        override fun getDisplay(): DisplayMode? = display
        override fun setFlexDirection(direction: FlexDirection?) { flexDir = direction }
        override fun getFlexDirection(): FlexDirection? = flexDir
        override fun setFlexWrap(wrap: FlexWrap?) { flexWrap = wrap }
        override fun getFlexWrap(): FlexWrap? = flexWrap
        override fun setJustifyContent(justify: JustifyContent?) { this.justify = justify }
        override fun getJustifyContent(): JustifyContent? = justify
        override fun setAlignItems(align: AlignItems?) { alignItems = align }
        override fun getAlignItems(): AlignItems? = alignItems
        override fun setAlignSelf(align: AlignSelf?) { alignSelf = align }
        override fun getAlignSelf(): AlignSelf? = alignSelf
        override fun setOverflow(overflow: OverflowBehavior?) { this.overflow = overflow }
        override fun getOverflow(): OverflowBehavior? = overflow
        override fun setPosition(position: PositionMode?) { this.position = position }
        override fun getPosition(): PositionMode? = position
        override fun setMarginAll(scale: SpacingScale?) { marginAll = scale }
        override fun getMarginAll(): SpacingScale? = marginAll
        override fun setPaddingAll(scale: SpacingScale?) { paddingAll = scale }
        override fun getPaddingAll(): SpacingScale? = paddingAll
        override fun setVisible(visible: Boolean) { this.visible = visible }
        override fun isVisible(): Boolean = visible
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun isEnabled(): Boolean = enabled
        override fun setOpacity(opacity: Float) { this.opacity = opacity }
        override fun getOpacity(): Float = opacity
        override fun setTooltipText(tooltip: String?) { this.tooltip = tooltip }
        override fun getTooltipText(): String? = tooltip
        override fun setPreferredSize(size: Size?) {}
        override fun getBounds(): Rect = Rect.ZERO
        override fun setPadding(padding: EdgeInsets) {}
        override fun setMargin(margin: EdgeInsets) {}
        override fun applyComputedStyle(style: ComputedStyle) {}
        override fun setOnClick(listener: (() -> Unit)?) {}
        override fun setOnDoubleClick(listener: (() -> Unit)?) {}
        override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
        override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
        override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}
    }

    private class FakeContainerNativeImpl(prefix: String) : StubNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakeButtonNativeImpl(prefix: String) : StubNativeImpl(prefix), ButtonNativeImpl<Any> {
        private var label: String = ""
        private var iconName: String? = null
        override fun setText(text: String) { label = text }
        override fun getText(): String = label
        override fun setIcon(icon: String?) { iconName = icon }
        override fun getIcon(): String? = iconName
    }

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        private var title: String = "GenericUI"
        private var size: Size = Size(800, 600)
        private var state: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { this.size = size }
        override fun getWindowSize(): Size = size
        override fun setWindowState(state: WindowState) { this.state = state }
        override fun getWindowState(): WindowState = state
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) { this.fullscreen = fullscreen }
        override fun isFullscreen(): Boolean = fullscreen
        override fun applyTitleBarInstructions(titleBar: TitleBar?) {}
        override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}
        override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {}
        override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {}
        override fun showWindow() {}
        override fun closeWindow() {}
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
    }

    private class FakeDialogWindowNativeImpl : StubNativeImpl("dialog"), DialogWindowNativeImpl<Any> {
        private var title: String = ""
        private var presentationStyle: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
        private var onClosed: (() -> Unit)? = null
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setPresentationStyle(style: DialogPresentationStyle) { this.presentationStyle = style }
        override fun getPresentationStyle(): DialogPresentationStyle = presentationStyle
        override fun setModal(modal: Boolean) {}
        override fun setFullscreen(fullscreen: Boolean) {}
        override fun showDialog(ownerWindow: WindowNativeImpl<*>?) {}
        override fun closeDialog() {}
        override fun setOnDialogClosed(listener: (() -> Unit)?) { onClosed = listener }
    }

    private class FakeGamepadDialogTestFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in GamepadUiNavigatorTest")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override val supportsDialogWindows: Boolean = true
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = unsupported()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = unsupported()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = unsupported()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unsupported()
        override fun createDockArea(): DockAreaNativeImpl<*> = unsupported()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unsupported()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unsupported()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = FakeContainerNativeImpl(classPrefix)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unsupported()
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = FakeButtonNativeImpl(classPrefix)
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unsupported()
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = unsupported()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unsupported()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unsupported()
        override fun createTab(closable: Boolean): TabNativeImpl<*> = unsupported()
        override fun createTabContainer(): TabContainerNativeImpl<*> = unsupported()
        override fun createNavbar(): NavbarNativeImpl<*> = unsupported()
        override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = unsupported()
        override fun createProgressBar(): ProgressBarNativeImpl<*> = unsupported()
        override fun createSpinner(): SpinnerNativeImpl<*> = unsupported()
        override fun createPopover(): PopoverNativeImpl<*> = unsupported()
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = FakeDialogWindowNativeImpl()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = unsupported()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = unsupported()
        override fun createLazyList(): LazyListNativeImpl<*> = unsupported()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unsupported()
        override fun createTreeView(): TreeViewNativeImpl<*> = unsupported()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unsupported()
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = object : NetworkMonitorNativeImpl {
            override var isDisposed: Boolean = false
            override fun dispose() { isDisposed = true }
            override fun isOnline(): Boolean = true
            override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {}
        }
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }
}
