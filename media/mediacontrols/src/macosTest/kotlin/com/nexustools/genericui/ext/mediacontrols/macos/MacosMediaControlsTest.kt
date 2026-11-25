@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.macos

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaControlsSession
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.macos.MacosApplicationNativeImpl
import platform.Foundation.NSNumber
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.posix.usleep
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacosMediaControlsTest {

    @AfterTest
    fun tearDown() {
        MacosApplicationNativeImpl.systemDefinedEventHook = null
        MediaControlsRegistry.resetToPlatformDefault()
    }

    private fun encodeSystemDefinedData1(
        keyCode: Int,
        keyState: Int,
        isRepeat: Boolean = false
    ): Int = ((keyCode and 0xFFFF) shl 16) or ((keyState and 0xFF) shl 8) or (if (isRepeat) 1 else 0)

    private fun extractDouble(value: Any?): Double? = when (value) {
        is Double -> value
        is Number -> value.toDouble()
        is NSNumber -> value.doubleValue
        else -> null
    }

    @Test
    fun testProviderRegistrationAndNowPlayingMetadataAndLoadingState() {
        MacosMediaControlsProvider.register()
        val session = MediaControlsSession()
        assertTrue(session.nativeImpl is MacosMediaControlsImpl)
        val impl = session.nativeImpl as MacosMediaControlsImpl

        assertTrue(session.capabilities.supportsSystemTransportControls)
        assertTrue(session.capabilities.supportsKeyboardMediaKeyHook)
        assertFalse(session.capabilities.supportsTaskbarThumbnailButtons)

        session.activate()
        session.updateMetadata(
            MediaControlMetadata(
                title = "   ",
                artist = "",
                album = "   ",
                durationMs = 90_000L
            )
        )
        session.updatePlaybackState(MediaPlaybackStatus.LOADING, positionMs = 5_000L, durationMs = 90_000L)

        // Blank title gets a non-empty active fallback; blank artist/album are omitted.
        assertEquals("Media Playing", impl.nowPlayingInfoDictionary["MPMediaItemPropertyTitle"])
        assertFalse(impl.nowPlayingInfoDictionary.containsKey("MPMediaItemPropertyArtist"))
        assertFalse(impl.nowPlayingInfoDictionary.containsKey("MPMediaItemPropertyAlbumTitle"))
        assertEquals(1.0, impl.nowPlayingInfoDictionary["MPNowPlayingInfoPropertyDefaultPlaybackRate"])
        assertEquals(0.0, impl.nowPlayingInfoDictionary["MPNowPlayingInfoPropertyPlaybackRate"])
        assertEquals("PLAYING", impl.nowPlayingInfoDictionary["MPNowPlayingPlaybackState"])

        val infoCenter = MPNowPlayingInfoCenter.defaultCenter()
        assertEquals(MPNowPlayingPlaybackStatePlaying, infoCenter.playbackState)
        val centerInfo = infoCenter.nowPlayingInfo
        assertNotNull(centerInfo)
        assertEquals("Media Playing", centerInfo[MPMediaItemPropertyTitle])
        assertNull(centerInfo[MPMediaItemPropertyArtist])
        assertNull(centerInfo[MPMediaItemPropertyAlbumTitle])
        assertEquals(1.0, extractDouble(centerInfo[MPNowPlayingInfoPropertyDefaultPlaybackRate]))
        assertEquals(0.0, extractDouble(centerInfo[MPNowPlayingInfoPropertyPlaybackRate]))

        // Transitioning to STOPPED drops playbackState to Stopped.
        session.updatePlaybackState(MediaPlaybackStatus.STOPPED, positionMs = 0L, durationMs = 90_000L)
        assertEquals(MPNowPlayingPlaybackStateStopped, infoCenter.playbackState)

        session.dispose()
    }

    @Test
    fun testSystemDefinedEventDownUpRepeatAndToggle() {
        val session = MacosMediaControlsProvider.createSession()
        val impl = session.nativeImpl as MacosMediaControlsImpl
        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }
        session.activate()

        val downData1 = encodeSystemDefinedData1(
            keyCode = MacosMediaControlsImpl.NX_KEYTYPE_PLAY,
            keyState = MacosMediaControlsImpl.NX_KEYSTATE_DOWN,
            isRepeat = false
        )
        val repeatData1 = encodeSystemDefinedData1(
            keyCode = MacosMediaControlsImpl.NX_KEYTYPE_PLAY,
            keyState = MacosMediaControlsImpl.NX_KEYSTATE_DOWN,
            isRepeat = true
        )
        val upData1 = encodeSystemDefinedData1(
            keyCode = MacosMediaControlsImpl.NX_KEYTYPE_PLAY,
            keyState = MacosMediaControlsImpl.NX_KEYSTATE_UP,
            isRepeat = false
        )

        assertTrue(impl.handleSystemDefinedEvent(MacosMediaControlsImpl.NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS, downData1))
        assertFalse(impl.handleSystemDefinedEvent(MacosMediaControlsImpl.NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS, repeatData1))
        assertFalse(impl.handleSystemDefinedEvent(MacosMediaControlsImpl.NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS, upData1))
        assertFalse(impl.handleSystemDefinedEvent(0, downData1))

        assertEquals(1, events.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, events[0].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, events[0].source)

        // Respects keyboardMediaKeysEnabled = false
        session.keyboardMediaKeysEnabled = false
        assertFalse(impl.handleSystemDefinedEvent(MacosMediaControlsImpl.NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS, downData1))
        assertEquals(1, events.size)

        // Hook integration via MacosApplicationNativeImpl.systemDefinedEventHook
        session.keyboardMediaKeysEnabled = true
        val hook = MacosApplicationNativeImpl.systemDefinedEventHook
        assertNotNull(hook)
        val nextDown = encodeSystemDefinedData1(
            keyCode = MacosMediaControlsImpl.NX_KEYTYPE_NEXT,
            keyState = MacosMediaControlsImpl.NX_KEYSTATE_DOWN
        )
        val nextUp = encodeSystemDefinedData1(
            keyCode = MacosMediaControlsImpl.NX_KEYTYPE_NEXT,
            keyState = MacosMediaControlsImpl.NX_KEYSTATE_UP
        )
        assertTrue(hook(MacosMediaControlsImpl.NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS, nextDown))
        assertTrue(hook(MacosMediaControlsImpl.NSEVENT_SUBTYPE_AUX_CONTROL_BUTTONS, nextUp))
        assertEquals(2, events.size)
        assertEquals(MediaControlAction.NEXT, events[1].action)

        session.dispose()
        assertNull(MacosApplicationNativeImpl.systemDefinedEventHook)
    }

    @Test
    fun testCrossSourceDeduplicationAndSimulateBypass() {
        val session = MacosMediaControlsProvider.createSession()
        val impl = session.nativeImpl as MacosMediaControlsImpl
        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }
        session.activate()

        // Same-source rapid presses are NOT suppressed.
        assertTrue(impl.handleNxKeyType(MacosMediaControlsImpl.NX_KEYTYPE_PLAY))
        assertTrue(impl.handleNxKeyType(MacosMediaControlsImpl.NX_KEYTYPE_PLAY))
        assertEquals(2, events.size)

        // Opposite-source duplicate within 150ms across {PLAY, PAUSE, PLAY_PAUSE} is dropped.
        assertFalse(impl.handleRemoteCommand(MediaControlAction.PAUSE))
        assertFalse(impl.handleRemoteCommand(MediaControlAction.PLAY))
        assertFalse(impl.handleRemoteCommand(MediaControlAction.PLAY_PAUSE))
        assertEquals(2, events.size)

        // simulateMediaKeyEvent is never suppressed.
        session.simulateMediaKey(MediaControlAction.PAUSE, MediaControlEventSource.SYSTEM_TRANSPORT)
        assertEquals(3, events.size)
        assertEquals(MediaControlAction.PAUSE, events[2].action)

        // Unrelated action from opposite source is not suppressed.
        assertTrue(impl.handleRemoteCommand(MediaControlAction.NEXT))
        assertEquals(4, events.size)
        // Duplicate NEXT from keyboard within 150ms IS suppressed.
        assertFalse(impl.handleNxKeyType(MacosMediaControlsImpl.NX_KEYTYPE_NEXT))
        assertEquals(4, events.size)

        // After 150ms window elapses, opposite-source PLAY is accepted again.
        usleep(170_000u)
        assertTrue(impl.handleRemoteCommand(MediaControlAction.PLAY))
        assertEquals(5, events.size)

        session.dispose()
    }

    @Test
    fun testDisposeRemovesOnlyOwnTargetsWithoutClobberingSecondActiveSession() {
        val session1 = MacosMediaControlsProvider.createSession()
        val session2 = MacosMediaControlsProvider.createSession()
        val impl2 = session2.nativeImpl as MacosMediaControlsImpl
        val events2 = mutableListOf<MediaControlEvent>()
        session2.addListener { events2.add(it) }

        session1.activate()
        session1.updateMetadata(MediaControlMetadata(title = "Track 1"))
        session1.updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 1_000L, durationMs = 30_000L)

        session2.activate()
        session2.updateMetadata(MediaControlMetadata(title = "Track 2"))
        session2.updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 5_000L, durationMs = 60_000L)

        // Disposing session1 must not disable shared remote commands or wipe session2's Now Playing state.
        session1.dispose()

        val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()
        assertTrue(commandCenter.playCommand.enabled)
        assertTrue(commandCenter.pauseCommand.enabled)
        assertTrue(commandCenter.togglePlayPauseCommand.enabled)
        assertEquals(MPNowPlayingPlaybackStatePlaying, MPNowPlayingInfoCenter.defaultCenter().playbackState)
        assertEquals("Track 2", impl2.nowPlayingInfoDictionary["MPMediaItemPropertyTitle"])
        assertTrue(impl2.handleRemoteCommand(MediaControlAction.PAUSE))
        assertEquals(1, events2.size)
        assertEquals(MediaControlAction.PAUSE, events2[0].action)

        // Disposing the last active session cleans up shared state.
        session2.dispose()
        assertFalse(commandCenter.playCommand.enabled)
        assertEquals(MPNowPlayingPlaybackStateStopped, MPNowPlayingInfoCenter.defaultCenter().playbackState)
    }
}
