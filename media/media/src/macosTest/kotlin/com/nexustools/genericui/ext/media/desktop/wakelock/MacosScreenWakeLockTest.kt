@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.wakelock

import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.ext.media.desktop.DesktopVlcMediaExtensionProvider
import com.nexustools.genericui.ext.media.desktop.libvlc.DesktopLibVlcVideoPlayerImpl
import platform.CoreFoundation.CFRelease
import platform.IOKit.IOPMAssertionCopyProperties
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.usleep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MacosScreenWakeLockTest {

    private fun resolveTestMp4Path(): String? {
        val candidates = listOf(
            "test.mp4",
            "extensions/media/media/test.mp4",
            "../extensions/media/media/test.mp4",
            "../../extensions/media/media/test.mp4",
            "../../../extensions/media/media/test.mp4",
            "../test.mp4",
            "../../test.mp4",
            "../../../test.mp4"
        )
        for (path in candidates) {
            val fp = fopen(path, "rb")
            if (fp != null) {
                fclose(fp)
                return path
            }
        }
        return null
    }

    @BeforeTest
    fun setUp() {
        MediaScreenWakeController.releaseAll()
        DesktopVlcMediaExtensionProvider.install("macOS")
    }

    @AfterTest
    fun tearDown() {
        MediaScreenWakeController.releaseAll()
    }

    @Test
    fun testVideoAndAudioPlayersAcquireAndReleaseRealMacosIoKitAssertionByDefault() {
        val video = VideoPlayer()
        val audio = AudioPlayer()

        assertTrue(video.keepScreenOn)
        assertTrue(audio.keepScreenOn)
        assertEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

        // Playing VideoPlayer acquires real macOS IOKit PreventUserIdleDisplaySleep assertion
        video.play()
        val assertionId = DesktopScreenWakePlatformBridge.activeDisplayAssertionId
        assertNotEquals(0u, assertionId, "Expected non-zero macOS IOPMAssertionID while VideoPlayer is playing")
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)

        val props = IOPMAssertionCopyProperties(assertionId)
        assertNotNull(props, "Expected IOPMAssertionCopyProperties to return live IOKit assertion dictionary")
        CFRelease(props)

        // Playing AudioPlayer keeps assertion active across pause of VideoPlayer
        audio.play()
        video.pause()
        assertNotEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)

        // Disabling keepScreenOn on AudioPlayer releases IOKit assertion immediately
        audio.keepScreenOn = false
        assertEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

        // Re-enabling keepScreenOn while AudioPlayer is still playing re-acquires IOKit assertion
        audio.keepScreenOn = true
        assertNotEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)

        // Stopping AudioPlayer releases IOKit assertion cleanly
        audio.stop()
        assertEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

        video.dispose()
        audio.dispose()
    }

    @Test
    fun testPausedFirstFramePrerollDoesNotHoldScreenWakeLockUntilPlayCalled() {
        val path = resolveTestMp4Path() ?: return
        val widget = DesktopNativeWidget("macOS", "libvlc-video")
        val impl = DesktopLibVlcVideoPlayerImpl(nativeWidget = widget, platformName = "macOS")
        try {
            impl.setMuted(true)
            impl.setSource(path, 0.0, null)
            usleep(40_000u)
            impl.tickVBlank()

            // During paused first-frame preroll, wake lock MUST remain false
            assertFalse(impl.isPlaying())
            assertEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
            assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

            // Calling play() immediately acquires the IOKit assertion
            impl.play()
            assertTrue(impl.isPlaying())
            assertNotEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
            assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)

            // Calling pause() immediately releases the IOKit assertion
            impl.pause()
            assertFalse(impl.isPlaying())
            assertEquals(0u, DesktopScreenWakePlatformBridge.activeDisplayAssertionId)
            assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)
        } finally {
            impl.dispose()
        }
    }
}
