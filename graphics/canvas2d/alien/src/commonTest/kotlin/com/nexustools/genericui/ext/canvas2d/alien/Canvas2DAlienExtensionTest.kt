package com.nexustools.genericui.ext.canvas2d.alien

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.alien.AlienContainer
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienFactory
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Canvas2DAlienExtensionTest {

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
        GenericUI.initialize(hostFactory, "Canvas2DAlienTest")
        Canvas2DAlienExtension.register()
    }

    @AfterTest
    fun tearDown() {
        Canvas2DAlienExtension.unregister()
        AlienExtension.useSystemCanvasRenderer()
    }

    @Test
    fun testRegistrationAndIdempotencyAndSessionCanvasIsolation() {
        assertTrue(Canvas2DAlienExtension.isRegistered)
        // Idempotent second call
        Canvas2DAlienExtension.register()
        assertTrue(Canvas2DAlienExtension.isRegistered)

        val session = AlienRenderSession(hostFactory = hostFactory, initialWidth = 320, initialHeight = 240)
        // AlienRenderSession.canvas2D uses hostFactory directly and must NEVER be replaced with Canvas2DAlienImpl
        assertFalse(session.canvas2D.nativeImpl is Canvas2DAlienImpl)
        assertSame(null, session.alienFactory.createExtensionNativeImpl(session.canvas2D))

        val childCanvas = Canvas2D(width = 100, height = 60)
        val createdImpl = session.alienFactory.createExtensionNativeImpl(childCanvas)
        assertIs<Canvas2DAlienImpl>(createdImpl)

        Canvas2DAlienExtension.unregister()
        assertFalse(Canvas2DAlienExtension.isRegistered)
        assertSame(null, session.alienFactory.createExtensionNativeImpl(childCanvas))

        // Re-register cleanly
        Canvas2DAlienExtension.register()
        assertTrue(Canvas2DAlienExtension.isRegistered)
        assertIs<Canvas2DAlienImpl>(session.alienFactory.createExtensionNativeImpl(childCanvas))
    }

    @Test
    fun testCanvas2DInsideAlienContainerDrawsShapesAndUpdatesAlienFramebuffer() {
        val alienRoot = AlienContainer().apply {
            widthPx = 300
            heightPx = 200
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val canvas = Canvas2D(width = 120, height = 80, doubleBuffer = true)
        alienRoot.addChild(canvas)

        val impl = assertIs<Canvas2DAlienImpl>(canvas.nativeImpl)
        assertTrue(
            session.underlaySlots.isEmpty(),
            "Canvas2D in AlienContainer must render inline without creating an underlay hole slot"
        )

        val ctx = canvas.getContext()
        ctx.setFillColor(RgbaColor(255, 0, 0, 255))
        ctx.fillRect(0f, 0f, 120f, 80f)
        ctx.setFillColor(RgbaColor(0, 0, 255, 255))
        ctx.fillRect(20f, 15f, 60f, 40f)
        canvas.swapBuffers()

        session.forceFullRepaint(0f)

        val redSample = session.canvas2D.imageBuffer.getPixel(10, 10)
        assertTrue(redSample.r > 240 && redSample.g < 15 && redSample.b < 15 && redSample.a == 255)

        val blueSample = session.canvas2D.imageBuffer.getPixel(40, 30)
        assertTrue(blueSample.b > 240 && blueSample.r < 15 && blueSample.g < 15 && blueSample.a == 255)

        val implPixel = impl.getPixel(40, 30)
        assertTrue(implPixel.b > 240 && implPixel.a == 255)

        // Mutate canvas pixels and swap buffers -> dirty repaint updates Alien framebuffer
        ctx.setFillColor(RgbaColor(0, 220, 40, 255))
        ctx.fillRect(20f, 15f, 60f, 40f)
        canvas.swapBuffers()

        val dirtyRects = session.repaintIfDirty(0f)
        assertTrue(dirtyRects.isNotEmpty())
        val greenSample = session.canvas2D.imageBuffer.getPixel(40, 30)
        assertTrue(greenSample.g > 200 && greenSample.r < 25 && greenSample.b < 60 && greenSample.a == 255)
    }

    @Test
    fun testCanvas2DAlienResizeInputForwardingAndScrollCanvas2D() {
        val alienRoot = AlienContainer().apply {
            widthPx = 400
            heightPx = 300
        }
        alienRoot.ensureNativeImpl()
        val session = assertNotNull(alienRoot.renderSession)

        val canvas = Canvas2D(width = 100, height = 80)
        var resizedTo: Pair<Int, Int>? = null
        var pointerDownLocal: Pair<Float, Float>? = null
        var wheelDeltaY = 0f
        var lastKey = ""

        canvas.onPointerDown = { ev -> pointerDownLocal = ev.x to ev.y }
        canvas.onWheel = { ev -> wheelDeltaY = ev.deltaY }
        canvas.onKeyDown = { ev -> lastKey = ev.key }

        alienRoot.addChild(canvas)
        val impl = assertIs<Canvas2DAlienImpl>(canvas.nativeImpl)
        impl.setOnSurfaceResizedListener { w, h -> resizedTo = w to h }

        canvas.syncSurfaceSize(160, 100)
        assertEquals(160 to 100, resizedTo)
        assertEquals(160, canvas.surfaceWidth)
        assertEquals(100, canvas.surfaceHeight)

        session.forceFullRepaint(0f)
        val bounds = impl.getBounds()
        assertEquals(160, bounds.width)
        assertEquals(100, bounds.height)

        assertTrue(
            session.eventDispatcher.dispatchPointerDown(
                PointerEvent(x = bounds.x + 25f, y = bounds.y + 35f)
            )
        )
        assertEquals(25f to 35f, pointerDownLocal)

        assertTrue(
            session.eventDispatcher.dispatchWheel(
                WheelEvent(x = bounds.x + 10f, y = bounds.y + 10f, deltaX = 0f, deltaY = 48f)
            )
        )
        assertEquals(48f, wheelDeltaY)

        assertTrue(session.eventDispatcher.dispatchKeyDown(KeyEvent(key = "Enter")))
        assertEquals("Enter", lastKey)

        // Verify ScrollCanvas2D inside AlienContainer attaches Canvas2DAlienImpl for its viewportCanvas
        val scrollCanvas = ScrollCanvas2D(
            viewportWidth = 100,
            viewportHeight = 80,
            contentWidth = 400,
            contentHeight = 300
        )
        alienRoot.addChild(scrollCanvas)
        assertIs<ContainerAlienImpl>(scrollCanvas.nativeImpl)
        assertIs<Canvas2DAlienImpl>(scrollCanvas.viewportCanvas.nativeImpl)
        scrollCanvas.getContext().apply {
            setFillColor(RgbaColor(240, 140, 20, 255))
            fillRect(0f, 0f, 100f, 80f)
        }
        scrollCanvas.swapBuffers()
        session.forceFullRepaint(0f)
        val scrollBounds = assertNotNull(scrollCanvas.viewportCanvas.nativeImpl).getBounds()
        val sampled = session.canvas2D.imageBuffer.getPixel(scrollBounds.x + 20, scrollBounds.y + 20)
        assertTrue(sampled.r > 220 && sampled.g in 120..160 && sampled.b < 40)
    }
}
