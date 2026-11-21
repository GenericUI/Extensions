package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaScreenWakeLockTest {

    private class RecordingWakeLockNativeImpl : ScreenWakeLockNativeImpl {
        override var isDisposed: Boolean = false
            private set

        private var held: Boolean = false
        var transitions: MutableList<Pair<Boolean, String>> = mutableListOf()

        override val isWakeLockHeld: Boolean
            get() = !isDisposed && held

        override fun setWakeLockEnabled(enabled: Boolean, reason: String) {
            if (isDisposed && enabled) return
            held = !isDisposed && enabled
            transitions.add(held to reason)
        }

        override fun dispose() {
            if (!isDisposed) {
                isDisposed = true
                held = false
            }
        }
    }

    private class ControllableAudioNativeImpl : AudioPlayerNativeImpl {
        override var isDisposed: Boolean = false
            private set
        private var playingState: Boolean = false
        private var keepScreenOnFlag: Boolean = true
        private var wakeToken: Long = MediaScreenWakeController.allocateToken()
        private var onEndedCallback: (() -> Unit)? = null
        private var onErrorCallback: ((String) -> Unit)? = null
        private var onPlayingChanged: ((Boolean) -> Unit)? = null

        private fun syncWake() {
            MediaScreenWakeController.setTokenActive(wakeToken, !isDisposed && keepScreenOnFlag && playingState)
        }

        override fun setSource(url: String, seekOffsetSeconds: Double) {}
        override fun play() {
            playingState = true
            syncWake()
            onPlayingChanged?.invoke(true)
        }
        override fun pause() {
            playingState = false
            syncWake()
            onPlayingChanged?.invoke(false)
        }
        override fun stop() {
            playingState = false
            syncWake()
            onPlayingChanged?.invoke(false)
        }
        override fun seekTo(seconds: Double) {}
        override fun getCurrentTimeSeconds(): Double = 0.0
        override fun getDurationSeconds(): Double = 60.0
        override fun isPlaying(): Boolean = playingState
        override fun setVolume(volume: Float) {}
        override fun getVolume(): Float = 1f
        override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {}
        override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) {}
        override fun setOnEnded(listener: (() -> Unit)?) { onEndedCallback = listener }
        override fun setOnError(listener: ((String) -> Unit)?) { onErrorCallback = listener }
        override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { onPlayingChanged = listener }
        override fun setKeepScreenOn(keepScreenOn: Boolean) {
            keepScreenOnFlag = keepScreenOn
            syncWake()
        }
        override fun isKeepScreenOn(): Boolean = keepScreenOnFlag
        override fun bindScreenWakeToken(token: Long) {
            if (token > 0L && token != wakeToken) {
                val prev = wakeToken
                wakeToken = token
                MediaScreenWakeController.releaseToken(prev)
                syncWake()
            }
        }

        fun simulateEnded() {
            playingState = false
            syncWake()
            onPlayingChanged?.invoke(false)
            onEndedCallback?.invoke()
        }

        fun simulateError(msg: String) {
            playingState = false
            syncWake()
            onPlayingChanged?.invoke(false)
            onErrorCallback?.invoke(msg)
        }

        override fun dispose() {
            isDisposed = true
            playingState = false
            MediaScreenWakeController.releaseToken(wakeToken)
        }
    }

    private lateinit var activeWakeLock: RecordingWakeLockNativeImpl

    @BeforeTest
    fun setUp() {
        MediaScreenWakeController.releaseAll()
        MediaExtension.resetProvidersForTest()
        activeWakeLock = RecordingWakeLockNativeImpl()
        MediaExtension.registerProviders(
            screenWakeLock = { activeWakeLock }
        )
    }

    @AfterTest
    fun tearDown() {
        MediaScreenWakeController.releaseAll()
        MediaExtension.resetProvidersForTest()
    }

    @Test
    fun testKeepScreenOnEnabledByDefaultAndTogglesWithVideoAndAudioPlayback() {
        val video = VideoPlayer("https://example.com/episode.mp4")
        val audio = AudioPlayer("https://example.com/music.mp3")

        assertTrue(video.keepScreenOn, "VideoPlayer.keepScreenOn must default to true")
        assertTrue(audio.keepScreenOn, "AudioPlayer.keepScreenOn must default to true")
        assertEquals("true", video.getAttribute("data-keep-screen-on"))

        // Idle loaded players must NOT hold screen wake lock
        assertEquals(0, MediaScreenWakeController.activePlayerCount)
        assertFalse(MediaScreenWakeController.isKeepScreenOnActive)
        assertFalse(MediaScreenWakeController.isNativeWakeLockHeld)
        assertFalse(activeWakeLock.isWakeLockHeld)

        // Start VideoPlayer -> wake lock acquired
        video.play()
        assertEquals(1, MediaScreenWakeController.activePlayerCount)
        assertTrue(MediaScreenWakeController.isKeepScreenOnActive)
        assertTrue(MediaScreenWakeController.isNativeWakeLockHeld)
        assertTrue(activeWakeLock.isWakeLockHeld)

        // Start AudioPlayer concurrently -> ref count is 2, wake lock remains held
        audio.play()
        assertEquals(2, MediaScreenWakeController.activePlayerCount)
        assertTrue(activeWakeLock.isWakeLockHeld)

        // Pause VideoPlayer while AudioPlayer still plays -> wake lock MUST stay held
        video.pause()
        assertEquals(1, MediaScreenWakeController.activePlayerCount)
        assertTrue(activeWakeLock.isWakeLockHeld)

        // Pause AudioPlayer -> all active players paused -> wake lock released immediately
        audio.pause()
        assertEquals(0, MediaScreenWakeController.activePlayerCount)
        assertFalse(MediaScreenWakeController.isKeepScreenOnActive)
        assertFalse(activeWakeLock.isWakeLockHeld)

        video.dispose()
        audio.dispose()
    }

    @Test
    fun testDisablingKeepScreenOnMidPlaybackReleasesAndReEnablingReacquiresWakeLock() {
        val video = VideoPlayer("https://example.com/live.mp4")
        video.play()
        assertTrue(video.isPlaying)
        assertTrue(activeWakeLock.isWakeLockHeld)

        // Turn off keepScreenOn while still playing -> wake lock releases immediately, video stays playing
        video.keepScreenOn = false
        assertEquals("false", video.getAttribute("data-keep-screen-on"))
        assertTrue(video.isPlaying)
        assertEquals(0, MediaScreenWakeController.activePlayerCount)
        assertFalse(activeWakeLock.isWakeLockHeld)

        // Turn keepScreenOn back on while still playing -> wake lock re-acquires immediately
        video.keepScreenOn = true
        assertEquals("true", video.getAttribute("data-keep-screen-on"))
        assertEquals(1, MediaScreenWakeController.activePlayerCount)
        assertTrue(activeWakeLock.isWakeLockHeld)

        // Dispose while playing -> releases immediately
        video.dispose()
        assertEquals(0, MediaScreenWakeController.activePlayerCount)
        assertFalse(activeWakeLock.isWakeLockHeld)
    }

    @Test
    fun testSharedTokenBindingPreventsDoubleCountingAndReleasesOnNativeEndOrError() {
        var latestAudioNative: ControllableAudioNativeImpl? = null
        MediaExtension.registerProviders(
            audioPlayer = {
                ControllableAudioNativeImpl().also { latestAudioNative = it }
            },
            screenWakeLock = { activeWakeLock }
        )
        try {
            val audio = AudioPlayer("https://example.com/track.flac")
            audio.play()
            // Must count as 1 active player (not 2!) thanks to bindScreenWakeToken
            assertEquals(1, MediaScreenWakeController.activePlayerCount)
            assertTrue(activeWakeLock.isWakeLockHeld)

            // Natural end from native player releases wake lock immediately
            latestAudioNative?.simulateEnded()
            assertFalse(audio.isPlaying)
            assertEquals(0, MediaScreenWakeController.activePlayerCount)
            assertFalse(activeWakeLock.isWakeLockHeld)

            // Resume and simulate native playback error -> releases wake lock immediately
            audio.play()
            assertEquals(1, MediaScreenWakeController.activePlayerCount)
            assertTrue(activeWakeLock.isWakeLockHeld)

            latestAudioNative?.simulateError("network lost")
            assertFalse(audio.isPlaying)
            assertEquals(0, MediaScreenWakeController.activePlayerCount)
            assertFalse(activeWakeLock.isWakeLockHeld)

            audio.dispose()
        } finally {
            MediaScreenWakeController.releaseAll()
            MediaExtension.resetProvidersForTest()
        }
    }
}
