package com.nexustools.genericui.ext.mediacontrols.android

import android.media.session.PlaybackState
import android.view.KeyEvent
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

class AndroidMediaControlsTest {

    @AfterTest
    fun tearDown() {
        MediaControlsRegistry.resetToPlatformDefault()
    }

    @Test
    fun testAndroidMediaControlsSessionAndKeyEvents() {
        assertTrue(AndroidMediaControlsProvider.supportsTarget(BackendTarget.Android))
        assertFalse(AndroidMediaControlsProvider.supportsTarget(BackendTarget.IOS))

        val session = AndroidMediaControlsProvider.createSession()
        val impl = session.nativeImpl as AndroidMediaControlsImpl
        assertTrue(session.capabilities.supportsSystemTransportControls)
        assertTrue(session.capabilities.supportsKeyboardMediaKeyHook)
        assertFalse(session.capabilities.supportsTaskbarThumbnailButtons)

        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }

        assertFalse(impl.handleAndroidKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        session.activate()

        session.updateMetadata(
            MediaControlMetadata(
                title = "Android Track",
                artist = "GenericUI",
                durationMs = 90_000L
            )
        )
        session.updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 12_000L)
        assertEquals(PlaybackState.STATE_PLAYING, impl.toAndroidPlaybackStateCode(MediaPlaybackStatus.PLAYING))

        assertTrue(impl.handleAndroidKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue(impl.handleAndroidKeyCode(KeyEvent.KEYCODE_HEADSETHOOK))
        assertTrue(impl.handleAndroidKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertFalse(impl.handleAndroidKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_UP))

        assertTrue(impl.dispatchTransportAction(MediaControlAction.SEEK_TO, 30_000L))
        session.updateButtonConfig(MediaControlButtonConfig(stopEnabled = false, nextEnabled = false))
        assertFalse(impl.dispatchTransportAction(MediaControlAction.STOP))
        assertFalse(impl.dispatchTransportAction(MediaControlAction.NEXT))
        assertTrue((impl.computeAllowedPlaybackActions() and PlaybackState.ACTION_SKIP_TO_NEXT) == 0L)

        assertEquals(4, events.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, events[0].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, events[0].source)
        assertEquals(MediaControlAction.SEEK_TO, events[3].action)
        assertEquals(30_000L, events[3].seekPositionMs)
        assertEquals(MediaControlEventSource.SYSTEM_TRANSPORT, events[3].source)

        session.dispose()
        assertTrue(session.isDisposed)
    }
}
