@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.windows

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import platform.windows.Sleep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
        assertEquals("", initialSnapshot[1].second)

        session.updateButtonConfig(MediaControlButtonConfig(playTooltip = "Play", pauseTooltip = "Pause"))
        assertEquals("Play", impl.resolveTaskbarButtonsSnapshot()[1].second)

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

    @Test
    fun testWin32MediaThumbIconFactoryAndButtonIconResolution() {
        val session = WindowsMediaControlsProvider.createSession()
        val impl = session.nativeImpl as WindowsMediaControlsImpl
        session.activate()

        val prevIcon = impl.resolveTaskbarButtonIcon(WindowsMediaControlsImpl.THUMB_BUTTON_PREVIOUS_ID)
        val playIcon = impl.resolveTaskbarButtonIcon(WindowsMediaControlsImpl.THUMB_BUTTON_PLAY_PAUSE_ID)
        val nextIcon = impl.resolveTaskbarButtonIcon(WindowsMediaControlsImpl.THUMB_BUTTON_NEXT_ID)
        val stopIcon = impl.resolveTaskbarButtonIcon(WindowsMediaControlsImpl.THUMB_BUTTON_STOP_ID)

        assertNotNull(prevIcon)
        assertNotNull(playIcon)
        assertNotNull(nextIcon)
        assertNotNull(stopIcon)
        assertEquals(prevIcon, impl.resolveTaskbarButtonIcon(WindowsMediaControlsImpl.THUMB_BUTTON_PREVIOUS_ID))

        session.updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 5_000L, durationMs = 30_000L)
        val pauseIcon = impl.resolveTaskbarButtonIcon(WindowsMediaControlsImpl.THUMB_BUTTON_PLAY_PAUSE_ID)
        assertNotNull(pauseIcon)
        assertNotEquals(playIcon, pauseIcon)

        session.dispose()
    }

    @Test
    fun testWindowsCrossSourceDeduplicationBetweenKeyboardHookAndAppCommand() {
        val session = WindowsMediaControlsProvider.createSession()
        val impl = session.nativeImpl as WindowsMediaControlsImpl
        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }
        session.activate()

        // Same-source rapid virtual key presses are not suppressed.
        assertTrue(impl.handleVirtualKey(WindowsMediaControlsImpl.VK_MEDIA_PLAY_PAUSE))
        assertTrue(impl.handleVirtualKey(WindowsMediaControlsImpl.VK_MEDIA_PLAY_PAUSE))
        assertEquals(2, events.size)

        // Opposite-source WM_APPCOMMAND within 150ms across {PLAY, PAUSE, PLAY_PAUSE} is dropped.
        assertFalse(impl.handleAppCommand(WindowsMediaControlsImpl.APPCOMMAND_MEDIA_PLAY_PAUSE))
        assertFalse(impl.handleAppCommand(WindowsMediaControlsImpl.APPCOMMAND_MEDIA_PLAY))
        assertFalse(impl.handleAppCommand(WindowsMediaControlsImpl.APPCOMMAND_MEDIA_PAUSE))
        assertEquals(2, events.size)

        // simulateMediaKeyEvent bypasses cross-source dedupe.
        session.simulateMediaKey(MediaControlAction.PAUSE, MediaControlEventSource.SYSTEM_TRANSPORT)
        assertEquals(3, events.size)

        // Distinct action from WM_APPCOMMAND succeeds, and duplicate virtual key within 150ms is dropped.
        assertTrue(impl.handleAppCommand(WindowsMediaControlsImpl.APPCOMMAND_MEDIA_NEXTTRACK))
        assertFalse(impl.handleVirtualKey(WindowsMediaControlsImpl.VK_MEDIA_NEXT_TRACK))
        assertEquals(4, events.size)

        // After >150ms, opposite-source WM_APPCOMMAND play/pause succeeds again.
        Sleep(170u)
        assertTrue(impl.handleAppCommand(WindowsMediaControlsImpl.APPCOMMAND_MEDIA_PLAY_PAUSE))
        assertEquals(5, events.size)

        session.dispose()
    }
}
