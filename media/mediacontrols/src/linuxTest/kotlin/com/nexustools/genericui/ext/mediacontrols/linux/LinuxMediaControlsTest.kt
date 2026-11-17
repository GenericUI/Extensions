package com.nexustools.genericui.ext.mediacontrols.linux

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.ext.mediacontrols.gtk.GtkMediaControlsImpl
import com.nexustools.genericui.ext.mediacontrols.gtk.GtkMediaControlsProvider
import com.nexustools.genericui.ext.mediacontrols.qt.QtMediaControlsImpl
import com.nexustools.genericui.ext.mediacontrols.qt.QtMediaControlsProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxMediaControlsTest {

    @AfterTest
    fun tearDown() {
        MediaControlsRegistry.resetToPlatformDefault()
    }

    @Test
    fun testGtkAndQtMediaKeysAndSessionLifecycle() {
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

        val qtSession = QtMediaControlsProvider.createSession()
        val qtImpl = qtSession.nativeImpl as QtMediaControlsImpl
        val qtEvents = mutableListOf<MediaControlEvent>()
        qtSession.addListener { qtEvents.add(it) }
        qtSession.activate()

        assertTrue(qtImpl.handleQtKeyCode(QtMediaControlsImpl.QT_KEY_MEDIA_TOGGLE_PLAY_PAUSE))
        assertTrue(qtImpl.handleXf86AudioKeyName("XF86AudioStop"))
        assertEquals(2, qtEvents.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, qtEvents[0].action)
        assertEquals(MediaControlAction.STOP, qtEvents[1].action)
        qtSession.dispose()
    }
}
