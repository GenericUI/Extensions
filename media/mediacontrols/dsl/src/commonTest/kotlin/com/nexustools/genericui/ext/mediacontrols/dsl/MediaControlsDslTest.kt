package com.nexustools.genericui.ext.mediacontrols.dsl

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlListener
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsRegistry
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.window.IWindow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaControlsDslTest {

    private class TestDslMediaControlsNativeImpl : MediaControlsNativeImpl {
        override val capabilities = MediaControlsCapabilities()
        private var active = false
        private var listener: MediaControlListener? = null
        override var isDisposed: Boolean = false
            private set

        override fun bindWindow(window: IWindow?) {}
        override fun setActive(active: Boolean) { this.active = active }
        override fun isActive(): Boolean = !isDisposed && active
        override fun setKeyboardMediaKeysEnabled(enabled: Boolean) {}
        override fun setTaskbarPreviewControlsEnabled(enabled: Boolean) {}
        override fun updateMetadata(metadata: MediaControlMetadata) {}
        override fun updatePlaybackStatus(
            status: MediaPlaybackStatus,
            positionMs: Long,
            durationMs: Long,
            playbackRate: Float
        ) {}
        override fun updateButtonConfig(config: MediaControlButtonConfig) {}
        override fun setListener(listener: MediaControlListener?) { this.listener = listener }
        override fun simulateMediaKeyEvent(
            action: MediaControlAction,
            source: MediaControlEventSource,
            seekPositionMs: Long?
        ) {
            if (isActive()) {
                listener?.onMediaControlEvent(MediaControlEvent(action, seekPositionMs, source))
            }
        }
        override fun dispose() {
            active = false
            listener = null
            isDisposed = true
        }
    }

    @AfterTest
    fun tearDown() {
        MediaControlsRegistry.factory = null
    }

    @Test
    fun testMediaControlsSessionDslBuilder() {
        MediaControlsRegistry.factory = { TestDslMediaControlsNativeImpl() }
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
