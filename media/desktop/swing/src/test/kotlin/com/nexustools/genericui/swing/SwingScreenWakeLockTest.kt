package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.ext.media.swing.SwingMediaExtensionProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwingScreenWakeLockTest {

    @BeforeTest
    fun setUp() {
        MediaScreenWakeController.releaseAll()
        SwingMediaExtensionProvider.install()
    }

    @AfterTest
    fun tearDown() {
        MediaScreenWakeController.releaseAll()
    }

    @Test
    fun testSwingScreenWakeLockAcquiresDuringPlaybackAndReleasesWhenStoppedOrDisabled() {
        val video = VideoPlayer("https://example.com/movie.mp4")
        val audio = AudioPlayer()

        assertTrue(video.keepScreenOn)
        assertTrue(audio.keepScreenOn)
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

        video.play()
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)

        audio.play()
        video.pause()
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)

        audio.keepScreenOn = false
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

        audio.keepScreenOn = true
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)

        audio.stop()
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

        video.dispose()
        audio.dispose()
    }
}
