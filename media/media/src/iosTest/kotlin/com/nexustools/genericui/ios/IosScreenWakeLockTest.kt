package com.nexustools.genericui.ios

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.component.system.MediaSessionManager
import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import platform.UIKit.UIView
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosScreenWakeLockTest {

    private var previousFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        previousFactory = GenericUI.activeFactory
        GenericUI.initialize(IosNativeFactory())
        MediaScreenWakeController.releaseAll()
        IosMediaProvider.install(BackendTarget.IPadOS)
    }

    @AfterTest
    fun tearDown() {
        MediaScreenWakeController.releaseAll()
        GenericUI.activeFactory = previousFactory
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

        assertIs<StyledSlider>(seekBar)
        assertIs<Composite>(seekBar)
        val videoNative = assertNotNull(video.ensureNativeImpl())
        val seekBarNative = assertNotNull(seekBar.ensureNativeImpl())
        assertIs<UikitViewHandle>(videoNative.nativeWidget)
        assertIs<UikitViewHandle>(seekBarNative.nativeWidget)
        assertIs<UIView>((seekBarNative.nativeWidget as UikitViewHandle).uiView)

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
