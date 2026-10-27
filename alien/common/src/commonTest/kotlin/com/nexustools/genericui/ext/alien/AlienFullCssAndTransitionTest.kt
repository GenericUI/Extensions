package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.feedback.ProgressBar
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.css.*
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.ext.alien.impl.ButtonAlienImpl
import com.nexustools.genericui.ext.alien.impl.CheckboxAlienImpl
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.ext.alien.impl.ProgressBarAlienImpl
import com.nexustools.genericui.ext.alien.impl.RangeSliderAlienImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.*
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AlienFullCssAndTransitionTest {

    private class TestAlienHostFactory : NativeFactory {
        override val backendName: String = "test-alien-host"
        private val delegate = AlienFactory(this)
        override fun createApplication(): ApplicationNativeImpl<*> = throw UnsupportedOperationException()
        override fun createWindow(): WindowNativeImpl<*> = throw UnsupportedOperationException()
        override fun createContainer(classPrefix: String) = delegate.createContainer(classPrefix)
        override fun createContainer(classPrefix: String, tagName: String) = delegate.createContainer(classPrefix, tagName)
        override fun createMenuBar() = delegate.createMenuBar()
        override fun createMenu() = delegate.createMenu()
        override fun createMenuItem(checkable: Boolean, radio: Boolean) = delegate.createMenuItem(checkable, radio)
        override fun createContextMenu() = delegate.createContextMenu()
        override fun createSplitPane() = delegate.createSplitPane()
        override fun createDockWorkspace() = delegate.createDockWorkspace()
        override fun createDockArea() = delegate.createDockArea()
        override fun createDockGroup() = delegate.createDockGroup()
        override fun createDockPanel() = delegate.createDockPanel()
        override fun createDockWindow() = delegate.createDockWindow()
        override fun createToolPalette() = delegate.createToolPalette()
        override fun createBookContainer() = delegate.createBookContainer()
        override fun createScrollView() = delegate.createScrollView()
        override fun createDivider() = delegate.createDivider()
        override fun createText(tagName: String, classPrefix: String) = delegate.createText(tagName, classPrefix)
        override fun createMarkdownView() = delegate.createMarkdownView()
        override fun createImage() = delegate.createImage()
        override fun createTable() = delegate.createTable()
        override fun createButton(classPrefix: String) = delegate.createButton(classPrefix)
        override fun createDropdown() = delegate.createDropdown()
        override fun createTextInput(multiline: Boolean) = delegate.createTextInput(multiline)
        override fun createSelect() = delegate.createSelect()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean) = delegate.createCheckbox(switchStyle, radioStyle)
        override fun createRangeSlider() = delegate.createRangeSlider()
        override fun createCollapse() = delegate.createCollapse()
        override fun createTab(closable: Boolean) = delegate.createTab(closable)
        override fun createTabContainer() = delegate.createTabContainer()
        override fun createNavbar() = delegate.createNavbar()
        override fun createBottomNavigationBar() = delegate.createBottomNavigationBar()
        override fun createProgressBar() = delegate.createProgressBar()
        override fun createSpinner() = delegate.createSpinner()
        override fun createPopover() = delegate.createPopover()
        override fun createDialogWindow() = delegate.createDialogWindow()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = throw UnsupportedOperationException()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = throw UnsupportedOperationException()
        override fun createLazyList() = delegate.createLazyList()
        override fun createLazyGrid() = delegate.createLazyGrid()
        override fun createTreeView() = delegate.createTreeView()
        override fun createPermissionManager(): PermissionManagerNativeImpl = throw UnsupportedOperationException()
        override fun createSystemTray(): SystemTrayNativeImpl = throw UnsupportedOperationException()
        override fun createNotificationManager(): NotificationManagerNativeImpl = throw UnsupportedOperationException()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = throw UnsupportedOperationException()
    }

    @BeforeTest
    fun setUp() {
        AlienExtension.useSystemCanvasRenderer()
        GenericUI.initialize(TestAlienHostFactory(), "AlienFullCssTest")
    }

    @AfterTest
    fun tearDown() {
        AlienExtension.useSystemCanvasRenderer()
    }

    @Test
    fun testBootstrapClassesAreInvisibleToGetClassesAndHasClassOnAlienImpl() {
        val alienRoot = AlienContainer().apply {
            widthPx = 600
            heightPx = 400
        }
        alienRoot.ensureNativeImpl()

        val button = Button("Action").apply {
            getBootstrap().apply {
                type = BootstrapType.DANGER
                size = BootstrapSize.LARGE
                outline = true
                borderRadius = BorderRadiusStyle.PILL
                shadow = ShadowElevation.LARGE
                marginAll = SpacingScale.THREE
                paddingX = SpacingScale.FOUR
            }
            addClass("custom-action", "toolbar-item")
        }
        alienRoot.addChild(button)

        val btnImpl = assertIs<ButtonAlienImpl>(button.nativeImpl)

        // Custom classes are visible
        assertEquals(setOf("custom-action", "toolbar-item"), button.getClasses())
        assertEquals(setOf("custom-action", "toolbar-item"), btnImpl.getClasses())
        assertTrue(button.hasClass("custom-action"))
        assertTrue(btnImpl.hasClass("toolbar-item"))

        // Bootstrap enum classes are 100% invisible to getClasses() / hasClass() / getClass()
        assertFalse(button.hasClass("btn"))
        assertFalse(button.hasClass("btn-danger"))
        assertFalse(button.hasClass("btn-outline-danger"))
        assertFalse(button.hasClass("btn-lg"))
        assertFalse(button.hasClass("rounded-pill"))
        assertFalse(button.hasClass("shadow-lg"))
        assertFalse(button.hasClass("m-3"))
        assertFalse(btnImpl.hasClass("btn-danger"))
        assertFalse(btnImpl.hasClass("rounded-pill"))

        // Yet AlienCssStyleResolver still resolves the Bootstrap enum properties from styleSnapshot!
        val resolved = btnImpl.node.resolveVisualStyle()
        assertEquals(9999f, resolved.borderRadiusPx)
        assertEquals(16, resolved.margin.top)
        assertEquals(24, resolved.padding.left)
        assertEquals(24, resolved.padding.right)

        btnImpl.removeClass("custom-action")
        assertEquals(setOf("toolbar-item"), btnImpl.getClasses())
        assertEquals("toolbar-item", btnImpl.getClass())
    }

    @Test
    fun testFullComponentStyleAndBootstrapStyleResolutionAndImportantOverrides() {
        val alienRoot = AlienContainer().apply {
            widthPx = 800
            heightPx = 600
        }
        alienRoot.ensureNativeImpl()

        val card = Container("card").apply {
            getBootstrap().apply {
                width = BootstrapWidth.W_75
                height = BootstrapHeight.H_50
                borderWidth = BootstrapBorderWidth.BORDER_3
                opacity = BootstrapOpacity.OPACITY_75
                fontSize = BootstrapFontSize.FS_4
                lineHeight = BootstrapLineHeight.LH_LG
                flexGrow = BootstrapFlexGrow.GROW_1
                flexShrink = BootstrapFlexShrink.SHRINK_0
                gap = SpacingScale.THREE
                rowGap = SpacingScale.TWO
                columnGap = SpacingScale.FOUR
                marginX = SpacingScale.AUTO
                floatSide = BootstrapFloat.END
                userSelect = BootstrapUserSelect.NONE
                objectFit = BootstrapObjectFit.COVER
                aspectRatio = BootstrapAspectRatio.RATIO_16X9
            }
            getStyle().apply {
                backgroundColor = RgbaColor(20, 40, 80, 255)
                textColor = RgbaColor(240, 245, 250, 255)
                accentColor = RgbaColor(255, 120, 0, 255)
                borderColor = RgbaColor(80, 160, 255, 255)
                borderRadius = CssCornerRadii.px(10, 20, 30, 40)
                boxShadow = CssShadowList.of(CssShadow.px(2f, 4f, 8f, 1f, RgbaColor(0, 0, 0, 120)))
                textShadow = CssShadowList.of(CssShadow.px(1f, 2f, 3f, 0f, RgbaColor(0, 0, 0, 200)))
                letterSpacing = CssLength.px(2f)
                textDecoration = CssTextDecoration.UNDERLINE
                wordBreak = CssWordBreak.BREAK_ALL
                verticalAlign = CssVerticalAlign.MIDDLE
                boxSizing = CssBoxSizing.CONTENT_BOX
                cursor = CssCustomCursor(urlOrPath = "cursors/brush.png", hotspotX = 4, hotspotY = 4, fallback = CssCursor.CROSSHAIR)
                filter = CssFilter(blur = CssLength.px(4f), brightness = 1.2f)
                backdropFilter = CssFilter.blur(CssLength.px(8f))
            }
        }
        alienRoot.addChild(card)
        val cardImpl = assertIs<ContainerAlienImpl>(card.nativeImpl)
        val style = cardImpl.node.resolveVisualStyle()

        assertEquals(RgbaColor(20, 40, 80, 255), style.backgroundColor)
        assertEquals(RgbaColor(240, 245, 250, 255), style.textColor)
        assertEquals(RgbaColor(255, 120, 0, 255), style.accentColor)
        assertEquals(RgbaColor(80, 160, 255, 255), style.borderColor)
        assertEquals(3f, style.borderWidthPx)
        assertEquals(0.75f, style.opacity, 0.001f)
        assertEquals(24f, style.fontSizePx, 0.01f)
        assertEquals(48f, assertNotNull(style.lineHeightPx), 0.01f)
        assertEquals(2f, style.letterSpacingPx, 0.01f)
        assertEquals(CssTextDecoration.UNDERLINE, style.textDecoration)
        assertEquals(CssWordBreak.BREAK_ALL, style.wordBreak)
        assertEquals(CssVerticalAlign.MIDDLE, style.verticalAlign)
        assertEquals(CssBoxSizing.CONTENT_BOX, style.boxSizing)
        assertEquals("75%", style.widthCss)
        assertEquals("50%", style.heightCss)
        assertEquals(1f, style.flexGrow)
        assertEquals(0f, style.flexShrink)
        assertEquals(16f, style.gapPx)
        assertEquals(8f, style.rowGapPx)
        assertEquals(24f, style.columnGapPx)
        assertTrue(style.marginLeftAuto)
        assertTrue(style.marginRightAuto)
        assertEquals("right", style.floatMode)
        assertEquals(CssUserSelect.NONE, style.userSelect)
        assertEquals(ObjectFit.COVER, style.objectFit)
        assertEquals(16f / 9f, assertNotNull(style.aspectRatio), 0.001f)
        assertEquals(CssCornerRadii.px(10, 20, 30, 40), style.cornerRadii)
        assertNotNull(style.boxShadowList)
        assertNotNull(style.textShadow)
        assertIs<CssCustomCursor>(style.cursor)
        assertEquals(4f, assertNotNull(style.filter?.blur).value)
        assertEquals(8f, assertNotNull(style.backdropFilter?.blur).value)

        // Verify !important priority between ComputedStyle and inline ComponentStyle
        cardImpl.applyComputedStyle(
            ComputedStyle(
                backgroundColor = RgbaColor(200, 10, 10, 255),
                rawDeclarations = mapOf("background-color" to "rgb(200, 10, 10)"),
                importantProperties = setOf("background-color")
            )
        )
        // Non-important inline backgroundColor yields to !important ComputedStyle
        assertEquals(RgbaColor(200, 10, 10, 255), cardImpl.node.resolveVisualStyle().backgroundColor)

        // Marking inline backgroundColor as !important overrides ComputedStyle !important
        card.getStyle().setBackgroundColor(RgbaColor(10, 220, 90, 255), important = true)
        assertEquals(RgbaColor(10, 220, 90, 255), cardImpl.node.resolveVisualStyle().backgroundColor)
    }

    @Test
    fun testAlienTransitionAnimatorInterpolatesAllCssProperties() {
        val node = AlienWidgetNode(componentClassPrefix = "card")
        node.inlineComponentStyle = ComponentStyle().apply {
            backgroundColor = RgbaColor(0, 0, 0, 255)
            textColor = RgbaColor(100, 100, 100, 255)
            accentColor = RgbaColor(0, 100, 200, 255)
            borderWidthPx = 1f
            borderRadiusPx = 4f
            fontSizePx = 14f
            letterSpacing = CssLength.px(0f)
            opacity = 0.2f
            width = CssLength.px(100f)
            height = CssLength.px(50f)
            transform = CssTransform2D(translateX = 0f, translateY = 0f, scaleX = 1f, scaleY = 1f)
            filter = CssFilter(blur = CssLength.px(0f), brightness = 1.0f)
            transitions = listOf(
                CssTransitionSpec(
                    property = "all",
                    durationMs = 200f,
                    timingFunction = CssTimingFunction.LINEAR,
                    delayMs = 0f
                )
            )
        }

        // Initial frame establishes baseline target style
        val initial = node.resolveVisualStyle()
        assertEquals(RgbaColor(0, 0, 0, 255), initial.backgroundColor)
        assertEquals(14f, initial.fontSizePx, 0.01f)
        assertEquals("100px", initial.widthCss)

        // Mutate target style to trigger transitions across all properties
        node.inlineComponentStyle = ComponentStyle().apply {
            backgroundColor = RgbaColor(200, 100, 50, 255)
            textColor = RgbaColor(200, 200, 200, 255)
            accentColor = RgbaColor(200, 200, 0, 255)
            borderWidthPx = 5f
            borderRadiusPx = 20f
            fontSizePx = 26f
            letterSpacing = CssLength.px(8f)
            opacity = 1.0f
            width = CssLength.px(300f)
            height = CssLength.px(150f)
            transform = CssTransform2D(translateX = 40f, translateY = 20f, scaleX = 2f, scaleY = 2f)
            filter = CssFilter(blur = CssLength.px(10f), brightness = 2.0f)
            transitions = listOf(
                CssTransitionSpec(
                    property = "all",
                    durationMs = 200f,
                    timingFunction = CssTimingFunction.LINEAR,
                    delayMs = 0f
                )
            )
        }

        // Initiate transition on frame 0 and step 100ms (50% progress on linear curve)
        node.resolveVisualStyle()
        assertTrue(node.transitionAnimator.isAnimating)
        assertTrue(node.transitionAnimator.step(100f))

        val mid = node.resolveVisualStyle()
        assertEquals(100, mid.backgroundColor.r)
        assertEquals(50, mid.backgroundColor.g)
        assertEquals(25, mid.backgroundColor.b)
        assertEquals(150, mid.textColor.r)
        assertEquals(100, mid.accentColor.r)
        assertEquals(3f, mid.borderWidthPx, 0.05f)
        assertEquals(12f, mid.borderRadiusPx, 0.05f)
        assertEquals(20f, mid.fontSizePx, 0.05f)
        assertEquals(4f, mid.letterSpacingPx, 0.05f)
        assertEquals(0.6f, mid.opacity, 0.05f)
        assertEquals("200px", mid.widthCss)
        assertEquals("100px", mid.heightCss)
        assertEquals(20f, mid.transform2D.translateX, 0.05f)
        assertEquals(1.5f, mid.transform2D.scaleX, 0.05f)
        assertEquals(5f, assertNotNull(mid.filter?.blur).value, 0.05f)
        assertEquals(1.5f, assertNotNull(mid.filter?.brightness), 0.05f)

        // Step remaining 100ms to complete transition
        node.transitionAnimator.step(100f)
        assertFalse(node.transitionAnimator.isAnimating)
        val end = node.resolveVisualStyle()
        assertEquals(RgbaColor(200, 100, 50, 255), end.backgroundColor)
        assertEquals(5f, end.borderWidthPx, 0.01f)
        assertEquals(20f, end.borderRadiusPx, 0.01f)
        assertEquals(26f, end.fontSizePx, 0.01f)
        assertEquals("300px", end.widthCss)
    }

    @Test
    fun testFormControlsUseResolvedAccentColor() {
        val alienRoot = AlienContainer().apply {
            widthPx = 400
            heightPx = 300
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val customAccent = RgbaColor(230, 40, 140, 255)
        val checkbox = CheckBox("Accent Check", checked = true).apply {
            getStyle().accentColor = customAccent
        }
        val slider = RangeSlider(minValue = 75.0, min = 0.0, max = 100.0).apply {
            getStyle().accentColor = customAccent
        }
        val progress = ProgressBar(0.8f).apply {
            getStyle().accentColor = customAccent
        }
        alienRoot.addChild(checkbox)
        alienRoot.addChild(slider)
        alienRoot.addChild(progress)

        val checkImpl = assertIs<CheckboxAlienImpl>(checkbox.nativeImpl)
        val sliderImpl = assertIs<RangeSliderAlienImpl>(slider.nativeImpl)
        val progressImpl = assertIs<ProgressBarAlienImpl>(progress.nativeImpl)

        assertEquals(customAccent, checkImpl.node.resolveVisualStyle().accentColor)
        assertEquals(customAccent, sliderImpl.node.resolveVisualStyle().accentColor)
        assertEquals(customAccent, progressImpl.node.resolveVisualStyle().accentColor)

        val repainted = session.forceFullRepaint(0f)
        assertTrue(repainted.isNotEmpty())
    }

    @Test
    fun testAlienPrecompiledBootstrapStyleSheetAndBootstrapTypeNone() {
        assertTrue(AlienPrecompiledBootstrapStyleSheet.compiledRules.isNotEmpty())
        assertEquals(
            AlienPrecompiledBootstrapStyleSheet,
            StyleSheetRegistry.resolveByResourceId(IAlien.BOOTSTRAP_STYLE_SHEET_ID)
        )

        val alienRoot = AlienContainer().apply {
            widthPx = 640
            heightPx = 480
        }
        alienRoot.ensureNativeImpl()

        // 1. Default Button has BootstrapType.NONE and remains un-tinted
        val plainButton = Button("Plain")
        alienRoot.addChild(plainButton)
        assertEquals(BootstrapType.NONE, plainButton.getBootstrap().type)
        val plainImpl = assertIs<ButtonAlienImpl>(plainButton.nativeImpl)
        assertEquals(RgbaColor.TRANSPARENT, plainImpl.node.resolveVisualStyle().backgroundColor)

        // 2. Semantic Button (.btn-danger, .btn-primary:hover, .btn-outline-success:hover)
        val dangerBtn = Button("Delete").apply {
            getBootstrap().type = BootstrapType.DANGER
        }
        alienRoot.addChild(dangerBtn)
        val dangerImpl = assertIs<ButtonAlienImpl>(dangerBtn.nativeImpl)
        assertEquals(AlienBootstrapThemeTokens.DANGER, dangerImpl.node.resolveVisualStyle().backgroundColor)

        val primaryBtn = Button("Save").apply {
            getBootstrap().type = BootstrapType.PRIMARY
        }
        alienRoot.addChild(primaryBtn)
        val primaryImpl = assertIs<ButtonAlienImpl>(primaryBtn.nativeImpl)
        primaryImpl.node.hoveredState = true
        assertEquals(AlienBootstrapThemeTokens.PRIMARY_HOVER, primaryImpl.node.resolveVisualStyle().backgroundColor)

        val outlineSuccessBtn = Button("Confirm").apply {
            getBootstrap().apply {
                type = BootstrapType.SUCCESS
                outline = true
            }
        }
        alienRoot.addChild(outlineSuccessBtn)
        val outlineImpl = assertIs<ButtonAlienImpl>(outlineSuccessBtn.nativeImpl)
        assertEquals(RgbaColor.TRANSPARENT, outlineImpl.node.resolveVisualStyle().backgroundColor)
        outlineImpl.node.hoveredState = true
        outlineImpl.node.resolveVisualStyle()
        outlineImpl.node.transitionAnimator.step(150f)
        assertEquals(AlienBootstrapThemeTokens.SUCCESS, outlineImpl.node.resolveVisualStyle().backgroundColor)

        // 3. Light vs Dark theme .card from AlienPrecompiledBootstrapStyleSheet
        val card = Container("card")
        alienRoot.addChild(card)
        val cardImpl = assertIs<ContainerAlienImpl>(card.nativeImpl)
        assertEquals(AlienBootstrapThemeTokens.BODY_BG_LIGHT, cardImpl.node.resolveVisualStyle().backgroundColor)
        cardImpl.node.attributes["data-bs-theme"] = "dark"
        assertEquals(AlienBootstrapThemeTokens.TERTIARY_BG_DARK, cardImpl.node.resolveVisualStyle().backgroundColor)
    }

    @Test
    fun testIAlienStyleSheetManagementAndInsertApplicationStyleSheetsOrder() {
        val alienContainer = AlienContainer().apply {
            widthPx = 640
            heightPx = 480
        }
        val alienFragment = object : AlienFragment() {}
        assertIs<IAlien>(alienContainer)
        assertIs<IAlien>(alienFragment)

        // 1. Default Bootstrap stylesheet is present at index 0 and insertApplicationStyleSheets defaults to 1
        assertEquals(1, alienContainer.insertApplicationStyleSheets)
        assertEquals(1, alienFragment.insertApplicationStyleSheets)
        assertTrue(alienContainer.hasStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID))
        assertTrue(alienContainer.isBootstrapStyleSheetEnabled)
        assertTrue(alienFragment.hasStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID))

        alienContainer.ensureNativeImpl()

        val dangerBtn = Button("Danger").apply {
            getBootstrap().type = BootstrapType.DANGER
            addClass("app-styled-btn", "alien-local-btn")
        }
        alienContainer.addChild(dangerBtn)
        val btnImpl = assertIs<ButtonAlienImpl>(dangerBtn.nativeImpl)
        assertEquals(AlienBootstrapThemeTokens.DANGER, btnImpl.node.resolveVisualStyle().backgroundColor)

        // 2. Removing IAlien.BOOTSTRAP_STYLE_SHEET_ID disables default Bootstrap styling
        assertTrue(alienContainer.removeStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID))
        assertFalse(alienContainer.hasStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID))
        assertFalse(alienContainer.isBootstrapStyleSheetEnabled)
        assertEquals(RgbaColor.TRANSPARENT, btnImpl.node.resolveVisualStyle().backgroundColor)

        // Re-adding IAlien.BOOTSTRAP_STYLE_SHEET_ID restores default Bootstrap styling at index 0
        alienContainer.addStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID)
        assertTrue(alienContainer.hasStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID))
        assertEquals(AlienBootstrapThemeTokens.DANGER, btnImpl.node.resolveVisualStyle().backgroundColor)

        // 3. Default insertApplicationStyleSheets = 1:
        //    Order is [Bootstrap, $ApplicationStyleSheets, AllAdditionalStyleSheets...]
        val appSheet = CssStyleSheet.parse(".app-styled-btn { background-color: #112233; border-radius: 14px; }")
        alienContainer.applyStyleSheet(appSheet)
        // $ApplicationStyleSheets (at index 1) overrides Bootstrap (at index 0)
        assertEquals(RgbaColor(0x11, 0x22, 0x33, 255), btnImpl.node.resolveVisualStyle().backgroundColor)
        assertEquals(14f, btnImpl.node.resolveVisualStyle().borderRadiusPx, 0.01f)

        // Adding an additional Alien stylesheet (at index 1 in activeStyleSheets, evaluated after $ApplicationStyleSheets)
        val localAlienSheet = CssStyleSheet.parse(".alien-local-btn { background-color: #445566; border-radius: 22px; }")
        alienContainer.addStyleSheet(localAlienSheet)
        assertTrue(alienContainer.hasStyleSheet(localAlienSheet))
        // Additional Alien stylesheet overrides $ApplicationStyleSheets when insertApplicationStyleSheets == 1
        assertEquals(RgbaColor(0x44, 0x55, 0x66, 255), btnImpl.node.resolveVisualStyle().backgroundColor)
        assertEquals(22f, btnImpl.node.resolveVisualStyle().borderRadiusPx, 0.01f)

        // 4. Setting insertApplicationStyleSheets = 2 places $ApplicationStyleSheets AFTER localAlienSheet
        alienContainer.insertApplicationStyleSheets = 2
        assertEquals(2, alienContainer.insertApplicationStyleSheets)
        assertEquals(RgbaColor(0x11, 0x22, 0x33, 255), btnImpl.node.resolveVisualStyle().backgroundColor)
        assertEquals(14f, btnImpl.node.resolveVisualStyle().borderRadiusPx, 0.01f)

        // 5. Setting insertApplicationStyleSheets = 0 places $ApplicationStyleSheets BEFORE Bootstrap
        alienContainer.removeStyleSheet(localAlienSheet)
        alienContainer.insertApplicationStyleSheets = 0
        // Bootstrap (.btn-danger) overrides $ApplicationStyleSheets when insertApplicationStyleSheets == 0
        assertEquals(AlienBootstrapThemeTokens.DANGER, btnImpl.node.resolveVisualStyle().backgroundColor)

        // 6. Setting insertApplicationStyleSheets = -1 disables $ApplicationStyleSheets completely
        alienContainer.addStyleSheet(localAlienSheet)
        alienContainer.insertApplicationStyleSheets = -1
        assertEquals(-1, alienContainer.insertApplicationStyleSheets)
        assertEquals(RgbaColor(0x44, 0x55, 0x66, 255), btnImpl.node.resolveVisualStyle().backgroundColor)
        assertEquals(22f, btnImpl.node.resolveVisualStyle().borderRadiusPx, 0.01f)

        assertTrue(alienContainer.removeStyleSheet(localAlienSheet))
        assertFalse(alienContainer.hasStyleSheet(localAlienSheet))
        assertEquals(AlienBootstrapThemeTokens.DANGER, btnImpl.node.resolveVisualStyle().backgroundColor)
    }
}
