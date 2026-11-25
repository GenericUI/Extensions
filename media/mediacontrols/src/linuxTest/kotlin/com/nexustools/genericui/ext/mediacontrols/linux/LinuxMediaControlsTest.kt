package com.nexustools.genericui.ext.mediacontrols.linux

import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.ext.mediacontrols.gtk.GtkMediaControlsImpl
import com.nexustools.genericui.ext.mediacontrols.gtk.GtkMediaControlsProvider
import com.nexustools.genericui.ext.mediacontrols.kde.KdeMediaControlsImpl
import com.nexustools.genericui.ext.mediacontrols.kde.KdeMediaControlsProvider
import com.nexustools.genericui.gtk.GtkWindowNativeImpl
import com.nexustools.genericui.kde.KdeWindowNativeImpl
import platform.posix.usleep
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxMediaControlsTest {

    @AfterTest
    fun tearDown() {
        MediaControlsRegistry.resetToPlatformDefault()
    }

    @Test
    fun testGtkAndKdeMediaKeysAndSessionLifecycle() {
        val gtkSession = GtkMediaControlsProvider.createSession()
        val gtkImpl = gtkSession.nativeImpl as GtkMediaControlsImpl
        val gtkEvents = mutableListOf<MediaControlEvent>()
        gtkSession.addListener { gtkEvents.add(it) }
        gtkSession.activate()
        gtkSession.updateMetadata(MediaControlMetadata(title = "GTK Song", artist = "GenericUI"))
        gtkSession.updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 4_000L, durationMs = 60_000L)

        assertTrue(gtkImpl.handleGdkKeySym(GtkMediaControlsImpl.GDK_KEY_AUDIO_PLAY))
        assertTrue(gtkImpl.handleXf86AudioKeyName("XF86AudioNext"))
        assertEquals(2, gtkEvents.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, gtkEvents[0].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, gtkEvents[0].source)
        assertEquals(MediaControlAction.NEXT, gtkEvents[1].action)
        gtkSession.dispose()

        val kdeSession = KdeMediaControlsProvider.createSession()
        val kdeImpl = kdeSession.nativeImpl as KdeMediaControlsImpl
        val kdeEvents = mutableListOf<MediaControlEvent>()
        kdeSession.addListener { kdeEvents.add(it) }
        kdeSession.activate()

        assertTrue(kdeImpl.handleKdeKeyCode(KdeMediaControlsImpl.KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE))
        assertTrue(kdeImpl.handleXf86AudioKeyName("XF86AudioStop"))
        assertEquals(2, kdeEvents.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, kdeEvents[0].action)
        assertEquals(MediaControlAction.STOP, kdeEvents[1].action)
        kdeSession.dispose()
    }

    @Test
    fun testGtkWindowKeyHookDispatchAndCrossSourceDeduplication() {
        val gtkWindow = GtkWindowNativeImpl()
        val gtkSession = GtkMediaControlsProvider.createSession()
        val gtkImpl = gtkSession.nativeImpl as GtkMediaControlsImpl
        val events = mutableListOf<MediaControlEvent>()
        gtkSession.addListener { events.add(it) }
        gtkSession.activate()

        // Dispatches XF86AudioPlay from GtkWindowNativeImpl key press
        gtkWindow.dispatchKeyDown(KeyEvent(key = "XF86AudioPlay", code = "XF86AudioPlay", keyCode = 172))
        assertEquals(1, events.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, events[0].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, events[0].source)

        // Repeat key event is ignored
        gtkWindow.dispatchKeyDown(KeyEvent(key = "XF86AudioPlay", code = "XF86AudioPlay", keyCode = 172, isRepeat = true))
        assertEquals(1, events.size)

        // MPRIS transport Play/Pause within 150ms is deduplicated
        assertFalse(gtkImpl.handleMprisTransportAction(MediaControlAction.PAUSE))
        assertFalse(gtkImpl.handleMprisTransportAction(MediaControlAction.PLAY))
        assertEquals(1, events.size)

        // Disabling keyboardMediaKeysEnabled prevents window hook dispatch
        gtkSession.keyboardMediaKeysEnabled = false
        gtkWindow.dispatchKeyDown(KeyEvent(key = "XF86AudioNext", code = "XF86AudioNext", keyCode = 171))
        assertEquals(1, events.size)
        gtkSession.keyboardMediaKeysEnabled = true

        // After >150ms, opposite-source MPRIS transport succeeds
        usleep(170_000u)
        assertTrue(gtkImpl.handleMprisTransportAction(MediaControlAction.PLAY))
        assertEquals(2, events.size)
        assertEquals(MediaControlEventSource.SYSTEM_TRANSPORT, events[1].source)

        // Disposing session unregisters hook
        gtkSession.dispose()
        gtkWindow.dispatchKeyDown(KeyEvent(key = "XF86AudioStop", code = "XF86AudioStop", keyCode = 174))
        assertEquals(2, events.size)
        gtkWindow.dispose()
    }

    @Test
    fun testKdeWindowKeyHookDispatchAndCrossSourceDeduplication() {
        val kdeWindow = KdeWindowNativeImpl()
        val kdeSession = KdeMediaControlsProvider.createSession()
        val kdeImpl = kdeSession.nativeImpl as KdeMediaControlsImpl
        val events = mutableListOf<MediaControlEvent>()
        kdeSession.addListener { events.add(it) }
        kdeSession.activate()

        // Dispatches Qt::Key_MediaTogglePlayPause from KdeWindowNativeImpl key press
        kdeWindow.dispatchKeyDown(
            KeyEvent(
                key = "Key_${KdeMediaControlsImpl.KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE}",
                code = "Key_${KdeMediaControlsImpl.KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE}",
                keyCode = KdeMediaControlsImpl.KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE
            )
        )
        assertEquals(1, events.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, events[0].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, events[0].source)

        // Repeat key event is ignored
        kdeWindow.dispatchKeyDown(
            KeyEvent(
                key = "Key_${KdeMediaControlsImpl.KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE}",
                code = "Key_${KdeMediaControlsImpl.KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE}",
                keyCode = KdeMediaControlsImpl.KDE_KEY_MEDIA_TOGGLE_PLAY_PAUSE,
                isRepeat = true
            )
        )
        assertEquals(1, events.size)

        // MPRIS transport Pause within 150ms is deduplicated
        assertFalse(kdeImpl.handleMprisTransportAction(MediaControlAction.PAUSE))
        assertEquals(1, events.size)

        // After >150ms, MPRIS transport succeeds
        usleep(170_000u)
        assertTrue(kdeImpl.handleMprisTransportAction(MediaControlAction.PAUSE))
        assertEquals(2, events.size)
        assertEquals(MediaControlEventSource.SYSTEM_TRANSPORT, events[1].source)

        // Disposing session unregisters hook
        kdeSession.dispose()
        kdeWindow.dispatchKeyDown(
            KeyEvent(
                key = "Key_${KdeMediaControlsImpl.KDE_KEY_MEDIA_NEXT}",
                code = "Key_${KdeMediaControlsImpl.KDE_KEY_MEDIA_NEXT}",
                keyCode = KdeMediaControlsImpl.KDE_KEY_MEDIA_NEXT
            )
        )
        assertEquals(2, events.size)
        kdeWindow.dispose()
    }
}
