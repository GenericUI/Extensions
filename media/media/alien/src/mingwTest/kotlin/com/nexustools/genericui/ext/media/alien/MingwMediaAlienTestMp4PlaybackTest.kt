@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.alien

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioFftSize
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
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.usleep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MingwMediaAlienTestMp4PlaybackTest {

    private class MingwTestHostNativeFactory : NativeFactory {
        override val backendTarget: BackendTarget = BackendTarget.Windows
        override val backendName: String = "mingw-test-host"
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

    private lateinit var hostFactory: MingwTestHostNativeFactory

    @BeforeTest
    fun setUp() {
        AlienExtension.clearAlienExtensionImplsForTests()
        hostFactory = MingwTestHostNativeFactory()
        GenericUI.initialize(hostFactory, "MingwMediaAlienTestApp")
    }

    @AfterTest
    fun tearDown() {
        MediaAlienExtension.unregister()
        AlienExtension.clearAlienExtensionImplsForTests()
    }

    private fun resolveTestMp4Path(): String {
        val candidates = listOf(
            "test.mp4",
            "../test.mp4",
            "../../test.mp4",
            "../../../test.mp4",
            "extensions/media/media/test.mp4",
            "../extensions/media/media/test.mp4",
            "../../extensions/media/media/test.mp4",
            "../../../extensions/media/media/test.mp4"
        )
        for (candidate in candidates) {
            val fp = fopen(candidate, "rb")
            if (fp != null) {
                fclose(fp)
                return candidate
            }
        }
        return "extensions/media/media/test.mp4"
    }

    @Test
    fun testDesktopLibVlcOrFallbackOffscreenRenderingInAlienSessionOnMingw() {
        MediaAlienExtension.register()
        val session = AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = 360,
            initialHeight = 220,
            offscreenOnly = true
        )
        GenericUI.initialize(session.alienFactory, "MingwMediaAlienMp4")

        val mp4Path = resolveTestMp4Path()
        val analyzer = AudioAnalyzer(AudioAnalyzerConfig(fftSize = AudioFftSize.FFT_1024))
        val player = object : VideoPlayer() {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        player.ensureNativeImpl()
        val impl = assertIs<VideoPlayerAlienImpl>(player.nativeImpl)
        session.rootNode.addChild(impl.node)

        player.isMuted = true
        player.volume = 0.7f
        player.attachAudioAnalyzer(analyzer)
        player.setSource(mp4Path)
        player.play()

        var renderedBuf = session.renderFrameToBuffer(16.67f)
        var attempts = 0
        while (attempts < 120 && (player.videoFrameVersion <= 0L || player.durationSeconds <= 0.0 || player.naturalWidth <= 0)) {
            usleep(25_000u)
            renderedBuf = session.renderFrameToBuffer(25f)
            attempts++
        }

        assertTrue(session.underlaySlots.isEmpty(), "Alien video must render offscreen without OS HWND underlay")
        assertTrue(player.durationSeconds > 0.0, "Expected duration > 0 for $mp4Path (got ${player.durationSeconds})")
        assertTrue(player.naturalWidth > 0 && player.naturalHeight > 0, "Expected natural dims > 0, got ${player.naturalWidth}x${player.naturalHeight}")
        assertTrue(player.videoFrameVersion > 0L, "Expected captured offscreen frame version > 0")

        var hasOpaqueNonZero = false
        for (y in 20 until minOf(200, renderedBuf.height) step 12) {
            for (x in 20 until minOf(320, renderedBuf.width) step 12) {
                val px = renderedBuf.getPixel(x, y)
                if (px.a == 255 && (px.r > 12 || px.g > 12 || px.b > 12)) {
                    hasOpaqueNonZero = true
                    break
                }
            }
            if (hasOpaqueNonZero) break
        }
        assertTrue(hasOpaqueNonZero, "Expected non-zero opaque RGBA pixels rendered in Alien offscreenBuffer")

        player.seekTo(5.0)
        var seekAttempts = 0
        while (seekAttempts < 40 && player.currentTimeSeconds < 4.5) {
            usleep(25_000u)
            session.renderFrameToBuffer(25f)
            seekAttempts++
        }
        assertTrue(player.currentTimeSeconds >= 4.5, "Expected seekTo(5.0), got ${player.currentTimeSeconds}")

        player.loop = true
        assertTrue(player.loop)
        player.pause()
        assertFalse(player.isPlaying)
        assertEquals(0.7f, player.volume)

        analyzer.dispose()
        player.dispose()
    }
}
