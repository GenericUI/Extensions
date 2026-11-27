package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.ext.imageprocessing.context2d.DefaultCpuImageBuffer
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AlienW3cComplianceAndExtensionContractTest {

    private class W3cTestHostFactory : NativeFactory {
        override val backendTarget: BackendTarget = BackendTarget.Windows
        override val backendName: String = "w3c-test-alien-host"
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true

        private val delegate = AlienFactory(this)

        override fun createApplication(): ApplicationNativeImpl<*> = throw UnsupportedOperationException()
        override fun createWindow(): WindowNativeImpl<*> = throw UnsupportedOperationException()
        override fun createContainer(classPrefix: String) = delegate.createContainer(classPrefix)
        override fun createContainer(classPrefix: String, tagName: String) = delegate.createContainer(classPrefix, tagName)
        override fun createStyledContainer(classPrefix: String, tagName: String) = delegate.createStyledContainer(classPrefix, tagName)
        override fun createStyledComponent(classPrefix: String, tagName: String) = delegate.createStyledComponent(classPrefix, tagName)
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
        override fun createButton(classPrefix: String) = delegate.createButton(classPrefix)
        override fun createStyledButton(classPrefix: String) = delegate.createStyledButton(classPrefix)
        override fun createDropdown() = delegate.createDropdown()
        override fun createTextInput(multiline: Boolean) = delegate.createTextInput(multiline)
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String) =
            delegate.createStyledTextInput(multiline, classPrefix)
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

    private class DummyExtWidget(classPrefix: String = "dummy-ext") :
        AbstractComponent(componentClassPrefix = classPrefix, htmlTag = "div") {
        override fun createNativeImpl(factory: NativeFactory): NativeImpl<*> =
            factory.createExtensionNativeImpl(this) ?: DummyHostExtImpl()
    }

    private class DummyExtAlienImpl(
        session: AlienRenderSession?
    ) : AlienImpl(componentClassPrefix = "dummy-ext", tagName = "div", session = session)

    private class DummyHostExtImpl : NativeImpl<Any?> {
        override val nativeWidget: Any? = Any()
        override val componentClassPrefix: String = "dummy-host-ext"
        override fun setClass(className: String) {}
        override fun getClass(): String = ""
        override fun getClasses(): Set<String> = emptySet()
        override fun addClass(className: String) {}
        override fun removeClass(className: String) {}
        override fun hasClass(className: String): Boolean = false
        override fun setType(type: BootstrapType?) {}
        override fun getType(): BootstrapType? = null
        override fun setOutline(outline: Boolean) {}
        override fun isOutline(): Boolean = false
        override fun setSize(size: BootstrapSize?) {}
        override fun getSize(): BootstrapSize? = null
        override fun setBackgroundType(type: BootstrapType?) {}
        override fun getBackgroundType(): BootstrapType? = null
        override fun setTextColorType(type: BootstrapType?) {}
        override fun getTextColorType(): BootstrapType? = null
        override fun setBorderType(type: BootstrapType?) {}
        override fun getBorderType(): BootstrapType? = null
        override fun setTextAlignment(alignment: TextAlignment?) {}
        override fun getTextAlignment(): TextAlignment? = null
        override fun setFontWeight(weight: FontWeight?) {}
        override fun getFontWeight(): FontWeight? = null
        override fun setTextTransform(transform: TextTransform?) {}
        override fun getTextTransform(): TextTransform? = null
        override fun setTextOverflow(overflow: TextOverflow?) {}
        override fun getTextOverflow(): TextOverflow? = null
        override fun setBorderRadius(radius: BorderRadiusStyle?) {}
        override fun getBorderRadius(): BorderRadiusStyle? = null
        override fun setShadow(shadow: ShadowElevation?) {}
        override fun getShadow(): ShadowElevation? = null
        override fun setDisplay(display: DisplayMode?) {}
        override fun getDisplay(): DisplayMode? = null
        override fun setFlexDirection(direction: FlexDirection?) {}
        override fun getFlexDirection(): FlexDirection? = null
        override fun setFlexWrap(wrap: FlexWrap?) {}
        override fun getFlexWrap(): FlexWrap? = null
        override fun setJustifyContent(justify: JustifyContent?) {}
        override fun getJustifyContent(): JustifyContent? = null
        override fun setAlignItems(align: AlignItems?) {}
        override fun getAlignItems(): AlignItems? = null
        override fun setAlignSelf(align: AlignSelf?) {}
        override fun getAlignSelf(): AlignSelf? = null
        override fun setOverflow(overflow: OverflowBehavior?) {}
        override fun getOverflow(): OverflowBehavior? = null
        override fun setPosition(position: PositionMode?) {}
        override fun getPosition(): PositionMode? = null
        override fun setMarginAll(scale: SpacingScale?) {}
        override fun getMarginAll(): SpacingScale? = null
        override fun setPaddingAll(scale: SpacingScale?) {}
        override fun getPaddingAll(): SpacingScale? = null
        override fun setVisible(visible: Boolean) {}
        override fun isVisible(): Boolean = true
        override fun setEnabled(enabled: Boolean) {}
        override fun isEnabled(): Boolean = true
        override fun setOpacity(opacity: Float) {}
        override fun getOpacity(): Float = 1f
        override fun setTooltipText(tooltip: String?) {}
        override fun getTooltipText(): String? = null
        override fun setPreferredSize(size: Size?) {}
        override fun getBounds(): Rect = Rect(0, 0, 0, 0)
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
        override fun dispose() {}
    }

    private lateinit var hostFactory: W3cTestHostFactory

    @BeforeTest
    fun setUp() {
        AlienExtension.useSystemCanvasRenderer()
        AlienExtension.clearAlienExtensionImplsForTests()
        hostFactory = W3cTestHostFactory()
        GenericUI.initialize(hostFactory, "W3cAlienComplianceTest")
    }

    @AfterTest
    fun tearDown() {
        AlienExtension.clearAlienExtensionImplsForTests()
    }

    private fun precompiledSheet(name: String, css: String): PrecompiledStyleSheet {
        val parsed = CssStyleSheet.parse(css)
        return object : PrecompiledStyleSheet {
            override val name: String = name
            override val styleSheet: CssStyleSheet = parsed
            override val rawCss: String = css
            override val keyframes: Map<String, Map<String, Map<String, String>>>
                get() = parsed.keyframes
        }
    }

    private fun newPureCssSession(
        css: String,
        width: Int = 600,
        height: Int = 400
    ): AlienRenderSession {
        val session = AlienRenderSession(hostFactory, width, height, offscreenOnly = true)
        session.styleSheets.clear()
        session.styleSheets.add(precompiledSheet("PureW3cSheet", css))
        return session
    }

    private fun node(
        session: AlienRenderSession,
        tagName: String = "div",
        id: String? = null,
        vararg classes: String
    ): AlienWidgetNode {
        val w = AlienWidgetNode(componentClassPrefix = "", tagName = tagName, session = session)
        if (!id.isNullOrEmpty()) w.attributes["id"] = id
        if (classes.isNotEmpty()) {
            w.styleSnapshot = w.styleSnapshot.copy(customClasses = LinkedHashSet(classes.toList()))
        }
        return w
    }

    @Test
    fun testExtensionImplProviderContractAndDirectContainerAttachment() {
        val session = AlienRenderSession(hostFactory, 320, 240, offscreenOnly = true)
        val alienFactory = AlienFactory(hostFactory, session)
        val parentImpl = ContainerAlienImpl(componentClassPrefix = "container", tagName = "div", session = session)
        session.rootNode.addChild(parentImpl.node)

        var seenFactory: NativeFactory? = null
        var seenSession: AlienRenderSession? = null
        var callCount = 0

        val provider = AlienExtensionImplProvider { component, activeSession, factory ->
            callCount++
            seenSession = activeSession
            seenFactory = factory
            if (component is DummyExtWidget && component.componentClassPrefix == "alien-supported") {
                DummyExtAlienImpl(activeSession)
            } else {
                null
            }
        }

        // Idempotent registration
        AlienExtension.registerAlienExtensionImpl(provider)
        AlienExtension.registerAlienExtensionImpl(provider)

        val selfRenderedChild = DummyExtWidget("alien-supported")
        val selfRenderedImpl = alienFactory.createExtensionNativeImpl(selfRenderedChild)
        assertNotNull(selfRenderedImpl)
        assertTrue(selfRenderedImpl is DummyExtAlienImpl)
        assertEquals(1, callCount, "Duplicate provider registration must remain idempotent")
        assertSame(session, seenSession)
        assertSame(hostFactory, seenFactory)

        // ContainerAlienImpl must attach AlienImpl extension children directly without an underlay slot
        parentImpl.addChildNative(selfRenderedImpl, -1)
        assertTrue(session.underlaySlots.isEmpty(), "Self-rendered AlienImpl must NOT create underlay slot")
        assertTrue(parentImpl.node.children.contains(selfRenderedImpl.node))

        // Unmatched extension widget falls back to non-Alien impl -> underlay slot created cleanly
        val underlayFallbackWidget = DummyExtWidget("needs-host-underlay")
        assertNull(alienFactory.createExtensionNativeImpl(underlayFallbackWidget))
        val fallbackImpl = DummyHostExtImpl()
        parentImpl.addChildNative(fallbackImpl, -1)
        assertEquals(1, session.underlaySlots.size, "Non-Alien host extension impl must keep underlay slot fallback")

        parentImpl.removeChildNative(selfRenderedImpl)
        assertFalse(parentImpl.node.children.contains(selfRenderedImpl.node))

        AlienExtension.unregisterAlienExtensionImpl(provider)
        assertNull(alienFactory.createExtensionNativeImpl(selfRenderedChild))
    }

    @Test
    fun testOffscreenRenderSessionAndDirtyTileExtraction() {
        val session = newPureCssSession(
            """
            #stage { width: 128px; height: 96px; background-color: #102030; }
            #box { width: 40px; height: 30px; background-color: #ff0000; }
            #box.green { background-color: #00ff00; }
            """.trimIndent(),
            width = 128,
            height = 96
        )

        assertTrue(session.offscreenOnly)
        assertNull(session.canvas2D.nativeImpl, "offscreenOnly session must not attach a native Canvas2D widget")
        assertEquals(128, session.offscreenBuffer.width)
        assertEquals(96, session.offscreenBuffer.height)

        val stage = node(session, "div", "stage")
        val box = node(session, "div", "box")
        stage.addChild(box)
        session.rootNode.addChild(stage)

        // First frame captures full viewport tiles
        val initialTiles = session.consumeDirtyTiles(tileSize = 64)
        assertEquals(4, initialTiles.size)
        val topLeftTile = initialTiles.first { it.x == 0 && it.y == 0 }
        assertEquals(64, topLeftTile.width)
        assertEquals(64, topLeftTile.height)
        // Pixel (10, 10) inside #box should be solid red
        val p10 = (10 * topLeftTile.width + 10) * 4
        assertEquals(255, topLeftTile.rgbaBytes[p10].toInt() and 0xFF)
        assertEquals(0, topLeftTile.rgbaBytes[p10 + 1].toInt() and 0xFF)

        // Clean frame must emit zero dirty tiles
        val cleanTiles = session.consumeDirtyTiles(tileSize = 64)
        assertTrue(cleanTiles.isEmpty(), "Unchanged frame should emit zero dirty tiles")

        // Mutating box inside top-left tile repaints only tile(s) intersecting box
        box.styleSnapshot = box.styleSnapshot.copy(customClasses = linkedSetOf("green"))
        box.invalidateBounds()
        val mutatedTiles = session.consumeDirtyTiles(tileSize = 64)
        assertEquals(1, mutatedTiles.size)
        assertEquals(0, mutatedTiles[0].x)
        assertEquals(0, mutatedTiles[0].y)
        assertEquals(0, mutatedTiles[0].rgbaBytes[p10].toInt() and 0xFF)
        assertEquals(255, mutatedTiles[0].rgbaBytes[p10 + 1].toInt() and 0xFF)

        // Resizing viewport resizes offscreenBuffer and triggers full tile sync
        session.resizeViewport(160, 110)
        assertEquals(160, session.offscreenBuffer.width)
        assertEquals(110, session.offscreenBuffer.height)
        val resizedTiles = session.consumeDirtyTiles(tileSize = 64)
        assertEquals(6, resizedTiles.size)
    }

    @Test
    fun testFlexboxRowReverseOrderGrowShrinkAndWrapAlignContent() {
        val session = newPureCssSession(
            """
            #flex-row {
                display: flex;
                flex-direction: row-reverse;
                justify-content: flex-start;
                align-items: stretch;
                width: 300px;
                height: 60px;
            }
            #a { order: 2; flex-basis: 40px; flex-grow: 1; }
            #b { order: 1; flex-basis: 80px; flex-grow: 2; }
            #flex-wrap {
                display: flex;
                flex-wrap: wrap;
                align-content: space-between;
                column-gap: 10px;
                row-gap: 20px;
                width: 110px;
                height: 100px;
            }
            .cell { width: 50px; height: 25px; }
            """.trimIndent(),
            width = 500,
            height = 300
        )

        val row = node(session, "div", "flex-row")
        val a = node(session, "div", "a")
        val b = node(session, "div", "b")
        row.addChild(a)
        row.addChild(b)

        val wrap = node(session, "div", "flex-wrap")
        val c1 = node(session, "div", "c1", "cell")
        val c2 = node(session, "div", "c2", "cell")
        val c3 = node(session, "div", "c3", "cell")
        wrap.addChild(c1)
        wrap.addChild(c2)
        wrap.addChild(c3)

        session.rootNode.addChild(row)
        session.rootNode.addChild(wrap)

        session.layoutEngine.performLayout(session.rootNode, 500, 300)

        // Order sorts #b (order 1) before #a (order 2), then row-reverse places #b on the right!
        // Free space = 300 - (40 + 80) = 180; a gets +60 => 100, b gets +120 => 200
        assertEquals(200, b.bounds.width)
        assertEquals(100, a.bounds.width)
        assertEquals(100, b.bounds.x, "row-reverse places first visual item (#b) on the right")
        assertEquals(0, a.bounds.x)
        assertEquals(60, a.bounds.height, "stretch expands cross axis to 60px")
        assertEquals(60, b.bounds.height)

        // Wrapped container: 50 + 10 + 50 = 110 fits row 1; c3 wraps to row 2 at bottom due to align-content: space-between
        assertEquals(wrap.bounds.y, c1.bounds.y)
        assertEquals(c1.bounds.x + 60, c2.bounds.x)
        assertEquals(wrap.bounds.x, c3.bounds.x)
        assertEquals(wrap.bounds.y + 75, c3.bounds.y)
    }

    @Test
    fun testCssGridFrTracksTemplateAreasAndAutoFillSpan() {
        val session = newPureCssSession(
            """
            #grid-main {
                display: grid;
                width: 320px;
                height: 200px;
                grid-template-columns: 100px 1fr 2fr;
                grid-template-rows: 40px 60px;
                column-gap: 10px;
                row-gap: 10px;
            }
            #g-span { grid-column: 1 / span 2; grid-row: 1; }
            #g-third { grid-column: 3; grid-row: 1 / span 2; }
            #grid-areas {
                display: grid;
                width: 200px;
                height: 100px;
                grid-template-columns: 60px 140px;
                grid-template-rows: 30px 70px;
                grid-template-areas: "hdr hdr" "nav main";
            }
            #hdr { grid-area: hdr; }
            #nav { grid-area: nav; }
            #main { grid-area: main; }
            #grid-autofill {
                display: grid;
                width: 200px;
                grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
                grid-auto-rows: 25px;
            }
            """.trimIndent(),
            width = 500,
            height = 500
        )

        val gridMain = node(session, "div", "grid-main")
        val gSpan = node(session, "div", "g-span")
        val gThird = node(session, "div", "g-third")
        val gCell = node(session, "div", "g-cell")
        gridMain.addChild(gSpan)
        gridMain.addChild(gThird)
        gridMain.addChild(gCell)

        val gridAreas = node(session, "div", "grid-areas")
        val hdr = node(session, "div", "hdr")
        val nav = node(session, "div", "nav")
        val main = node(session, "div", "main")
        gridAreas.addChild(hdr)
        gridAreas.addChild(nav)
        gridAreas.addChild(main)

        val gridAuto = node(session, "div", "grid-autofill")
        val af1 = node(session, "div")
        val af2 = node(session, "div")
        val af3 = node(session, "div")
        gridAuto.addChild(af1)
        gridAuto.addChild(af2)
        gridAuto.addChild(af3)

        session.rootNode.addChild(gridMain)
        session.rootNode.addChild(gridAreas)
        session.rootNode.addChild(gridAuto)

        session.layoutEngine.performLayout(session.rootNode, 500, 500)

        // Tracks: 100px + 10 gap + 67px (1fr of 200) + 10 gap + 133px (2fr of 200) = 320
        assertEquals(0, gSpan.bounds.x)
        assertTrue(gSpan.bounds.width in 176..178, "Span across 100 + 10 + 67 expected ~177, got ${gSpan.bounds.width}")
        assertEquals(40, gSpan.bounds.height)
        assertEquals(110, gThird.bounds.height, "40 + 10 + 60 row span = 110")
        assertEquals(0, gCell.bounds.x, "Auto-placed cell lands at col 1 row 2")
        assertEquals(gridMain.bounds.y + 50, gCell.bounds.y)

        // Template areas
        assertEquals(200, hdr.bounds.width)
        assertEquals(30, hdr.bounds.height)
        assertEquals(60, nav.bounds.width)
        assertEquals(gridAreas.bounds.y + 30, nav.bounds.y)
        assertEquals(gridAreas.bounds.x + 60, main.bounds.x)
        assertEquals(140, main.bounds.width)

        // Auto-fill 200px / 90px => 2 columns of 100px each; 3rd child wraps to row 2
        assertEquals(100, af1.bounds.width)
        assertEquals(gridAuto.bounds.x + 100, af2.bounds.x)
        assertEquals(gridAuto.bounds.y + 25, af3.bounds.y)
    }

    @Test
    fun testHtmlTableLayoutColspanRowspanAndBoxSizingContentBoxVsBorderBox() {
        val session = newPureCssSession(
            """
            #tbl { display: table; width: 300px; border-collapse: collapse; }
            #cbox { width: 100px; height: 50px; padding: 10px; border: 5px solid #000000; }
            #bbox { box-sizing: border-box; width: 100px; height: 50px; padding: 10px; border: 5px solid #000000; }
            """.trimIndent(),
            width = 500,
            height = 400
        )

        val tbl = node(session, "table", "tbl")
        val thead = node(session, "thead")
        val trHead = node(session, "tr")
        val thSpan = node(session, "th").apply { attributes["colspan"] = "2"; preferredSize = Size(160, 24) }
        val th3 = node(session, "th").apply { preferredSize = Size(80, 24) }
        trHead.addChild(thSpan)
        trHead.addChild(th3)
        thead.addChild(trHead)

        val tbody = node(session, "tbody")
        val tr1 = node(session, "tr")
        val tdRowSpan = node(session, "td").apply { attributes["rowspan"] = "2"; preferredSize = Size(60, 20) }
        val td1b = node(session, "td").apply { preferredSize = Size(80, 30) }
        val td1c = node(session, "td").apply { preferredSize = Size(80, 30) }
        tr1.addChild(tdRowSpan)
        tr1.addChild(td1b)
        tr1.addChild(td1c)

        val tr2 = node(session, "tr")
        val td2b = node(session, "td").apply { preferredSize = Size(80, 25) }
        val td2c = node(session, "td").apply { preferredSize = Size(80, 25) }
        tr2.addChild(td2b)
        tr2.addChild(td2c)

        tbody.addChild(tr1)
        tbody.addChild(tr2)
        tbl.addChild(thead)
        tbl.addChild(tbody)

        val cbox = node(session, "div", "cbox")
        val bbox = node(session, "div", "bbox")

        session.rootNode.addChild(tbl)
        session.rootNode.addChild(cbox)
        session.rootNode.addChild(bbox)

        session.layoutEngine.performLayout(session.rootNode, 500, 400)

        assertEquals(thSpan.bounds.right, th3.bounds.x)
        assertEquals(300, thSpan.bounds.width + th3.bounds.width)
        assertEquals(td1b.bounds.width + td1c.bounds.width, tbl.bounds.width - tdRowSpan.bounds.width)
        assertEquals(55, tdRowSpan.bounds.height, "rowspan=2 must cover row1(30) + row2(25)")
        assertEquals(td1b.bounds.x, td2b.bounds.x, "rowspan in col 0 shifts row 2 cells into cols 1 and 2")

        assertEquals(CssBoxSizing.CONTENT_BOX, cbox.resolveVisualStyle().boxSizing)
        assertEquals(130, cbox.bounds.width, "Pure W3C content-box adds padding(20)+border(10) to 100")
        assertEquals(80, cbox.bounds.height)

        assertEquals(CssBoxSizing.BORDER_BOX, bbox.resolveVisualStyle().boxSizing)
        assertEquals(100, bbox.bounds.width)
        assertEquals(50, bbox.bounds.height)
    }

    @Test
    fun testCustomVarsStickyPositionTransformHitTestAndWheelScroll() {
        val session = newPureCssSession(
            """
            #vars-parent {
                --gap-w: 40px;
                width: 200px;
                height: 200px;
            }
            #vars-child {
                width: calc(100% - var(--gap-w));
                height: clamp(20px, 50%, 70px);
                background-color: #112233;
            }
            #scroller {
                overflow-y: auto;
                width: 200px;
                height: 100px;
            }
            #sticky {
                position: sticky;
                top: 10px;
                width: 180px;
                height: 20px;
            }
            #spacer { width: 180px; height: 220px; }
            #clipper {
                overflow: hidden;
                width: 80px;
                height: 80px;
            }
            #overflowed-child {
                width: 140px;
                height: 40px;
            }
            #ghost-overlay {
                width: 80px;
                height: 40px;
                pointer-events: none;
            }
            #invisible-op {
                width: 50px;
                height: 50px;
                opacity: 0;
            }
            #hidden-vis {
                width: 50px;
                height: 50px;
                visibility: hidden;
            }
            """.trimIndent(),
            width = 500,
            height = 600
        )

        val varsParent = node(session, "div", "vars-parent")
        val varsChild = node(session, "div", "vars-child")
        varsParent.addChild(varsChild)

        val scroller = node(session, "div", "scroller")
        val sticky = node(session, "div", "sticky")
        val spacer = node(session, "div", "spacer")
        scroller.addChild(sticky)
        scroller.addChild(spacer)

        val clipper = node(session, "div", "clipper")
        val overflowedChild = node(session, "div", "overflowed-child")
        val ghostOverlay = node(session, "div", "ghost-overlay")
        clipper.addChild(overflowedChild)
        clipper.addChild(ghostOverlay)

        val opZero = node(session, "div", "invisible-op")
        val visHidden = node(session, "div", "hidden-vis")

        session.rootNode.addChild(varsParent)
        session.rootNode.addChild(scroller)
        session.rootNode.addChild(clipper)
        session.rootNode.addChild(opZero)
        session.rootNode.addChild(visHidden)

        session.layoutEngine.performLayout(session.rootNode, 500, 600)

        assertEquals(160, varsChild.bounds.width, "calc(100% - var(--gap-w)) on 200px parent => 160px")
        assertEquals(70, varsChild.bounds.height, "clamp(20px, 50%, 70px) on 200px parent => 70px")

        // Scroll down 60px via WheelEvent and relayout
        assertEquals(OverflowBehavior.AUTO, scroller.resolveVisualStyle().overflowY)
        val scrollerCenterX = scroller.bounds.x + 20f
        val scrollerCenterY = scroller.bounds.y + 20f
        assertTrue(session.eventDispatcher.dispatchWheel(WheelEvent(x = scrollerCenterX, y = scrollerCenterY, deltaX = 0f, deltaY = 60f)))
        assertEquals(60, scroller.scrollOffsetY)
        session.layoutEngine.performLayout(session.rootNode, 500, 600)
        assertEquals(scroller.bounds.y + 10, sticky.bounds.y, "position: sticky pins top edge at top: 10px during scroll")

        // overflow: hidden must prevent hit-testing outside clipper bounds (x + 120 > clipper.right)
        assertNull(session.eventDispatcher.elementFromPoint(clipper.bounds.x + 120f, overflowedChild.bounds.y + 10f))
        assertSame(overflowedChild, session.eventDispatcher.elementFromPoint(clipper.bounds.x + 10f, overflowedChild.bounds.y + 10f))

        // opacity: 0 MUST still receive pointer hit tests per W3C spec; visibility: hidden MUST NOT
        assertSame(opZero, session.eventDispatcher.elementFromPoint(opZero.bounds.x + 10f, opZero.bounds.y + 10f))
        val visHit = session.eventDispatcher.elementFromPoint(visHidden.bounds.x + 10f, visHidden.bounds.y + 10f)
        assertFalse(visHit === visHidden, "visibility: hidden node must not be returned by elementFromPoint")

        // Move pointer-events: none node right on top of overflowedChild and confirm passthrough
        ghostOverlay.bounds = Rect(overflowedChild.bounds.x, overflowedChild.bounds.y, 40, 40)
        assertSame(overflowedChild, session.eventDispatcher.elementFromPoint(overflowedChild.bounds.x + 15f, overflowedChild.bounds.y + 15f))
    }

    @Test
    fun testPseudoElementsKeyframesAndClippedCustomPaint() {
        val session = newPureCssSession(
            """
            @keyframes fadeShift {
                0% { opacity: 0; }
                100% { opacity: 1; }
            }
            #badge {
                width: 60px;
                height: 60px;
                border-radius: 20px;
                overflow: hidden;
                opacity: 0.5;
                animation-name: fadeShift;
                animation-duration: 100ms;
                animation-fill-mode: forwards;
            }
            #badge::before {
                content: "★";
                color: #ffff00;
            }
            """.trimIndent(),
            width = 100,
            height = 100
        )

        val srcImg = DefaultCpuImageBuffer(width = 40, height = 40)
        val bluePixels = ByteArray(40 * 40 * 4)
        for (i in 0 until 40 * 40) {
            val base = i * 4
            bluePixels[base] = 0
            bluePixels[base + 1] = 0
            bluePixels[base + 2] = 255.toByte()
            bluePixels[base + 3] = 255.toByte()
        }
        srcImg.writePixelsRgba(bluePixels, 40, 40, 0, 0)

        val badge = node(session, "div", "badge").apply {
            customPaint = { ctx, bounds, style ->
                AlienBootstrapPainter.paintImageBuffer(ctx, bounds, srcImg, ObjectFit.FILL, style)
            }
        }
        session.rootNode.addChild(badge)

        // Resolve and step keyframes across two frames
        session.renderFrameToBuffer(deltaMs = 0f)
        val badgeStyle = badge.resolveVisualStyle()
        assertNotNull(badgeStyle.beforeStyle)
        assertEquals("★", badgeStyle.beforeStyle?.content)

        val bufAfterHalf = session.renderFrameToBuffer(deltaMs = 100f)
        // Center pixel of badge at (30, 30) should carry blue channel from srcImg
        val rgba = bufAfterHalf.readPixelsRgba(0, 0, 100, 100)
        val centerIdx = (30 * 100 + 30) * 4
        assertTrue((rgba[centerIdx + 2].toInt() and 0xFF) > 0, "CustomPaint buffer should blit blue channel into center")
        // Top-left extreme corner (0, 0) is outside 20px rounded corner clip -> must stay transparent
        assertEquals(0, rgba[3].toInt() and 0xFF, "Rounded clip must keep outer corner (0, 0) transparent")
    }
}
