package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.content.ImageView
import com.nexustools.genericui.component.content.Table
import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dock.DockArea
import com.nexustools.genericui.component.dock.DockGroup
import com.nexustools.genericui.component.dock.DockPanel
import com.nexustools.genericui.component.dock.DockWorkspace
import com.nexustools.genericui.component.dock.ToolPalette
import com.nexustools.genericui.component.document.BookContainer
import com.nexustools.genericui.component.feedback.ProgressBar
import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.helper.FragmentNavigationChromeConfig
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Divider
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.lazy.LazyGrid
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.lazy.TreeView
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.overlay.Popover
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.Menu
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.MenuItem
import com.nexustools.genericui.component.shell.SplitPane
import com.nexustools.genericui.component.surface.CollapsiblePanel
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.MarkdownView
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.alien.impl.*
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.enums.css.CssPosition
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AlienExtensionTest {

    /**
     * Minimal host OS [NativeFactory] representing a native system backend (e.g., Windows / Qt / GTK)
     * whose native implementations are distinct from `*AlienImpl`.
     */
    private class MockHostOsNativeFactory : NativeFactory {
        override val backendTarget: com.nexustools.genericui.enums.system.BackendTarget =
            com.nexustools.genericui.enums.system.BackendTarget.Windows
        override val backendName: String = "mock-host-os"
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true

        private val delegate = AlienFactory(this)

        inner class HostOsContainerImpl(
            classPrefix: String,
            tagName: String
        ) : ContainerNativeImpl<String> by object : ContainerAlienImpl(classPrefix, tagName) {
            override val nativeWidget: AlienWidgetNode = AlienWidgetNode(classPrefix, tagName)
        } as ContainerNativeImpl<String> {
            val attachedNativeChildren = mutableListOf<NativeImpl<*>>()
            private val inner = ContainerAlienImpl(classPrefix, tagName)
            override val nativeWidget: String = "host-container:$classPrefix:$tagName"
            override val componentClassPrefix: String = classPrefix

            override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
                if (index < 0 || index >= attachedNativeChildren.size) {
                    attachedNativeChildren.add(childImpl)
                } else {
                    attachedNativeChildren.add(index, childImpl)
                }
            }

            override fun removeChildNative(childImpl: NativeImpl<*>) {
                attachedNativeChildren.remove(childImpl)
            }

            override fun clearChildrenNative() {
                attachedNativeChildren.clear()
            }
        }

        inner class HostOsButtonImpl(
            override val componentClassPrefix: String = "btn"
        ) : ButtonNativeImpl<String>, NativeImpl<String> by (object : AlienImpl(componentClassPrefix) {} as NativeImpl<String>) {
            override val nativeWidget: String = "host-os-button"
            private var textVal = ""
            private var iconVal: String? = null
            override fun setText(text: String) { textVal = text }
            override fun getText(): String = textVal
            override fun setIcon(icon: String?) { iconVal = icon }
            override fun getIcon(): String? = iconVal
        }

        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
            HostOsContainerImpl(classPrefix, "div")

        override fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> =
            HostOsContainerImpl(classPrefix, tagName)

        override fun createStyledContainer(classPrefix: String, tagName: String) =
            delegate.createStyledContainer(classPrefix, tagName)

        override fun createButton(classPrefix: String): ButtonNativeImpl<*> =
            HostOsButtonImpl(classPrefix)

        override fun createStyledButton(classPrefix: String) =
            delegate.createStyledButton(classPrefix)

        // Delegate remaining methods to standard implementations for test completeness
        override fun createApplication(): ApplicationNativeImpl<*> = throw UnsupportedOperationException()
        override fun createWindow(): WindowNativeImpl<*> = throw UnsupportedOperationException()
        override fun createMenuBar() = delegate.createMenuBar()
        override fun createMenu() = delegate.createMenu()
        override fun createMenuItem(checkable: Boolean, radio: Boolean) = delegate.createMenuItem(checkable, radio)
        override fun createContextMenu() = delegate.createContextMenu()
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
        override fun createDropdown() = delegate.createDropdown()
        override fun createTextInput(multiline: Boolean) = delegate.createTextInput(multiline)
        override fun createSelect() = delegate.createSelect()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean) = delegate.createCheckbox(switchStyle, radioStyle)
        override fun createRangeSlider() = delegate.createRangeSlider()
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

    private lateinit var hostFactory: MockHostOsNativeFactory

    @BeforeTest
    fun setUp() {
        AlienExtension.useSystemCanvasRenderer()
        hostFactory = MockHostOsNativeFactory()
        GenericUI.initialize(hostFactory, "AlienTestApp")
    }

    @AfterTest
    fun tearDown() {
        AlienExtension.useSystemCanvasRenderer()
    }

    @Test
    fun testAlienContainerAndAlienFragmentProvideAlienImplsForAllCommonComponents() {
        val alienContainer = AlienContainer().apply {
            widthPx = 800
            heightPx = 600
        }
        val rootImpl = alienContainer.ensureNativeImpl()
        assertIs<AlienHostCanvasContainerAlienImpl>(rootImpl)
        assertSame(rootImpl.hostContainerImpl.nativeWidget, rootImpl.nativeWidget)
        assertSame(rootImpl.session.rootNode, rootImpl.node)
        assertIs<AlienFactory>(alienContainer.resolveChildFactory())

        // Add all :common component types into AlienContainer and verify each receives its *AlienImpl
        val btn = Button("Primary Action", BootstrapType.PRIMARY).also { alienContainer.addChild(it) }
        val dropdown = DropdownButton("Options").also { alienContainer.addChild(it) }
        val textField = TextField(value = "Hello", placeholder = "Type...").also { alienContainer.addChild(it) }
        val select = Select().also { alienContainer.addChild(it) }
        val checkBox = CheckBox("Enable Feature", checked = true).also { alienContainer.addChild(it) }
        val slider = RangeSlider(minValue = 50.0, min = 0.0, max = 100.0).also { alienContainer.addChild(it) }
        val heading = Heading("Dashboard", level = 1).also { alienContainer.addChild(it) }
        val text = Text("Body paragraph").also { alienContainer.addChild(it) }
        val markdown = MarkdownView("# Title\n- Item").also { alienContainer.addChild(it) }
        val image = ImageView("logo.png").also { alienContainer.addChild(it) }
        val table = Table().also { alienContainer.addChild(it) }
        val nestedContainer = Container().also { alienContainer.addChild(it) }
        val scrollView = ScrollView().also { alienContainer.addChild(it) }
        val divider = Divider().also { alienContainer.addChild(it) }
        val book = BookContainer().also { alienContainer.addChild(it) }
        val collapse = CollapsiblePanel("Advanced").also { alienContainer.addChild(it) }
        val tab = Tab("General").also { alienContainer.addChild(it) }
        val tabContainer = TabContainer().also { alienContainer.addChild(it) }
        val navbar = Navbar("Brand").also { alienContainer.addChild(it) }
        val bottomNav = BottomNavigationBar().also { alienContainer.addChild(it) }
        val progressBar = ProgressBar(0.65f).also { alienContainer.addChild(it) }
        val spinner = Spinner().also { alienContainer.addChild(it) }
        val popover = Popover("Tip").also { alienContainer.addChild(it) }
        val splitPane = SplitPane().also { alienContainer.addChild(it) }
        val menuBar = MenuBar().also { alienContainer.addChild(it) }
        val menu = Menu("File").also { alienContainer.addChild(it) }
        val menuItem = MenuItem("Save").also { alienContainer.addChild(it) }
        val contextMenu = ContextMenu().also { alienContainer.addChild(it) }
        val dialog = DialogWindow("Settings").also { alienContainer.addChild(it) }
        val dockWorkspace = DockWorkspace().also { alienContainer.addChild(it) }
        val dockArea = DockArea(DockPosition.LEFT).also { alienContainer.addChild(it) }
        val dockGroup = DockGroup().also { alienContainer.addChild(it) }
        val dockPanel = DockPanel("Layers").also { alienContainer.addChild(it) }
        val toolPalette = ToolPalette().also { alienContainer.addChild(it) }
        val lazyList = LazyList().also { alienContainer.addChild(it) }
        val lazyGrid = LazyGrid().also { alienContainer.addChild(it) }
        val treeView = TreeView<String>().also { alienContainer.addChild(it) }

        assertIs<ButtonAlienImpl>(btn.nativeImpl)
        assertIs<DropdownAlienImpl>(dropdown.nativeImpl)
        assertIs<TextInputAlienImpl>(textField.nativeImpl)
        assertIs<SelectAlienImpl>(select.nativeImpl)
        assertIs<CheckboxAlienImpl>(checkBox.nativeImpl)
        assertIs<RangeSliderAlienImpl>(slider.nativeImpl)
        assertIs<TextAlienImpl>(heading.nativeImpl)
        assertIs<TextAlienImpl>(text.nativeImpl)
        assertIs<MarkdownViewAlienImpl>(markdown.nativeImpl)
        assertIs<ImageAlienImpl>(image.nativeImpl)
        assertIs<TableAlienImpl>(table.nativeImpl)
        assertIs<ContainerAlienImpl>(nestedContainer.nativeImpl)
        assertIs<ScrollViewAlienImpl>(scrollView.nativeImpl)
        assertIs<DividerAlienImpl>(divider.nativeImpl)
        assertIs<BookContainerAlienImpl>(book.nativeImpl)
        assertIs<ContainerAlienImpl>(collapse.nativeImpl)
        assertIs<TabAlienImpl>(tab.nativeImpl)
        assertIs<TabContainerAlienImpl>(tabContainer.nativeImpl)
        assertIs<NavbarAlienImpl>(navbar.nativeImpl)
        assertIs<BottomNavigationBarAlienImpl>(bottomNav.nativeImpl)
        assertIs<ProgressBarAlienImpl>(progressBar.nativeImpl)
        assertIs<SpinnerAlienImpl>(spinner.nativeImpl)
        assertIs<PopoverAlienImpl>(popover.nativeImpl)
        assertIs<ContainerAlienImpl>(splitPane.nativeImpl)
        assertIs<MenuBarAlienImpl>(menuBar.nativeImpl)
        assertIs<MenuAlienImpl>(menu.nativeImpl)
        assertIs<MenuItemAlienImpl>(menuItem.nativeImpl)
        assertIs<ContextMenuAlienImpl>(contextMenu.nativeImpl)
        assertIs<DialogWindowAlienImpl>(dialog.nativeImpl)
        assertIs<DockWorkspaceAlienImpl>(dockWorkspace.nativeImpl)
        assertIs<DockAreaAlienImpl>(dockArea.nativeImpl)
        assertIs<DockGroupAlienImpl>(dockGroup.nativeImpl)
        assertIs<DockPanelAlienImpl>(dockPanel.nativeImpl)
        assertIs<ToolPaletteAlienImpl>(toolPalette.nativeImpl)
        assertIs<LazyListAlienImpl>(lazyList.nativeImpl)
        assertIs<LazyGridAlienImpl>(lazyGrid.nativeImpl)
        assertIs<TreeViewAlienImpl>(treeView.nativeImpl)

        // Verify AlienFragment also provides AlienFactory and *AlienImpl to its children
        val fragment = object : AlienFragment() {}
        fragment.ensureNativeImpl()
        val fragBtn = Button("Fragment Button").also { fragment.addChild(it) }
        assertIs<ButtonAlienImpl>(fragBtn.nativeImpl)
    }

    @Test
    fun testUnalienContainerRestoresHostNativeFactoryInsideAlienHierarchy() {
        val alienRoot = AlienContainer().apply {
            widthPx = 640
            heightPx = 480
        }
        alienRoot.ensureNativeImpl()

        val alienButton = Button("Alien Button")
        alienRoot.addChild(alienButton)
        assertIs<ButtonAlienImpl>(alienButton.nativeImpl)

        // Place an UnalienContainer inside the AlienContainer
        val unalien = UnalienContainer()
        unalien.setPreferredSize(Size(300, 180))
        alienRoot.addChild(unalien)

        // UnalienContainer and its children must use the host OS NativeFactory, NOT AlienImpl!
        assertSame(hostFactory, unalien.resolveActiveFactory())
        assertSame(hostFactory, unalien.resolveChildFactory())
        assertIs<MockHostOsNativeFactory.HostOsContainerImpl>(unalien.nativeImpl)

        val nativeButtonInsideUnalien = Button("OS Native Button")
        unalien.addChild(nativeButtonInsideUnalien)
        assertIs<MockHostOsNativeFactory.HostOsButtonImpl>(nativeButtonInsideUnalien.nativeImpl)

        // Verify UnalienContainer was registered as an underlay slot below the Canvas2D
        val session = assertNotNull(alienRoot.renderSession)
        assertEquals(1, session.underlaySlots.size)
        assertSame(unalien.nativeImpl, session.underlaySlots.first().wrappedNativeImpl)
    }

    @Test
    fun testExtensionUnderlayTransparentHoleCuttingOverdrawingAndMousePassthrough() {
        // Custom mock extension component (representing Canvas3D / VideoPlayer / WebView)
        class MockExtensionCanvas3D : AbstractComponent("canvas-3d", "canvas") {
            init {
                widthPx = 200
                heightPx = 150
            }

            protected override fun createNativeImpl(factory: NativeFactory): NativeImpl<*> =
                hostFactory.createButton("canvas-3d-native")
        }

        val alienRoot = AlienContainer().apply {
            widthPx = 500
            heightPx = 400
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val extCanvas = MockExtensionCanvas3D()
        alienRoot.addChild(extCanvas)
        assertEquals(1, session.underlaySlots.size)
        val slot = session.underlaySlots.first()
        assertEquals(AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE, slot.layerMode)

        // Perform layout and repaint on the Canvas2D
        val dirtyRegions = session.forceFullRepaint(16.67f)
        assertTrue(dirtyRegions.isNotEmpty())

        val slotBounds = slot.node.bounds
        assertTrue(slotBounds.width > 0 && slotBounds.height > 0)

        // 1. Verify a transparent hole (alpha == 0) was cut into the Canvas2D's ImageBuffer at the extension's center!
        val centerX = slotBounds.x + slotBounds.width / 2
        val centerY = slotBounds.y + slotBounds.height / 2
        val pixelAtHole = session.canvas2D.imageBuffer.getPixel(centerX, centerY)
        assertEquals(0, pixelAtHole.a, "Expected Canvas2D pixel over extension slot hole to be transparent (alpha == 0)")

        // 2. Verify mouse/pointer passthrough at the uncovered hole routes to the underlay slot
        assertTrue(session.eventDispatcher.isPointPassthroughToUnderlay(centerX.toFloat(), centerY.toFloat()))
        session.eventDispatcher.dispatchPointerDown(PointerEvent(x = centerX.toFloat(), y = centerY.toFloat()))
        session.eventDispatcher.dispatchPointerUp(PointerEvent(x = centerX.toFloat(), y = centerY.toFloat()))
        assertEquals(1, slot.passthroughPointerDownCount)
        assertEquals(1, slot.passthroughPointerUpCount)

        // 3. Now add a higher-Z Alien overlay button positioned directly over the extension hole
        var overlayClicked = 0
        val overlayButton = Button("Floating Overlay", BootstrapType.DANGER).apply {
            style.position = CssPosition.ABSOLUTE
            style.left = CssLength.px(slotBounds.x + 10)
            style.top = CssLength.px(slotBounds.y + 10)
            widthPx = 120
            heightPx = 40
            zIndex = 10
            onClick = { overlayClicked++ }
        }
        alienRoot.addChild(overlayButton)
        session.forceFullRepaint(16.67f)

        val overlayHitX = (slotBounds.x + 30).toFloat()
        val overlayHitY = (slotBounds.y + 25).toFloat()

        // Where the higher-Z Alien overlay button sits above the hole, passthrough must be false and the Alien button receives the click!
        assertFalse(session.eventDispatcher.isPointPassthroughToUnderlay(overlayHitX, overlayHitY))
        val pixelAtOverlay = session.canvas2D.imageBuffer.getPixel(overlayHitX.toInt(), overlayHitY.toInt())
        assertTrue(pixelAtOverlay.a > 0, "Higher-Z Alien overlay component should paint opaque pixels over the transparent hole")

        session.eventDispatcher.dispatchPointerDown(PointerEvent(x = overlayHitX, y = overlayHitY))
        session.eventDispatcher.dispatchPointerUp(PointerEvent(x = overlayHitX, y = overlayHitY))
        assertEquals(1, overlayClicked)
    }

    @Test
    fun testRepaintManagerDirtyRegionTrackingAndCssTransitions() {
        val alienRoot = AlienContainer().apply {
            widthPx = 600
            heightPx = 400
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val button = Button("Hover Me", BootstrapType.PRIMARY).apply {
            widthPx = 140
            heightPx = 42
        }
        alienRoot.addChild(button)

        // Initial full viewport paint
        val initialRegions = session.forceFullRepaint(0f)
        assertEquals(1, initialRegions.size)
        assertEquals(Rect(0, 0, 600, 400), initialRegions.first())

        // No changes -> draining dirty regions should be empty
        val idleRegions = session.repaintIfDirty(0f)
        assertTrue(idleRegions.isEmpty())

        // Hover over button -> only the button's padded dirty region should be repainted, and Bootstrap's .15s hover transition should start!
        val btnImpl = assertIs<ButtonAlienImpl>(button.nativeImpl)
        val btnBounds = btnImpl.getBounds()
        val baseColor = btnImpl.node.resolveVisualStyle().backgroundColor
        assertEquals(AlienBootstrapThemeTokens.PRIMARY, baseColor)

        btnImpl.setHoveredState(true)
        assertTrue(session.repaintManager.updateTracker.hasDirtyRegions)

        // Step 75ms (halfway through the 150ms Bootstrap hover transition)
        val partialRegions = session.repaintIfDirty(75f)
        assertEquals(1, partialRegions.size)
        assertTrue(partialRegions.first().width < 600, "Expected partial dirty-region repaint rather than full viewport")

        val midHoverColor = btnImpl.node.resolveVisualStyle().backgroundColor
        // Mid-transition color should be interpolated between PRIMARY (#0d6efd) and PRIMARY_HOVER (#0b5ed7)
        assertTrue(midHoverColor.b in AlienBootstrapThemeTokens.PRIMARY_HOVER.b..AlienBootstrapThemeTokens.PRIMARY.b)

        // Step remaining 100ms to finish transition
        session.repaintIfDirty(100f)
        val finalHoverColor = btnImpl.node.resolveVisualStyle().backgroundColor
        assertEquals(AlienBootstrapThemeTokens.PRIMARY_HOVER, finalHoverColor)
    }

    @Test
    fun testCssCustomVariablesAndThemeInheritance() {
        val alienRoot = AlienContainer().apply {
            widthPx = 400
            heightPx = 300
        }
        val rootImpl = assertIs<AlienHostCanvasContainerAlienImpl>(alienRoot.ensureNativeImpl())
        rootImpl.node.attributes["--bs-primary"] = "#ff5500"

        val btn = Button("Themed Button", BootstrapType.PRIMARY)
        alienRoot.addChild(btn)
        val btnImpl = assertIs<ButtonAlienImpl>(btn.nativeImpl)

        val style = btnImpl.node.resolveVisualStyle()
        assertEquals(RgbaColor(255, 85, 0, 255), style.backgroundColor)
    }

    @Test
    fun testWebTargetPassThroughForwardsDirectlyToParentFactory() {
        // Simulate Web target bridge
        AlienExtension.platformBridge = object : AlienPlatformBridge {
            override val isWebPassThrough: Boolean = true
            override fun resolveChildFactory(
                alienRoot: AbstractContainer,
                parentFactory: NativeFactory?,
                sessionProvider: () -> AlienRenderSession?
            ): NativeFactory? = parentFactory

            override fun createAlienRootContainerImpl(
                alienRoot: AbstractContainer,
                hostFactory: NativeFactory,
                sessionProvider: () -> AlienRenderSession
            ): ContainerNativeImpl<*> =
                hostFactory.createContainer(alienRoot.componentClassPrefix, alienRoot.htmlTag)
        }

        val alienContainer = AlienContainer()
        val rootImpl = alienContainer.ensureNativeImpl()
        // On Web, AlienContainer uses the host/Web factory directly with no AlienRenderSession or Canvas2D
        assertIs<MockHostOsNativeFactory.HostOsContainerImpl>(rootImpl)
        assertSame(hostFactory, alienContainer.resolveChildFactory())

        val childBtn = Button("Web Native DOM Button")
        alienContainer.addChild(childBtn)
        assertIs<MockHostOsNativeFactory.HostOsButtonImpl>(childBtn.nativeImpl)
    }

    @Test
    fun testAlienScrollFragmentScrollStateAndLayoutOffset() {
        val scrollFragment = object : AlienScrollFragment() {}
        scrollFragment.widthPx = 400
        scrollFragment.heightPx = 300
        scrollFragment.ensureNativeImpl()

        var observedX = -1
        var observedY = -1
        scrollFragment.onScroll { sx, sy ->
            observedX = sx
            observedY = sy
        }

        scrollFragment.setContentSize(800, 1200)
        assertEquals(800, scrollFragment.contentWidth)
        assertEquals(1200, scrollFragment.contentHeight)

        scrollFragment.scrollTo(120, 250)
        assertEquals(120, scrollFragment.scrollX)
        assertEquals(250, scrollFragment.scrollY)
        assertEquals(120, observedX)
        assertEquals(250, observedY)
        assertEquals(120, scrollFragment.renderSession?.rootNode?.scrollOffsetX)
        assertEquals(250, scrollFragment.renderSession?.rootNode?.scrollOffsetY)

        scrollFragment.scrollToBottom()
        assertEquals(900, scrollFragment.scrollY)
        assertEquals(900, observedY)
    }

    @Test
    fun testBackgroundImageRenderingOnAlienFragmentAndAlienContainer() {
        AlienBootstrapPainter.clearBackgroundImageCache()
        val testBg = ImageBuffer(16, 16).apply {
            fill(RgbaColor(210, 45, 90, 255))
        }
        ImageCacheManager.putInCache("bg.jpg?2", testBg)

        val fragment = object : AlienFragment() {}
        assertEquals("genericui:fragment", fragment.htmlTag)
        fragment.htmlTag = "genericui:app"
        fragment.widthPx = 200
        fragment.heightPx = 150
        fragment.applyComputedStyle(
            ComputedStyle(
                backgroundImageUrl = "bg.jpg?2",
                backgroundSize = "cover",
                backgroundPosition = "center center",
                backgroundRepeat = "no-repeat"
            )
        )
        fragment.ensureNativeImpl()
        val session = assertNotNull(fragment.renderSession)
        session.forceFullRepaint(0f)

        val sampled = session.canvas2D.imageBuffer.getPixel(100, 75)
        assertEquals(210, sampled.r)
        assertEquals(45, sampled.g)
        assertEquals(90, sampled.b)
        assertEquals(255, sampled.a)

        val container = AlienContainer().apply {
            widthPx = 120
            heightPx = 80
            applyComputedStyle(
                ComputedStyle(
                    backgroundImageUrl = "bg.jpg?2",
                    backgroundRepeat = "no-repeat",
                    backgroundPosition = "center center",
                    backgroundSize = "100% 100%"
                )
            )
        }
        container.ensureNativeImpl()
        val containerSession = assertNotNull(container.renderSession)
        containerSession.forceFullRepaint(0f)
        val containerPixel = containerSession.canvas2D.imageBuffer.getPixel(60, 40)
        assertEquals(210, containerPixel.r)
        assertEquals(45, containerPixel.g)
        assertEquals(90, containerPixel.b)
    }

    @Test
    fun testContextMenuSeparatorAndAbsoluteShrinkWrapLayout() {
        val alienRoot = AlienContainer().apply {
            widthPx = 1024
            heightPx = 768
        }
        alienRoot.ensureNativeImpl()

        val menu = Container("menu").apply {
            addClass("menu")
            applyComputedStyle(
                ComputedStyle(
                    position = "absolute",
                    left = "50%",
                    top = "50%",
                    transform = "translate(-50%, -50%)",
                    minWidth = "300px"
                )
            )
        }
        val btn = Button("Play Game").apply {
            applyComputedStyle(ComputedStyle(display = "block"))
        }
        val sep = Divider()
        menu.addChild(btn)
        menu.addChild(sep)
        alienRoot.addChild(menu)

        val menuBounds = assertNotNull(menu.nativeImpl).getBounds()
        assertEquals(300, menuBounds.width)
        assertEquals((1024 - 300) / 2, menuBounds.x)
    }

    @Test
    fun testAlienImplProviderSharedDelegationAndContract() {
        val alienContainer = AlienContainer()
        val alienFragment = object : AlienFragment() {}
        val alienScrollFragment = object : AlienScrollFragment() {}
        val alienHostFragment = AlienFragmentHostFragment()

        assertEquals("genericui:fragment", alienFragment.htmlTag)
        assertEquals("genericui:fragment", alienScrollFragment.htmlTag)
        assertEquals("genericui:fragment", alienHostFragment.htmlTag)

        assertIs<IAlien>(alienContainer)
        assertIs<IAlien>(alienFragment)
        assertIs<IAlien>(alienScrollFragment)
        assertIs<IAlien>(alienHostFragment)

        val rawContainer = Container()
        val provider = AlienImplProvider(rawContainer)
        val providerAsAny: Any = provider
        assertFalse(providerAsAny is IAlien)
        assertTrue(provider.isAlienModeActive)
        assertTrue(provider.isBootstrapStyleSheetEnabled)

        var customCreatorInvoked = false
        alienContainer.registerAlienComponentImpl(Button::class) {
            customCreatorInvoked = true
            ButtonAlienImpl(componentClassPrefix = "custom-btn", session = alienContainer.renderSession)
        }
        alienContainer.ensureNativeImpl()
        assertSame(alienContainer.renderSession, alienContainer.alienSession)
        assertTrue(alienContainer.isAlienModeActive)
        alienContainer.requestAlienRepaint()
        assertFalse(customCreatorInvoked)
    }

    @Test
    fun testAlienFragmentHostFragmentNavigationAndOnResizePropagation() {
        var homeResizedW = 0
        var homeResizedH = 0
        var settingsResizedW = 0
        var settingsResizedH = 0

        class HomeFragment : Fragment() {
            val actionButton = Button("Go to Settings", BootstrapType.PRIMARY)
            init {
                addChild(actionButton)
            }
            override fun onResize(viewportWidth: Int, viewportHeight: Int) {
                super.onResize(viewportWidth, viewportHeight)
                homeResizedW = viewportWidth
                homeResizedH = viewportHeight
            }
        }

        class SettingsAlienFragment : AlienFragment() {
            val saveButton = Button("Save Settings", BootstrapType.SUCCESS)
            init {
                addChild(saveButton)
            }
            override fun onResize(viewportWidth: Int, viewportHeight: Int) {
                super.onResize(viewportWidth, viewportHeight)
                settingsResizedW = viewportWidth
                settingsResizedH = viewportHeight
            }
        }

        var hostOnResizeCount = 0
        val hostFragment = object : AlienFragmentHostFragment(
            appTitle = "Alien Host App",
            navigationChrome = FragmentNavigationChromeConfig(
                showBackButton = true,
                showForwardButton = true,
                preferTitleBarNavigation = false
            )
        ) {
            override fun onResize(viewportWidth: Int, viewportHeight: Int) {
                super.onResize(viewportWidth, viewportHeight)
                hostOnResizeCount++
            }
        }
        hostFragment.widthPx = 960
        hostFragment.heightPx = 540

        hostFragment.registerRoute("home", HomeFragment::class, title = "Home") { HomeFragment() }
        hostFragment.registerRoute("settings", SettingsAlienFragment::class, title = "Settings") { SettingsAlienFragment() }

        val rootImpl = hostFragment.ensureNativeImpl()
        assertIs<AlienHostCanvasContainerAlienImpl>(rootImpl)
        val session = assertNotNull(hostFragment.renderSession)
        assertSame(session, hostFragment.alienSession)
        assertEquals(960, session.viewportWidth)
        assertEquals(540, session.viewportHeight)
        assertTrue(hostOnResizeCount >= 1)

        val home = hostFragment.navigateToRoute("home") as HomeFragment
        assertSame(home, hostFragment.currentFragment)
        assertIs<ContainerAlienImpl>(home.nativeImpl)
        assertIs<ButtonAlienImpl>(home.actionButton.nativeImpl)
        assertEquals(960, homeResizedW)
        assertEquals(540, homeResizedH)

        val settings = hostFragment.navigateToRoute("settings") as SettingsAlienFragment
        assertSame(settings, hostFragment.currentFragment)
        assertTrue(hostFragment.canGoBack)
        assertIs<ContainerAlienImpl>(settings.nativeImpl)
        assertIs<ButtonAlienImpl>(settings.saveButton.nativeImpl)
        assertSame(session, settings.getOrCreateRenderSession())

        // Resize the AlienFragmentHostFragment and verify propagation to session and active child fragment
        hostFragment.onResize(1280, 720)
        assertEquals(1280, session.viewportWidth)
        assertEquals(720, session.viewportHeight)
        assertEquals(1280, settingsResizedW)
        assertEquals(720, settingsResizedH)

        // Pop back to HomeFragment and verify resize state and back/forward navigation
        assertTrue(hostFragment.popBackStack())
        assertSame(home, hostFragment.currentFragment)
        assertTrue(hostFragment.canGoForward)
        assertEquals(1280, homeResizedW)
        assertEquals(720, homeResizedH)

        assertTrue(hostFragment.goForward())
        assertSame(settings, hostFragment.currentFragment)
    }

    @Test
    fun testStackShrinkCrossAxisAndSingleAxisImageAspectRatio() {
        val wideBannerBuf = ImageBuffer(800, 400).apply {
            fill(RgbaColor(40, 120, 220, 255))
        }
        ImageCacheManager.putInCache("wide-banner.png", wideBannerBuf)

        val tallPosterBuf = ImageBuffer(400, 800).apply {
            fill(RgbaColor(220, 80, 40, 255))
        }
        ImageCacheManager.putInCache("tall-poster.png", tallPosterBuf)

        val alienRoot = AlienContainer().apply {
            widthPx = 1000
            heightPx = 800
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        // 1. VStack with a 200px non-shrinking button and an 800x400 ImageView with shrinkCrossAxis = true
        val vstack = com.nexustools.genericui.component.layout.VStack(gapPx = 0).apply {
            applyComputedStyle(ComputedStyle(display = "inline-block"))
        }
        val vBtn = Button("Action").apply {
            widthPx = 200
            heightPx = 40
        }
        val vImg = ImageView("wide-banner.png")
        vstack.addChild(vBtn)
        vstack.addChild(vImg, com.nexustools.genericui.component.layout.StackConstraint.shrinkCrossAxis())
        alienRoot.addChild(vstack)

        // 2. HStack with a 60px-tall non-shrinking button and a 400x800 ImageView with shrinkCrossAxis = true
        val hstack = com.nexustools.genericui.component.layout.HStack(gapPx = 0)
        val hBtn = Button("Side").apply {
            widthPx = 100
            heightPx = 60
        }
        val hImg = ImageView("tall-poster.png")
        hstack.addChild(hBtn)
        hstack.addChild(hImg, com.nexustools.genericui.component.layout.StackConstraint.shrinkCrossAxis())
        alienRoot.addChild(hstack)

        // 3. Single-axis CSS height (`height: 235px`, width unset) on a 2:1 aspect-ratio image
        val fixedHeightImg = ImageView("wide-banner.png").apply {
            applyComputedStyle(ComputedStyle(height = "235px"))
        }
        alienRoot.addChild(fixedHeightImg)

        session.forceFullRepaint(0f)

        val vImgBounds = assertNotNull(vImg.nativeImpl).getBounds()
        assertEquals(200, vImgBounds.width, "Expected shrinkCrossAxis ImageView in VStack to match 200px sibling width")
        assertEquals(100, vImgBounds.height, "Expected shrinkCrossAxis ImageView in VStack to scale height proportionally (200 / 2 = 100)")

        val hImgBounds = assertNotNull(hImg.nativeImpl).getBounds()
        assertEquals(60, hImgBounds.height, "Expected shrinkCrossAxis ImageView in HStack to match 60px sibling height")
        assertEquals(30, hImgBounds.width, "Expected shrinkCrossAxis ImageView in HStack to scale width proportionally (60 * 0.5 = 30)")

        val fixedHeightBounds = assertNotNull(fixedHeightImg.nativeImpl).getBounds()
        assertEquals(235, fixedHeightBounds.height)
        assertEquals(470, fixedHeightBounds.width, "Expected single-axis height: 235px on 2:1 image to compute width = 470px")
    }

    @Test
    fun testResponsiveBootstrapDisplayClassesAndRow12ColumnWrapping() {
        val alienRoot = AlienContainer().apply {
            widthPx = 500
            heightPx = 600
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val desktopOnly = Container().apply {
            addClass("d-none")
            addClass("d-md-flex")
            widthPx = 200
            heightPx = 40
        }
        val mobileOnly = Container().apply {
            addClass("d-flex")
            addClass("d-md-none")
            widthPx = 200
            heightPx = 40
        }
        val row = Container("row").apply {
            addClass("row")
        }
        val col1 = Container().apply {
            addClass("col-12")
            addClass("col-md-6")
            heightPx = 50
        }
        val col2 = Container().apply {
            addClass("col-12")
            addClass("col-md-6")
            heightPx = 50
        }
        val col3 = Container().apply {
            addClass("col-12")
            addClass("col-md-6")
            heightPx = 50
        }
        row.addChild(col1)
        row.addChild(col2)
        row.addChild(col3)
        alienRoot.addChild(desktopOnly)
        alienRoot.addChild(mobileOnly)
        alienRoot.addChild(row)

        // At 500px (< 768px md breakpoint):
        session.forceFullRepaint(0f)
        assertEquals(0, assertNotNull(desktopOnly.nativeImpl).getBounds().width, "d-none d-md-flex must be hidden at 500px")
        assertTrue(assertNotNull(mobileOnly.nativeImpl).getBounds().width > 0, "d-flex d-md-none must be visible at 500px")

        val b1Mobile = assertNotNull(col1.nativeImpl).getBounds()
        val b2Mobile = assertNotNull(col2.nativeImpl).getBounds()
        val b3Mobile = assertNotNull(col3.nativeImpl).getBounds()
        assertTrue(b2Mobile.y >= b1Mobile.bottom, "At 500px, col-12 col-md-6 columns must stack vertically")
        assertTrue(b3Mobile.y >= b2Mobile.bottom, "At 500px, col-12 col-md-6 third column must stack on row 3")

        // Resize to 900px (>= 768px md breakpoint):
        session.resizeViewport(900, 600)
        session.forceFullRepaint(0f)
        assertTrue(assertNotNull(desktopOnly.nativeImpl).getBounds().width > 0, "d-none d-md-flex must be visible at 900px")
        assertEquals(0, assertNotNull(mobileOnly.nativeImpl).getBounds().width, "d-flex d-md-none must be hidden at 900px")

        val b1Desktop = assertNotNull(col1.nativeImpl).getBounds()
        val b2Desktop = assertNotNull(col2.nativeImpl).getBounds()
        val b3Desktop = assertNotNull(col3.nativeImpl).getBounds()
        assertEquals(b1Desktop.y, b2Desktop.y, "At 900px, first two col-md-6 columns must sit on the same row")
        assertTrue(b2Desktop.x >= b1Desktop.right, "At 900px, second col-md-6 column must be placed to the right of first")
        assertTrue(b3Desktop.y >= b1Desktop.bottom, "At 900px, third col-md-6 column (6+6+6=18 > 12) must wrap onto a second row")
    }

    @Test
    fun testDisplayTableRelativeOffsetsAndVerticalAlignBottom() {
        val alienRoot = AlienContainer().apply {
            widthPx = 300
            heightPx = 300
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val tableTrack = Container().apply {
            applyComputedStyle(
                ComputedStyle(
                    display = "table",
                    position = "relative",
                    left = "-100px",
                    top = "12px"
                )
            )
        }
        val cell1 = Container().apply {
            applyComputedStyle(ComputedStyle(display = "table-cell", width = "150px", height = "60px"))
        }
        val cell2 = Container().apply {
            applyComputedStyle(ComputedStyle(display = "table-cell", width = "150px", height = "60px"))
        }
        val cell3 = Container().apply {
            applyComputedStyle(ComputedStyle(display = "table-cell", width = "150px", height = "60px"))
        }
        tableTrack.addChild(cell1)
        tableTrack.addChild(cell2)
        tableTrack.addChild(cell3)
        alienRoot.addChild(tableTrack)

        val bottomAlignedBar = Container().apply {
            applyComputedStyle(
                ComputedStyle(
                    height = "80px",
                    verticalAlign = "bottom"
                )
            )
        }
        val inlineBtn = Button("Bottom Inline").apply {
            applyComputedStyle(ComputedStyle(display = "inline-block", width = "90px", height = "30px"))
        }
        bottomAlignedBar.addChild(inlineBtn)
        alienRoot.addChild(bottomAlignedBar)

        session.forceFullRepaint(0f)

        val trackBounds = assertNotNull(tableTrack.nativeImpl).getBounds()
        assertEquals(450, trackBounds.width, "display: table width must equal sum of table-cell widths (450px) without clamping to 300px parent")
        assertEquals(-100, trackBounds.x, "position: relative left: -100px must shift table track by -100px")
        assertEquals(12, trackBounds.y, "position: relative top: 12px must shift table track by +12px")

        val c1Bounds = assertNotNull(cell1.nativeImpl).getBounds()
        val c2Bounds = assertNotNull(cell2.nativeImpl).getBounds()
        val c3Bounds = assertNotNull(cell3.nativeImpl).getBounds()
        assertEquals(-100, c1Bounds.x)
        assertEquals(50, c2Bounds.x)
        assertEquals(200, c3Bounds.x)

        val barBounds = assertNotNull(bottomAlignedBar.nativeImpl).getBounds()
        val btnBounds = assertNotNull(inlineBtn.nativeImpl).getBounds()
        assertEquals(barBounds.bottom, btnBounds.bottom, "vertical-align: bottom must align inline-block child to bottom of container")
    }

    @Test
    fun testOverflowHiddenCanvasClippingInverseScaleHitTestingAndDragClickSuppression() {
        val alienRoot = AlienContainer().apply {
            widthPx = 400
            heightPx = 300
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        // 1. overflow: hidden container at (0, 0, 120, 80) with an off-screen child at left: 160px
        val clipBox = Container().apply {
            applyComputedStyle(
                ComputedStyle(
                    position = "absolute",
                    left = "0px",
                    top = "0px",
                    width = "120px",
                    height = "80px",
                    overflow = "hidden"
                )
            )
        }
        var clippedChildClicked = 0
        val offscreenBtn = Button("Offscreen", BootstrapType.DANGER).apply {
            applyComputedStyle(
                ComputedStyle(
                    position = "absolute",
                    left = "160px",
                    top = "10px",
                    width = "80px",
                    height = "40px"
                )
            )
            onClick = { clippedChildClicked++ }
        }
        clipBox.addChild(offscreenBtn)
        alienRoot.addChild(clipBox)

        // 2. Scaled button: 200x100 at (100, 100) with transform: scale(0.5) -> visual bounds are (150..250, 125..175)
        var scaledClicked = 0
        val scaledBtn = Button("Scaled", BootstrapType.SUCCESS).apply {
            applyComputedStyle(
                ComputedStyle(
                    position = "absolute",
                    left = "100px",
                    top = "100px",
                    width = "200px",
                    height = "100px",
                    transform = "scale(0.5)"
                )
            )
            onClick = { scaledClicked++ }
        }
        alienRoot.addChild(scaledBtn)

        session.forceFullRepaint(0f)

        // Verify offscreen child inside overflow: hidden is NOT hit at (180, 25)
        val hitAtOffscreen = session.eventDispatcher.hitTest(180f, 25f)
        assertNotNull(hitAtOffscreen)
        assertFalse(hitAtOffscreen === (offscreenBtn.nativeImpl as AlienImpl).node, "Offscreen child inside overflow: hidden must not be hit")

        // Verify inverse scale hit-testing: (110, 110) is inside unscaled 200x100 box (100..300, 100..200)
        // but OUTSIDE 0.5x center-scaled visual box (150..250, 125..175)
        val hitOutsideScaled = session.eventDispatcher.hitTest(110f, 110f)
        assertFalse(hitOutsideScaled === (scaledBtn.nativeImpl as AlienImpl).node, "Point outside 0.5x scaled bounds must not hit scaled button")

        val hitInsideScaled = session.eventDispatcher.hitTest(200f, 150f)
        assertSame((scaledBtn.nativeImpl as AlienImpl).node, hitInsideScaled, "Center point (200, 150) must hit 0.5x scaled button")

        // 3. Verify pointer drag click-suppression (> 6px movement suppresses onClick)
        session.eventDispatcher.dispatchPointerDown(PointerEvent(x = 200f, y = 150f))
        session.eventDispatcher.dispatchPointerMove(PointerEvent(x = 220f, y = 150f))
        session.eventDispatcher.dispatchPointerUp(PointerEvent(x = 220f, y = 150f))
        assertEquals(0, scaledClicked, "Dragging pointer by 20px (> 6px threshold) must suppress synthetic onClick on release")

        // Stationary click (<= 6px movement) must trigger onClick
        session.eventDispatcher.dispatchPointerDown(PointerEvent(x = 200f, y = 150f))
        session.eventDispatcher.dispatchPointerUp(PointerEvent(x = 202f, y = 150f))
        assertEquals(1, scaledClicked, "Click with <= 6px pointer movement must fire onClick")
    }
}
