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
        override val backendName: String = "mock-host-os"

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

        override fun createButton(classPrefix: String): ButtonNativeImpl<*> =
            HostOsButtonImpl(classPrefix)

        // Delegate remaining methods to standard implementations for test completeness
        override fun createApplication(): ApplicationNativeImpl<*> = throw UnsupportedOperationException()
        override fun createWindow(): WindowNativeImpl<*> = throw UnsupportedOperationException()
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
        assertIs<CollapseAlienImpl>(collapse.nativeImpl)
        assertIs<TabAlienImpl>(tab.nativeImpl)
        assertIs<TabContainerAlienImpl>(tabContainer.nativeImpl)
        assertIs<NavbarAlienImpl>(navbar.nativeImpl)
        assertIs<BottomNavigationBarAlienImpl>(bottomNav.nativeImpl)
        assertIs<ProgressBarAlienImpl>(progressBar.nativeImpl)
        assertIs<SpinnerAlienImpl>(spinner.nativeImpl)
        assertIs<PopoverAlienImpl>(popover.nativeImpl)
        assertIs<SplitPaneAlienImpl>(splitPane.nativeImpl)
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
        // Custom mock extension component (representing GLCanvas / VKCanvas / VideoPlayer / WebView)
        class MockExtensionGlCanvas : AbstractComponent("gl-canvas", "canvas") {
            init {
                widthPx = 200
                heightPx = 150
            }

            override fun createNativeImpl(factory: NativeFactory): NativeImpl<*> =
                hostFactory.createButton("gl-canvas-native")
        }

        val alienRoot = AlienContainer().apply {
            widthPx = 500
            heightPx = 400
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val extCanvas = MockExtensionGlCanvas()
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
            positionMode = PositionMode.ABSOLUTE
            leftOffset = "${slotBounds.x + 10}px"
            topOffset = "${slotBounds.y + 10}px"
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
        alienRoot.ensureNativeImpl()
        alienRoot.applyComputedStyle(
            ComputedStyle(
                rawDeclarations = mapOf(
                    "--bs-primary" to "#ff5500"
                )
            )
        )

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
        fragment.htmlTag = "genericui:root"
        fragment.widthPx = 200
        fragment.heightPx = 150
        fragment.applyComputedStyle(
            ComputedStyle(
                backgroundImageUrl = "bg.jpg?2",
                rawDeclarations = mapOf(
                    "background-image" to "url('bg.jpg?2')",
                    "background-size" to "cover",
                    "background-position" to "center center",
                    "background-repeat" to "no-repeat"
                )
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
                    rawDeclarations = mapOf(
                        "background" to "url('bg.jpg?2') no-repeat center center",
                        "background-size" to "100% 100%"
                    )
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
}
