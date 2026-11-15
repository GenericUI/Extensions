package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosScreenWakeLockTest {

    @BeforeTest
    fun setUp() {
        MediaScreenWakeController.releaseAll()
        IosMediaProvider.install()
    }

    @AfterTest
    fun tearDown() {
        MediaScreenWakeController.releaseAll()
    }

    @Test
    fun iosVideoAndAudioPlayersAcquireAndReleaseScreenWakeLockByDefault() {
        val video = VideoPlayer("https://example.com/movie.mp4")
        val audio = AudioPlayer()

        assertTrue(video.keepScreenOn)
        assertTrue(audio.keepScreenOn)
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)

        video.play()
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)
        assertEquals(1, MediaScreenWakeController.activePlayerCount)

        audio.play()
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)
        assertEquals(2, MediaScreenWakeController.activePlayerCount)

        video.keepScreenOn = false
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)
        assertEquals(1, MediaScreenWakeController.activePlayerCount)

        audio.pause()
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)
        assertEquals(0, MediaScreenWakeController.activePlayerCount)

        video.dispose()
        audio.dispose()
    }
}
