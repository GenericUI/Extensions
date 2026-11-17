package com.nexustools.genericui.ext.mediacontrols.swing

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwingMediaControlsTest {

    @Test
    fun testSwingMultimediaKeyHandling() {
        val session = SwingMediaControlsProvider.createSession()
        val impl = session.nativeImpl as SwingMediaControlsImpl
        val events = mutableListOf<MediaControlEvent>()
        session.addListener { events.add(it) }
        session.activate()

        assertTrue(impl.handleAwtKeyCodes(SwingMediaControlsImpl.RAW_VK_MEDIA_PLAY_PAUSE))
        assertTrue(impl.handleAwtKeyCodes(0, SwingMediaControlsImpl.XF86_AUDIO_NEXT))

        assertEquals(2, events.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, events[0].action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, events[0].source)
        assertEquals(MediaControlAction.NEXT, events[1].action)

        session.dispose()
    }
}
