package com.nexustools.genericui.ext.pip.swing

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PiPCompatible
import com.nexustools.genericui.ext.pip.PipAction
import com.nexustools.genericui.ext.pip.PipActionIcon
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.ext.pip.PlatformPipBootstrap
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SwingFloatingPipBackendTest {

    @BeforeTest
    fun setUp() {
        PipRegistry.resetForTest()
        FragmentPipRegistry.clearForTest()
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        UiTimerScheduler.clearAllTimers()
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
    }

    @Test
    fun bootstrapRegistersSwingBackendAndDetectsVideoAndAnnotatedComponents() {
        assertFalse(PipRegistry.hasRegisteredFactory())

        val caps = PipRegistry.capabilities()
        assertTrue(PipRegistry.hasRegisteredFactory())
        assertTrue(caps.isSupported)
        assertTrue(PipContentType.VIDEO in caps.supportedContentTypes)
        assertTrue(PipContentType.CANVAS in caps.supportedContentTypes)
        assertTrue(PipContentType.ANY_COMPONENT in caps.supportedContentTypes)
        assertTrue(caps.supportsCustomControlOverlay)
        assertTrue(caps.supportsAutoEnterOnMinimize)

        val videoPlayer = VideoPlayer()
        val annotated = CustomPipSurfaceComponent()
        val plain = Container()

        assertTrue(PlatformPipBootstrap.hasJvmPipCompatibleAnnotation(annotated))
        assertFalse(PlatformPipBootstrap.hasJvmPipCompatibleAnnotation(plain))
        assertTrue(PipRegistry.supportsComponent(videoPlayer))
        assertTrue(PipRegistry.supportsComponent(annotated))
        assertTrue(PipRegistry.supportsComponent(plain))
    }

    @Test
    fun enterExitRestoresReparentedVideoPlayerAndHandlesMinimizeRestoreAndClosePause() {
        val backend = SwingFloatingPipBackend().apply {
            headlessImmediateTransitions = true
        }
        PipRegistry.registerBackendFactory { backend }

        val ownerWindow = Window("Main Player", Size(1280, 720)).apply {
            windowBounds = Rect(40, 40, 1280, 720)
        }
        val fragment = Fragment()
        val stack = VStack(gapPx = 8)
        val header = Button("Header")
        val player = VideoPlayer().apply {
            bounds = Rect(0, 40, 800, 450)
        }
        val footer = Button("Footer")
        stack.addChild(header)
        stack.addChild(player)
        stack.addChild(footer)
        fragment.addChild(stack)
        ownerWindow.setContent(fragment)

        var playing = true
        var title = "Sample Stream"
        var pauseRequestedCount = 0
        val modeCallbacks = mutableListOf<Boolean>()

        val helper = FragmentPipHelper(fragment).apply {
            pipTarget = player
            isActivelyPlayingProvider = { playing }
            configProvider = {
                PipSessionConfig(
                    title = title,
                    aspectRatioNumerator = 16,
                    aspectRatioDenominator = 9,
                    autoEnterOnMinimize = true,
                    pauseOnCloseButton = true,
                    preferredWidthPx = 420,
                    preferredHeightPx = 236
                )
            }
            actionsProvider = {
                listOf(
                    PipAction(
                        id = "play_pause",
                        label = if (playing) "Pause" else "Play",
                        icon = if (playing) PipActionIcon.PAUSE else PipActionIcon.PLAY
                    ) {
                        playing = !playing
                    }
                )
            }
            onPauseRequested = {
                playing = false
                pauseRequestedCount++
            }
            onPipModeChanged = { inPip ->
                modeCallbacks.add(inPip)
            }
        }

        try {
            helper.bindWindow(ownerWindow)
            assertFalse(helper.isInPipMode)
            assertNull(backend.activePipWindow)

            // Minimize while actively playing -> auto-enters PiP.
            ownerWindow.minimize()
            assertTrue(helper.isInPipMode)
            assertFalse(helper.isEnteringPip)
            assertNotNull(backend.activePipWindow)
            assertTrue(backend.activePipWindow?.isUndecorated == true)
            assertTrue(backend.activePipWindow?.isAlwaysOnTop == true)
            assertFalse(stack.children.any { it === player })
            assertEquals("true", stack.children[1].getAttribute("data-pip-placeholder"))

            // notifyStateChanged updates PiP title and actions live.
            title = "Updated Episode"
            helper.notifyStateChanged()
            assertEquals("Updated Episode", backend.activePipWindow?.getTitle())
            assertEquals("Updated Episode", backend.activeControlOverlay?.titleText)
            assertEquals(1, backend.activeControlOverlay?.actionButtons?.size)

            // Restore main window -> auto-exits PiP back into exact index 1.
            ownerWindow.restore()
            assertFalse(helper.isInPipMode)
            assertNull(backend.activePipWindow)
            assertSame(stack, player.parent)
            assertSame(header, stack.children[0])
            assertSame(player, stack.children[1])
            assertSame(footer, stack.children[2])

            // Manual enter + overlay close button pauses playback and exits cleanly.
            playing = true
            assertTrue(helper.enterPip())
            assertTrue(helper.isInPipMode)

            val overlay = assertNotNull(backend.activeControlOverlay)
            overlay.closeButton.onClick?.invoke()

            assertEquals(1, pauseRequestedCount)
            assertFalse(playing)
            assertFalse(helper.isInPipMode)
            assertNull(backend.activePipWindow)
            assertSame(stack, player.parent)
            assertSame(player, stack.children[1])

            // Manual enter + overlay restore button focuses owner and restores component cleanly.
            assertTrue(helper.enterPip())
            assertNotNull(backend.activeControlOverlay).restoreButton.onClick?.invoke()
            assertFalse(helper.isInPipMode)
            assertSame(stack, player.parent)
            assertEquals(listOf(true, false, true, false, true, false), modeCallbacks)
            assertEquals(WindowState.NORMAL, ownerWindow.getWindowState())
        } finally {
            helper.dispose()
            ownerWindow.close()
        }
    }

    @PiPCompatible
    private class CustomPipSurfaceComponent : Container("custom-pip")
}
