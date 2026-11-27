package com.nexustools.genericui.ext.svg.alien

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.alien.AlienContainer
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienFactory
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.ext.svg.Svg
import com.nexustools.genericui.ext.svg.SvgCircle
import com.nexustools.genericui.ext.svg.SvgGroup
import com.nexustools.genericui.ext.svg.SvgPaint
import com.nexustools.genericui.ext.svg.SvgRect
import com.nexustools.genericui.ext.svg.SvgViewBox
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
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssLength
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SvgAlienExtensionTest {

    private class MockHostOsNativeFactory : NativeFactory {
        override val backendTarget: BackendTarget = BackendTarget.Windows
        override val backendName: String = "mock-host-os"
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true

        private val delegate = AlienFactory(this)

        inner class HostOsContainerImpl(
            override val componentClassPrefix: String,
            tagName: String
        ) : ContainerNativeImpl<Any?> by ContainerAlienImpl(componentClassPrefix, tagName) {
            val attachedNativeChildren = mutableListOf<NativeImpl<*>>()
            override val nativeWidget: Any = "host-container:$componentClassPrefix:$tagName"

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

        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
            HostOsContainerImpl(classPrefix, "div")

        override fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> =
            HostOsContainerImpl(classPrefix, tagName)

        override fun createStyledContainer(classPrefix: String, tagName: String) =
            delegate.createStyledContainer(classPrefix, tagName)

        override fun createStyledComponent(classPrefix: String, tagName: String) =
            delegate.createStyledComponent(classPrefix, tagName)

        override fun createButton(classPrefix: String) = delegate.createButton(classPrefix)
        override fun createStyledButton(classPrefix: String) = delegate.createStyledButton(classPrefix)
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
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String) =
            delegate.createStyledTextInput(multiline, classPrefix)
        override fun createSelect() = delegate.createSelect()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean) =
            delegate.createCheckbox(switchStyle, radioStyle)

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
        GenericUI.initialize(hostFactory, "SvgAlienTest")
        SvgAlienExtension.register()
    }

    @AfterTest
    fun tearDown() {
        SvgAlienExtension.unregister()
        AlienExtension.useSystemCanvasRenderer()
    }

    @Test
    fun testRegistrationAndIdempotencyAndFactoryResolution() {
        assertTrue(SvgAlienExtension.isRegistered)
        // Idempotent repeat registration
        SvgAlienExtension.register()
        assertTrue(SvgAlienExtension.isRegistered)

        val session = AlienRenderSession(hostFactory = hostFactory, initialWidth = 320, initialHeight = 240)
        val svg = Svg(
            viewBox = SvgViewBox(0f, 0f, 100f, 100f),
            svgWidth = 100f,
            svgHeight = 100f
        )

        val created = session.alienFactory.createExtensionNativeImpl(svg)
        assertIs<SvgAlienImpl>(created)

        SvgAlienExtension.unregister()
        assertFalse(SvgAlienExtension.isRegistered)
        assertNull(session.alienFactory.createExtensionNativeImpl(svg))

        // Re-register cleanly
        SvgAlienExtension.register()
        assertTrue(SvgAlienExtension.isRegistered)
        assertIs<SvgAlienImpl>(session.alienFactory.createExtensionNativeImpl(svg))
    }

    @Test
    fun testSvgInsideAlienContainerRendersShapesAndPreservesBackgroundAndCache() {
        val alienRoot = AlienContainer().apply {
            widthPx = 300
            heightPx = 220
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)
        val bg = RgbaColor(20, 40, 60, 255)
        session.rootNode.inlineComponentStyle = ComponentStyle().apply { setBackgroundColor(bg) }

        val svg = Svg(
            viewBox = SvgViewBox(0f, 0f, 100f, 100f),
            svgWidth = 100f,
            svgHeight = 100f
        ).apply {
            addChild(
                SvgRect(x = 10f, y = 10f, width = 35f, height = 35f).apply {
                    fill = SvgPaint.SolidColor(RgbaColor(240, 20, 20, 255))
                }
            )
            addChild(
                SvgCircle(cx = 70f, cy = 70f, r = 20f).apply {
                    fill = SvgPaint.SolidColor(RgbaColor(20, 40, 240, 255))
                }
            )
        }
        alienRoot.addChild(svg)

        val impl = assertIs<SvgAlienImpl>(svg.nativeImpl)
        assertTrue(
            session.underlaySlots.isEmpty(),
            "Svg in AlienContainer must render inline without creating an underlay hole slot"
        )

        session.forceFullRepaint(0f)

        val bounds = impl.getBounds()
        assertEquals(100, bounds.width)
        assertEquals(100, bounds.height)
        assertEquals(100, impl.renderedWidthPx)
        assertEquals(100, impl.renderedHeightPx)
        assertEquals(svg.sceneVersion, impl.renderedSceneVersion)

        // Transparent SVG corner preserves Alien container background via alpha-blended 9-arg drawImage
        val transparentCorner = session.canvas2D.imageBuffer.getPixel(bounds.x + 4, bounds.y + 4)
        assertEquals(bg.r, transparentCorner.r)
        assertEquals(bg.g, transparentCorner.g)
        assertEquals(bg.b, transparentCorner.b)
        assertEquals(255, transparentCorner.a)

        val redPixel = session.canvas2D.imageBuffer.getPixel(bounds.x + 25, bounds.y + 25)
        assertTrue(redPixel.r > 220 && redPixel.g < 35 && redPixel.b < 35 && redPixel.a == 255)

        val bluePixel = session.canvas2D.imageBuffer.getPixel(bounds.x + 70, bounds.y + 70)
        assertTrue(bluePixel.b > 220 && bluePixel.r < 35 && bluePixel.g < 55 && bluePixel.a == 255)

        // Unchanged scene + dimensions must reuse the cached ImageBuffer instance
        val cachedBuffer = assertNotNull(impl.renderedBuffer)
        session.forceFullRepaint(0f)
        assertSame(cachedBuffer, impl.renderedBuffer)
    }

    @Test
    fun testSvgLiveDomMutationReRasterizesAndUpdatesAlienFramebuffer() {
        val alienRoot = AlienContainer().apply {
            widthPx = 300
            heightPx = 220
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)
        val bg = RgbaColor(20, 40, 60, 255)
        session.rootNode.inlineComponentStyle = ComponentStyle().apply { setBackgroundColor(bg) }

        val circle = SvgCircle(cx = 25f, cy = 65f, r = 18f).apply {
            fill = SvgPaint.SolidColor(RgbaColor(240, 30, 30, 255))
        }
        val svg = Svg(
            viewBox = SvgViewBox(0f, 0f, 100f, 100f),
            svgWidth = 100f,
            svgHeight = 100f
        ).apply {
            addChild(circle)
        }
        alienRoot.addChild(svg)

        val impl = assertIs<SvgAlienImpl>(svg.nativeImpl)
        session.forceFullRepaint(0f)

        val bounds = impl.getBounds()
        val initialLeft = session.canvas2D.imageBuffer.getPixel(bounds.x + 25, bounds.y + 65)
        assertTrue(initialLeft.r > 220 && initialLeft.g < 45 && initialLeft.a == 255)

        val initialRight = session.canvas2D.imageBuffer.getPixel(bounds.x + 75, bounds.y + 65)
        assertEquals(bg.r, initialRight.r)
        assertEquals(bg.g, initialRight.g)
        assertEquals(bg.b, initialRight.b)

        val versionBefore = svg.sceneVersion
        val bufferBefore = assertNotNull(impl.renderedBuffer)

        // Live DOM mutations: change color, move circle right, add a yellow rect top-left
        circle.fill = SvgPaint.SolidColor(RgbaColor(20, 220, 60, 255))
        circle.cx = 75f
        svg.addChild(
            SvgRect(x = 10f, y = 10f, width = 30f, height = 30f).apply {
                fill = SvgPaint.SolidColor(RgbaColor(245, 220, 20, 255))
            }
        )

        assertTrue(svg.sceneVersion > versionBefore)

        val dirtyRects = session.repaintIfDirty(0f)
        assertTrue(dirtyRects.isNotEmpty())
        assertEquals(svg.sceneVersion, impl.renderedSceneVersion)
        assertNotSame(bufferBefore, impl.renderedBuffer)

        // Old circle spot is restored to host background, moved circle is green, new rect is yellow
        val clearedLeft = session.canvas2D.imageBuffer.getPixel(bounds.x + 25, bounds.y + 65)
        assertEquals(bg.r, clearedLeft.r)
        assertEquals(bg.g, clearedLeft.g)
        assertEquals(bg.b, clearedLeft.b)

        val movedRight = session.canvas2D.imageBuffer.getPixel(bounds.x + 75, bounds.y + 65)
        assertTrue(movedRight.g > 200 && movedRight.r < 35 && movedRight.b < 75 && movedRight.a == 255)

        val addedYellow = session.canvas2D.imageBuffer.getPixel(bounds.x + 22, bounds.y + 22)
        assertTrue(addedYellow.r > 220 && addedYellow.g > 200 && addedYellow.b < 40 && addedYellow.a == 255)
    }

    @Test
    fun testSvgIntrinsicSizingW3CRules() {
        val alienRoot = AlienContainer().apply {
            widthPx = 500
            heightPx = 400
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        // 1. Default W3C <svg> fallback (300x150), not stretched across 500px parent
        val defaultSvg = Svg()
        alienRoot.addChild(defaultSvg)
        val defaultImpl = assertIs<SvgAlienImpl>(defaultSvg.nativeImpl)

        // 2. Single-axis svgWidth + viewBox preserves aspect ratio (120 x 60)
        val ratioSvg = Svg(
            viewBox = SvgViewBox(0f, 0f, 200f, 100f),
            svgWidth = 120f
        )
        alienRoot.addChild(ratioSvg)
        val ratioImpl = assertIs<SvgAlienImpl>(ratioSvg.nativeImpl)

        // 3. Explicit preferred widthPx overrides intrinsic size while preserving viewBox ratio (160 x 80)
        val preferredSvg = Svg(
            viewBox = SvgViewBox(0f, 0f, 100f, 50f)
        ).apply {
            widthPx = 160
        }
        alienRoot.addChild(preferredSvg)
        val preferredImpl = assertIs<SvgAlienImpl>(preferredSvg.nativeImpl)

        // 4. CSS width style overrides intrinsic size while preserving viewBox ratio (180 x 60)
        val cssSvg = Svg(
            viewBox = SvgViewBox(0f, 0f, 90f, 30f)
        ).apply {
            getStyle().setWidth(CssLength.px(180f))
        }
        alienRoot.addChild(cssSvg)
        val cssImpl = assertIs<SvgAlienImpl>(cssSvg.nativeImpl)

        session.forceFullRepaint(0f)

        assertEquals(300, defaultImpl.getBounds().width)
        assertEquals(150, defaultImpl.getBounds().height)

        assertEquals(120, ratioImpl.getBounds().width)
        assertEquals(60, ratioImpl.getBounds().height)

        assertEquals(160, preferredImpl.getBounds().width)
        assertEquals(80, preferredImpl.getBounds().height)
        assertEquals(160, preferredImpl.renderedWidthPx)
        assertEquals(80, preferredImpl.renderedHeightPx)

        assertEquals(180, cssImpl.getBounds().width)
        assertEquals(60, cssImpl.getBounds().height)
    }

    @Test
    fun testSvgPointerHoverClickAndTransformHitTesting() {
        val alienRoot = AlienContainer().apply {
            widthPx = 300
            heightPx = 300
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        var circleEnterCount = 0
        var circleLeaveCount = 0
        var circleClickCount = 0
        var groupEnterCount = 0
        var groupLeaveCount = 0
        var groupClickCount = 0
        var svgClickCount = 0

        val circle = SvgCircle(cx = 30f, cy = 40f, r = 10f).apply {
            fill = SvgPaint.SolidColor(RgbaColor(10, 180, 240, 255))
            onPointerEnter = { circleEnterCount++ }
            onPointerLeave = { circleLeaveCount++ }
            onClick = { circleClickCount++ }
        }
        val group = SvgGroup().apply {
            setTransform("translate(20 10)")
            onPointerEnter = { groupEnterCount++ }
            onPointerLeave = { groupLeaveCount++ }
            onClick = { groupClickCount++ }
            addChild(circle)
        }
        val svg = Svg(
            viewBox = SvgViewBox(0f, 0f, 100f, 100f),
            svgWidth = 200f,
            svgHeight = 200f
        ).apply {
            onClick = { svgClickCount++ }
            addChild(group)
        }
        alienRoot.addChild(svg)

        val impl = assertIs<SvgAlienImpl>(svg.nativeImpl)
        session.forceFullRepaint(0f)

        val bounds = impl.getBounds()
        assertEquals(200, bounds.width)
        assertEquals(200, bounds.height)

        // Transformed circle center in viewBox is (30+20, 40+10) = (50, 50) -> (100, 100) in 200x200 viewport
        assertSame(circle, impl.hitTestViewport(100f, 100f))
        assertNull(impl.hitTestViewport(10f, 10f))

        val hitX = bounds.x + 100f
        val hitY = bounds.y + 100f
        assertTrue(session.eventDispatcher.dispatchPointerMove(PointerEvent(x = hitX, y = hitY)))
        assertEquals(1, circleEnterCount)
        assertEquals(1, groupEnterCount)
        assertEquals(0, circleLeaveCount)

        assertTrue(session.eventDispatcher.dispatchPointerDown(PointerEvent(x = hitX, y = hitY)))
        assertTrue(session.eventDispatcher.dispatchPointerUp(PointerEvent(x = hitX, y = hitY)))
        assertEquals(1, circleClickCount)
        assertEquals(1, groupClickCount)
        assertEquals(1, svgClickCount)

        // Move off the shape inside the SVG viewport -> triggers pointerLeave
        val missX = bounds.x + 10f
        val missY = bounds.y + 10f
        assertTrue(session.eventDispatcher.dispatchPointerMove(PointerEvent(x = missX, y = missY)))
        assertEquals(1, circleLeaveCount)
        assertEquals(1, groupLeaveCount)
    }
}
