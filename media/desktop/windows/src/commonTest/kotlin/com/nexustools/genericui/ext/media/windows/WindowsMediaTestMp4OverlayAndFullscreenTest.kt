@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.windows

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.desktop.core.DesktopStageOverlaySpec
import com.nexustools.genericui.ext.media.desktop.DesktopVlcMediaExtensionProvider
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.windows.Win32NativeWindowHost
import com.nexustools.genericui.windows.WindowsApplicationNativeImpl
import com.nexustools.genericui.windows.WindowsNativeFactory
import platform.posix.fclose
import platform.posix.fopen
import platform.windows.InvalidateRect
import platform.windows.UpdateWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end Win32 + `:extensions:media` tests using the real `extensions/media/test.mp4` video asset.
 *
 * Verifies live `libvlc` decoding inside a Win32 `Window` with an attached stage HUD overlay,
 * atomic overlay + video frame composition, and fullscreen enter/exit while `test.mp4` is playing.
 */
class WindowsMediaTestMp4OverlayAndFullscreenTest {

    private fun resolveMediaExtensionTestMp4Path(): String {
        val candidates = listOf(
            "D:\\IdeaProjects\\GenericUI\\extensions\\media\\test.mp4",
            "..\\..\\test.mp4",
            "..\\..\\..\\test.mp4",
            "extensions\\media\\test.mp4",
            "test.mp4"
        )
        for (path in candidates) {
            val fp = fopen(path, "rb")
            if (fp != null) {
                fclose(fp)
                return path
            }
        }
        return "D:\\IdeaProjects\\GenericUI\\extensions\\media\\test.mp4"
    }

    @Test
    fun testLiveTestMp4PlaybackWithOverlayAndFullscreenExitInWin32Window() {
        val prevFactory = GenericUI.activeFactory
        val prevInteractive = WindowsApplicationNativeImpl.interactiveWin32GuiEnabled
        val prevVisualTest = Win32NativeWindowHost.visualTestMode

        WindowsApplicationNativeImpl.interactiveWin32GuiEnabled = true
        Win32NativeWindowHost.visualTestMode = true
        val factory = WindowsNativeFactory()
        GenericUI.initialize(factory)
        DesktopVlcMediaExtensionProvider.install("Windows")

        val videoPlayer = VideoPlayer()
        val sidebarButton = Button("Library Sidebar")
        val titleLabel = Text("Test MP4 Episode")
        val timeReadout = Text("0:00 / 0:10").apply {
            setAttribute(DesktopStageOverlaySpec.ATTR_OVERLAY_ROLE, DesktopStageOverlaySpec.ROLE_TIME_READOUT)
        }
        val seekBar = SeekBar(value = 0.0, min = 0.0, max = 10.0)
        val seekWrap = Container("div").apply {
            setAttribute(DesktopStageOverlaySpec.ATTR_OVERLAY_ROLE, DesktopStageOverlaySpec.ROLE_SEEK_CONTAINER)
            addChild(seekBar)
        }
        val controlsRow = HStack().apply {
            setAttribute(DesktopStageOverlaySpec.ATTR_OVERLAY_ROLE, DesktopStageOverlaySpec.ROLE_CONTROLS_ROW)
            addChild(Button("Pause"))
            addChild(timeReadout)
        }
        val topBar = HStack().apply {
            setAttribute(DesktopStageOverlaySpec.ATTR_OVERLAY_ROLE, DesktopStageOverlaySpec.ROLE_TOP_BAR)
            addChild(titleLabel)
        }
        val bottomBar = VStack().apply {
            setAttribute(DesktopStageOverlaySpec.ATTR_OVERLAY_ROLE, DesktopStageOverlaySpec.ROLE_BOTTOM_BAR)
            addChild(seekWrap)
            addChild(controlsRow)
        }
        val controlsLayer = VStack().apply {
            setAttribute(DesktopStageOverlaySpec.ATTR_OVERLAY_ROLE, DesktopStageOverlaySpec.ROLE_CONTROLS_LAYER)
            addChild(topBar)
            addChild(bottomBar)
        }
        val overlayRoot = Container("div").apply {
            setAttribute("data-overlay-root", "true")
            setAttribute("hudVisible", "true")
            addChild(controlsLayer)
        }
        val videoStageStack = LayeredContainer().apply {
            addFillLayer(videoPlayer, zIndex = 0)
            addFillLayer(overlayRoot, zIndex = 10, passThroughPointerEvents = false)
        }
        val rootLayout = HStack().apply {
            addChild(sidebarButton)
            addChild(videoStageStack)
        }

        val app = Application(initialContent = rootLayout, title = "Media Extension test.mp4 Test")
        val window = app.mainWindow
        try {
            window.setWindowSize(Size(1280, 800))
            window.show(factory)

            val overlayNativeWidget = overlayRoot.ensureNativeImpl()!!.nativeWidget as DesktopNativeWidget
            DesktopStageOverlaySpec().attachTo(overlayNativeWidget)

            val hwnd = Win32NativeWindowHost.activeHwnd
            assertNotNull(hwnd, "Expected active Win32 HWND for test.mp4 playback window")
            Win32NativeWindowHost.pumpVisualWindowEvents(32L)

            val testMp4Path = resolveMediaExtensionTestMp4Path()
            val fp = fopen(testMp4Path, "rb")
            assertNotNull(fp, "Expected test.mp4 at $testMp4Path")
            fclose(fp)

            videoPlayer.isMuted = true
            videoPlayer.setSource(testMp4Path)

            // Verify paused first-frame preload before play() is called
            var prerollAttempts = 0
            while (prerollAttempts < 80 && !videoPlayer.isMediaReady) {
                Win32NativeWindowHost.pumpVisualWindowEvents(25L)
                InvalidateRect(hwnd, null, 0)
                UpdateWindow(hwnd)
                prerollAttempts++
            }
            assertFalse(videoPlayer.isPlaying, "Expected videoPlayer.isPlaying to remain false during paused first-frame preload")
            assertTrue(videoPlayer.isMediaReady, "Expected videoPlayer.isMediaReady to become true from paused first-frame preload")

            videoPlayer.play()

            var attempts = 0
            while (attempts < 80 && Win32NativeWindowHost.lastRenderedVideoSwapCount <= 0L) {
                Win32NativeWindowHost.pumpVisualWindowEvents(25L)
                InvalidateRect(hwnd, null, 0)
                UpdateWindow(hwnd)
                attempts++
            }

            assertTrue(
                Win32NativeWindowHost.lastRenderedVideoSwapCount > 0L,
                "Expected VideoPlayer to decode and render live frames from $testMp4Path in Win32 window (swapCount=${Win32NativeWindowHost.lastRenderedVideoSwapCount})"
            )

            // Enter fullscreen on videoStageStack while test.mp4 is playing
            videoStageStack.requestFullscreen()
            Win32NativeWindowHost.pumpVisualWindowEvents(32L)
            InvalidateRect(hwnd, null, 1)
            UpdateWindow(hwnd)
            assertTrue(videoStageStack.isFullscreen(), "Expected videoStageStack to be in fullscreen")
            assertTrue(window.isFullscreen(), "Expected hosting Window to report fullscreen")

            // Exit fullscreen while test.mp4 is playing and verify full windowed layout is repainted
            videoStageStack.exitFullscreen()
            Win32NativeWindowHost.pumpVisualWindowEvents(32L)
            assertFalse(videoStageStack.isFullscreen(), "Expected videoStageStack to exit fullscreen")
            assertFalse(window.isFullscreen(), "Expected hosting Window to exit fullscreen")

            Win32NativeWindowHost.resetFrameMetrics()
            InvalidateRect(hwnd, null, 1)
            UpdateWindow(hwnd)
            val metricsAfterExit = Win32NativeWindowHost.getFrameMetricsSnapshot()
            assertEquals(
                0,
                metricsAfterExit.videoStageOnlyPaints,
                "Full window repaint after exiting fullscreen during test.mp4 playback must not be trapped in videoStageOnly fast path (metrics=$metricsAfterExit)"
            )
            assertEquals(
                1,
                metricsAfterExit.fullWindowPaints,
                "Full window repaint after exiting fullscreen during test.mp4 playback must execute a fullWindowPaint (metrics=$metricsAfterExit)"
            )
        } finally {
            videoPlayer.pause()
            window.close()
            Win32NativeWindowHost.closeAllVisualWindows()
            Win32NativeWindowHost.visualTestMode = prevVisualTest
            WindowsApplicationNativeImpl.interactiveWin32GuiEnabled = prevInteractive
            if (prevFactory != null) {
                GenericUI.initialize(prevFactory)
            }
        }
    }
}
