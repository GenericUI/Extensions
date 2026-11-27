package com.nexustools.genericui.ext.media.alien

import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienFactory
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmMediaAlienTestMp4PlaybackTest {

    private class JvmTestHostNativeFactory : NativeFactory {
        override val backendTarget: BackendTarget = BackendTarget.Windows
        override val backendName: String = "jvm-test-host"
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true

        private val delegate = AlienFactory(this)

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
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
            ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = "div")
        override fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> =
            ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = tagName)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
            delegate.createStyledContainer(classPrefix, tagName)
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
            delegate.createStyledComponent(classPrefix, tagName)
        override fun createBookContainer() = delegate.createBookContainer()
        override fun createScrollView() = delegate.createScrollView()
        override fun createDivider() = delegate.createDivider()
        override fun createText(tagName: String, classPrefix: String) = delegate.createText(tagName, classPrefix)
        override fun createMarkdownView() = delegate.createMarkdownView()
        override fun createImage() = delegate.createImage()
        override fun createTable() = delegate.createTable()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> =
            object : ButtonNativeImpl<Any?>, AlienImpl(classPrefix) {
                private var txt = ""
                private var icn: String? = null
                override fun setText(text: String) { txt = text }
                override fun getText(): String = txt
                override fun setIcon(icon: String?) { icn = icon }
                override fun getIcon(): String? = icn
            }
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
            delegate.createStyledButton(classPrefix)
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

    private lateinit var hostFactory: JvmTestHostNativeFactory

    @BeforeTest
    fun setUp() {
        AlienExtension.clearAlienExtensionImplsForTests()
        hostFactory = JvmTestHostNativeFactory()
        GenericUI.initialize(hostFactory, "JvmMediaAlienTestApp")
    }

    @AfterTest
    fun tearDown() {
        MediaAlienExtension.unregister()
        AlienExtension.clearAlienExtensionImplsForTests()
    }

    private fun resolveTestMp4File(): File {
        var dir: File? = File(".").canonicalFile
        for (depth in 0 until 6) {
            val direct = File(dir, "test.mp4")
            if (direct.isFile) return direct
            val nested = File(dir, "extensions/media/media/test.mp4")
            if (nested.isFile) return nested
            dir = dir?.parentFile ?: break
        }
        error("Could not locate extensions/media/media/test.mp4 from ${File(".").canonicalPath}")
    }

    @Test
    fun testRealTestMp4PlaybackAndFrameRenderingInAlienOffscreenSessionOnJvm() {
        MediaAlienExtension.register()
        val session = AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = 360,
            initialHeight = 220,
            offscreenOnly = true
        )
        GenericUI.initialize(session.alienFactory, "JvmMediaAlienMp4")

        val testMp4 = resolveTestMp4File()
        val player = object : VideoPlayer() {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        player.ensureNativeImpl()
        val impl = assertIs<VideoPlayerAlienImpl>(player.nativeImpl)
        session.rootNode.addChild(impl.node)

        player.setSource(testMp4.absolutePath)
        assertTrue(player.durationSeconds >= 29.0, "Expected ~30s test.mp4 duration, got ${player.durationSeconds}")
        assertEquals(320, player.naturalWidth)
        assertEquals(180, player.naturalHeight)
        assertTrue(player.getAudioTracks().isNotEmpty(), "Expected MP4 audio track parsed from test.mp4")

        val initialBuf = session.renderFrameToBuffer(16.67f)
        assertTrue(session.underlaySlots.isEmpty())
        assertTrue(impl.videoFrameVersion >= 1L)

        var hasOpaqueNonZero = false
        for (y in 20 until 170 step 12) {
            for (x in 20 until 300 step 12) {
                val px = initialBuf.getPixel(x, y)
                if (px.a == 255 && (px.r > 15 || px.g > 15 || px.b > 15)) {
                    hasOpaqueNonZero = true
                    break
                }
            }
            if (hasOpaqueNonZero) break
        }
        assertTrue(hasOpaqueNonZero, "Expected rendered non-zero opaque video frame pixels from test.mp4")

        player.play()
        assertTrue(player.isPlaying)
        for (i in 0 until 6) {
            session.renderFrameToBuffer(40f)
        }
        assertTrue(player.currentTimeSeconds > 0.0)
        player.seekTo(12.5)
        assertTrue(player.currentTimeSeconds in 12.4..12.6)
        player.volume = 0.4f
        player.isMuted = true
        assertEquals(0.4f, player.volume)
        assertTrue(player.isMuted)
        player.pause()
        assertFalse(player.isPlaying)
        player.dispose()
    }
}
