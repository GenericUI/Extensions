package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.window.IWindow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaControlsSessionTest {

    private class TestMediaControlsNativeImpl(
        override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities()
    ) : MediaControlsNativeImpl {
        private val state = MediaControlsStateSupport(capabilities)

        val boundWindow: IWindow?
            get() = state.boundWindow
        val currentMetadata: MediaControlMetadata
            get() = state.currentMetadata
        val currentPlaybackStatus: MediaPlaybackStatus
            get() = state.currentPlaybackStatus
        val currentPositionMs: Long
            get() = state.currentPositionMs
        val currentDurationMs: Long
            get() = state.currentDurationMs
        val currentPlaybackRate: Float
            get() = state.currentPlaybackRate
        val currentButtonConfig: MediaControlButtonConfig
            get() = state.currentButtonConfig

        override val isDisposed: Boolean
            get() = state.isDisposed

        override fun bindWindow(window: IWindow?) = state.bindWindow(window)
        override fun setActive(active: Boolean) { state.setActive(active) }
        override fun isActive(): Boolean = state.isActive()
        override fun setKeyboardMediaKeysEnabled(enabled: Boolean) = state.setKeyboardMediaKeysEnabled(enabled)
        override fun setTaskbarPreviewControlsEnabled(enabled: Boolean) = state.setTaskbarPreviewControlsEnabled(enabled)
        override fun updateMetadata(metadata: MediaControlMetadata) = state.updateMetadata(metadata)
        override fun updatePlaybackStatus(
            status: MediaPlaybackStatus,
            positionMs: Long,
            durationMs: Long,
            playbackRate: Float
        ) = state.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        override fun updateButtonConfig(config: MediaControlButtonConfig) = state.updateButtonConfig(config)
        override fun setListener(listener: MediaControlListener?) = state.setListener(listener)
        override fun simulateMediaKeyEvent(
            action: MediaControlAction,
            source: MediaControlEventSource,
            seekPositionMs: Long?
        ) {
            state.dispatchEvent(action, source, seekPositionMs)
        }
        override fun dispose() = state.markDisposed()
    }

    @AfterTest
    fun tearDown() {
        MediaControlsRegistry.resetToPlatformDefault()
    }

    @Test
    fun testRegistryDefaultsToPlatformFactoryAndFailsFastWhenUninstalled() {
        MediaControlsRegistry.resetToPlatformDefault()
        assertNotNull(MediaControlsRegistry.factory)
        val defaultSession = MediaControlsSession()
        assertNotNull(defaultSession.nativeImpl)
        defaultSession.dispose()

        MediaControlsRegistry.factory = null
        assertFailsWith<IllegalStateException> {
            MediaControlsRegistry.createNativeImpl()
        }
        assertFailsWith<IllegalStateException> {
            MediaControlsSession()
        }
    }

    @Test
    fun testSessionLifecycleAndWindowBinding() {
        val window = Window("Media Player")
        val impl = TestMediaControlsNativeImpl()
        val session = MediaControlsSession(window = window, nativeImpl = impl)

        assertSame(window, session.boundWindow)
        assertSame(window, impl.boundWindow)
        assertFalse(session.isActive)
        assertFalse(session.isDisposed)

        session.activate()
        assertTrue(session.isActive)
        assertTrue(impl.isActive())

        session.deactivate()
        assertFalse(session.isActive)
        assertFalse(impl.isActive())

        session.activate()
        session.dispose()
        assertTrue(session.isDisposed)
        assertFalse(session.isActive)
        assertNull(session.boundWindow)
        assertNull(impl.boundWindow)
    }

    @Test
    fun testMetadataPlaybackStateAndButtonConfigUpdates() {
        val impl = TestMediaControlsNativeImpl()
        val session = MediaControlsSession(nativeImpl = impl)
        val artwork = ImageBuffer(16, 16)

        val meta = MediaControlMetadata(
            title = "Symphony No. 9",
            subtitle = "Movement IV",
            artist = "Beethoven",
            album = "Choral",
            artwork = artwork,
            artworkUrl = "https://example.com/art.png",
            durationMs = 240_000L,
            positionMs = 15_000L,
            playbackRate = 1.0f
        )
        session.updateMetadata(meta)
        assertEquals("Symphony No. 9", impl.currentMetadata.title)
        assertEquals("Beethoven", impl.currentMetadata.artist)
        assertEquals(240_000L, impl.currentDurationMs)
        assertEquals(15_000L, impl.currentPositionMs)

        session.updatePlaybackState(
            status = MediaPlaybackStatus.PLAYING,
            positionMs = 42_500L,
            durationMs = 240_000L,
            playbackRate = 1.25f
        )
        assertEquals(MediaPlaybackStatus.PLAYING, session.playbackStatus)
        assertEquals(MediaPlaybackStatus.PLAYING, impl.currentPlaybackStatus)
        assertEquals(42_500L, session.metadata.positionMs)
        assertEquals(42_500L, impl.currentPositionMs)
        assertEquals(1.25f, session.metadata.playbackRate)
        assertEquals(1.25f, impl.currentPlaybackRate)

        val btnCfg = MediaControlButtonConfig(
            playPauseEnabled = true,
            previousEnabled = false,
            nextEnabled = true,
            stopEnabled = true,
            seekEnabled = false,
            previousTooltip = "Prev Track",
            playTooltip = "Resume",
            pauseTooltip = "Hold",
            nextTooltip = "Skip",
            stopTooltip = "Halt"
        )
        session.updateButtonConfig(btnCfg)
        assertEquals(btnCfg, session.buttonConfig)
        assertEquals(btnCfg, impl.currentButtonConfig)
    }

    @Test
    fun testEventDispatchAcrossAllSourcesAndToggleFlags() {
        MediaControlsRegistry.factory = { TestMediaControlsNativeImpl() }
        val received = mutableListOf<MediaControlEvent>()
        val session = MediaControlsSession()
        val listener = MediaControlListener { event -> received.add(event) }
        session.addListener(listener)

        session.simulateMediaKey(MediaControlAction.PLAY_PAUSE)
        assertTrue(received.isEmpty())

        session.activate()

        session.simulateMediaKey(MediaControlAction.PLAY_PAUSE, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
        assertEquals(1, received.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, received.last().action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, received.last().source)

        session.keyboardMediaKeysEnabled = false
        session.simulateMediaKey(MediaControlAction.NEXT, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
        assertEquals(1, received.size)
        session.keyboardMediaKeysEnabled = true

        session.simulateMediaKey(
            action = MediaControlAction.PREVIOUS,
            source = MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR
        )
        assertEquals(2, received.size)
        assertEquals(MediaControlAction.PREVIOUS, received.last().action)
        assertEquals(MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR, received.last().source)

        session.taskbarPreviewControlsEnabled = false
        session.simulateMediaKey(
            action = MediaControlAction.STOP,
            source = MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR
        )
        assertEquals(2, received.size)
        session.taskbarPreviewControlsEnabled = true

        session.simulateMediaKey(
            action = MediaControlAction.SEEK_TO,
            source = MediaControlEventSource.SYSTEM_TRANSPORT,
            seekPositionMs = 95_000L
        )
        assertEquals(3, received.size)
        assertEquals(MediaControlAction.SEEK_TO, received.last().action)
        assertEquals(95_000L, received.last().seekPositionMs)
        assertEquals(MediaControlEventSource.SYSTEM_TRANSPORT, received.last().source)

        session.removeListener(listener)
        session.simulateMediaKey(MediaControlAction.PLAY)
        assertEquals(3, received.size)
        session.dispose()
    }

    @Test
    fun testMediaControlsRegistryFactory() {
        val customCapabilities = MediaControlsCapabilities(
            supportsSystemTransportControls = true,
            supportsTaskbarThumbnailButtons = true,
            supportsKeyboardMediaKeyHook = true
        )
        MediaControlsRegistry.factory = {
            TestMediaControlsNativeImpl(capabilities = customCapabilities)
        }
        val session = MediaControlsSession()
        assertNotNull(session.nativeImpl)
        assertTrue(session.capabilities.supportsSystemTransportControls)
        assertTrue(session.capabilities.supportsTaskbarThumbnailButtons)
        assertTrue(session.capabilities.supportsKeyboardMediaKeyHook)
        session.dispose()
    }
}
