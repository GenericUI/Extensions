package com.nexustools.genericui.ext.mediacontrols.windows

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsMediaControlsTest {

    @Test
    fun testWindowsCapabilitiesAndMediaKeyHandling() {
        val session = WindowsMediaControlsProvider.createSession()
        val impl = session.nativeImpl as WindowsMediaControlsImpl
        assertTrue(session.capabilities.supportsSystemTransportControls)
        assertTrue(session.capabilities.supportsTaskbarThumbnailButtons)
        assertTrue(session.capabilities.supportsKeyboardMediaKeyHook)

        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }

        session.activate()
        assertTrue(impl.handleVirtualKey(WindowsMediaControlsImpl.VK_MEDIA_PLAY_PAUSE))
        assertTrue(impl.handleVirtualKey(WindowsMediaControlsImpl.VK_MEDIA_NEXT_TRACK))
        assertTrue(impl.handleAppCommand(WindowsMediaControlsImpl.APPCOMMAND_MEDIA_FAST_FORWARD))

        assertEquals(3, events.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, events[0].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, events[0].source)
        assertEquals(MediaControlAction.NEXT, events[1].action)
        assertEquals(MediaControlAction.FAST_FORWARD, events[2].action)
        assertEquals(MediaControlEventSource.SYSTEM_TRANSPORT, events[2].source)

        session.dispose()
    }

    @Test
    fun testWindowsTaskbarButtonSnapshotAndClicks() {
        val session = WindowsMediaControlsProvider.createSession()
        val impl = session.nativeImpl as WindowsMediaControlsImpl
        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }
        session.activate()

        val initialSnapshot = impl.resolveTaskbarButtonsSnapshot()
        assertEquals(4, initialSnapshot.size)
        assertEquals(WindowsMediaControlsImpl.THUMB_BUTTON_PLAY_PAUSE_ID, initialSnapshot[1].first)
        assertEquals("Play", initialSnapshot[1].second)

        // Click Play/Pause while STOPPED -> dispatches PLAY
        assertTrue(impl.handleTaskbarButtonClick(WindowsMediaControlsImpl.THUMB_BUTTON_PLAY_PAUSE_ID))
        assertEquals(MediaControlAction.PLAY, events.last().action)
        assertEquals(MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR, events.last().source)

        // Update state to PLAYING -> tooltip changes to Pause and click dispatches PAUSE
        session.updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 10_000L, durationMs = 60_000L)
        val playingSnapshot = impl.resolveTaskbarButtonsSnapshot()
        assertEquals("Pause", playingSnapshot[1].second)
        assertTrue(impl.handleTaskbarButtonClick(WindowsMediaControlsImpl.THUMB_BUTTON_PLAY_PAUSE_ID))
        assertEquals(MediaControlAction.PAUSE, events.last().action)

        // Stop button disabled by default -> click returns false
        assertFalse(impl.handleTaskbarButtonClick(WindowsMediaControlsImpl.THUMB_BUTTON_STOP_ID))

        // Enable stop button in config -> click dispatches STOP
        session.updateButtonConfig(MediaControlButtonConfig(stopEnabled = true, stopTooltip = "Stop Playback"))
        val updatedSnapshot = impl.resolveTaskbarButtonsSnapshot()
        assertEquals("Stop Playback", updatedSnapshot[3].second)
        assertEquals(WindowsMediaControlsImpl.THBF_ENABLED, updatedSnapshot[3].third)
        assertTrue(impl.handleTaskbarButtonClick(WindowsMediaControlsImpl.THUMB_BUTTON_STOP_ID))
        assertEquals(MediaControlAction.STOP, events.last().action)

        session.dispose()
    }
}
