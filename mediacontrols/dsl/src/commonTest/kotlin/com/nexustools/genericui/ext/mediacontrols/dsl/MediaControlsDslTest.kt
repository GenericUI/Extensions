package com.nexustools.genericui.ext.mediacontrols.dsl

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaControlsDslTest {

    @Test
    fun testMediaControlsSessionDslBuilder() {
        val window = Window("Player Window")
        val events = mutableListOf<MediaControlEvent>()
        val session = window.mediaControlsSession(
            metadata = MediaControlMetadata(title = "Track 1", artist = "Composer", durationMs = 180_000L),
            buttonConfig = MediaControlButtonConfig(stopEnabled = true),
            onEvent = { events.add(it) }
        ) {
            updatePlaybackState(MediaPlaybackStatus.PLAYING, positionMs = 5_000L)
        }

        assertTrue(session.isActive)
        assertSame(window, session.boundWindow)
        assertEquals("Track 1", session.metadata.title)
        assertEquals(MediaPlaybackStatus.PLAYING, session.playbackStatus)
        assertEquals(5_000L, session.metadata.positionMs)
        assertTrue(session.buttonConfig.stopEnabled)

        session.simulateMediaKey(MediaControlAction.NEXT)
        assertEquals(1, events.size)
        assertEquals(MediaControlAction.NEXT, events.first().action)

        session.dispose()
    }
}
