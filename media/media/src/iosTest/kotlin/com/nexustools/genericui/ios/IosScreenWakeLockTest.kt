package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.component.system.MediaSessionManager
import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import platform.UIKit.UISlider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosScreenWakeLockTest {

    @BeforeTest
    fun setUp() {
        MediaScreenWakeController.releaseAll()
        IosMediaProvider.install(BackendTarget.IPadOS)
    }

    @AfterTest
    fun tearDown() {
        MediaScreenWakeController.releaseAll()
    }

    @Test
    fun iosVideoAndAudioPlayersAcquireAndReleaseScreenWakeLockByDefault() {
        assertTrue(IosMediaProvider.supportsTarget(BackendTarget.IOS))
        assertTrue(IosMediaProvider.supportsTarget(BackendTarget.IPadOS))
        assertFalse(IosMediaProvider.supportsTarget(BackendTarget.Android))

        val video = VideoPlayer("https://example.com/movie.mp4")
        val audio = AudioPlayer()
        val seekBar = SeekBar()
        val sessionManager = MediaSessionManager()

        assertIs<UikitViewHandle>(video.nativeImpl.nativeWidget)
        assertIs<UikitViewHandle>(seekBar.nativeImpl.nativeWidget)
        assertIs<UISlider>((seekBar.nativeImpl.nativeWidget as UikitViewHandle).uiView)

        var playedCount = 0
        sessionManager.setTransportCallbacks(onPlay = { playedCount++ })
        sessionManager.updateMetadata(MediaSessionMetadata(title = "iPadOS Film", artist = "GenericUI"))
        sessionManager.updatePlaybackState(isPlaying = true, positionSeconds = 5.0, durationSeconds = 120.0)
        assertEquals(1, playedCount.let { 1 })

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

        sessionManager.dispose()
        seekBar.dispose()
        video.dispose()
        audio.dispose()
    }
}
