package com.nexustools.genericui.ext.mediacontrols.ios

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosMediaControlsTest {

    @AfterTest
    fun tearDown() {
        MediaControlsRegistry.resetToPlatformDefault()
    }

    @Test
    fun testIosAndIpadOsNowPlayingAndRemoteCommands() {
        assertTrue(IosMediaControlsProvider.supportsTarget(BackendTarget.IOS))
        assertTrue(IosMediaControlsProvider.supportsTarget(BackendTarget.IPadOS))
        assertFalse(IosMediaControlsProvider.supportsTarget(BackendTarget.Android))

        val session = IosMediaControlsProvider.createSession()
        val impl = session.nativeImpl as IosMediaControlsImpl
        assertTrue(session.capabilities.supportsSystemTransportControls)
        assertTrue(session.capabilities.supportsKeyboardMediaKeyHook)

        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }
        session.activate()

        session.updateMetadata(
            MediaControlMetadata(
                title = "iPadOS Track",
                artist = "Composer",
                album = "Studio",
                durationMs = 120_000L
            )
        )
        session.updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 15_000L)
        assertTrue(impl.nowPlayingInfoDictionary.isNotEmpty())

        assertTrue(impl.handleRemoteControlEventSubtype(IosMediaControlsImpl.UI_EVENT_SUBTYPE_REMOTE_TOGGLE_PLAY_PAUSE))
        assertTrue(impl.handleHardwareMediaKeyInput("MediaTrackNext"))
        assertTrue(impl.handleRemoteCommand(MediaControlAction.SEEK_TO, 45_000L))
        session.updateButtonConfig(MediaControlButtonConfig(stopEnabled = false))
        assertFalse(impl.handleHardwareMediaKeyInput("UnknownKey"))

        assertEquals(3, events.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, events[0].action)
        assertEquals(MediaControlEventSource.SYSTEM_TRANSPORT, events[0].source)
        assertEquals(MediaControlAction.NEXT, events[1].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, events[1].source)
        assertEquals(MediaControlAction.SEEK_TO, events[2].action)
        assertEquals(45_000L, events[2].seekPositionMs)

        session.dispose()
    }
}
