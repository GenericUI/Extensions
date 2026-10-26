package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.image.ImageBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaControlsSessionTest {

    @AfterTest
    fun tearDown() {
        MediaControlsRegistry.factory = null
    }

    @Test
    fun testSessionLifecycleAndWindowBinding() {
        val window = Window("Media Player")
        val impl = DefaultNoOpMediaControlsNativeImpl()
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
        val impl = DefaultNoOpMediaControlsNativeImpl()
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
        val received = mutableListOf<MediaControlEvent>()
        val session = MediaControlsSession()
        val listener = MediaControlListener { event -> received.add(event) }
        session.addListener(listener)

        // Inactive session ignores events
        session.simulateMediaKey(MediaControlAction.PLAY_PAUSE)
        assertTrue(received.isEmpty())

        session.activate()

        // 1. KEYBOARD_MEDIA_KEY
        session.simulateMediaKey(MediaControlAction.PLAY_PAUSE, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
        assertEquals(1, received.size)
        assertEquals(MediaControlAction.PLAY_PAUSE, received.last().action)
        assertEquals(MediaControlEventSource.KEYBOARD_MEDIA_KEY, received.last().source)

        // Disable keyboard media keys -> ignored
        session.keyboardMediaKeysEnabled = false
        session.simulateMediaKey(MediaControlAction.NEXT, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
        assertEquals(1, received.size)
        session.keyboardMediaKeysEnabled = true

        // 2. TASKBAR_THUMBNAIL_TOOLBAR
        session.simulateMediaKey(
            action = MediaControlAction.PREVIOUS,
            source = MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR
        )
        assertEquals(2, received.size)
        assertEquals(MediaControlAction.PREVIOUS, received.last().action)
        assertEquals(MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR, received.last().source)

        // Disable taskbar preview controls -> ignored
        session.taskbarPreviewControlsEnabled = false
        session.simulateMediaKey(
            action = MediaControlAction.STOP,
            source = MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR
        )
        assertEquals(2, received.size)
        session.taskbarPreviewControlsEnabled = true

        // 3. SYSTEM_TRANSPORT with seekPositionMs
        session.simulateMediaKey(
            action = MediaControlAction.SEEK_TO,
            source = MediaControlEventSource.SYSTEM_TRANSPORT,
            seekPositionMs = 95_000L
        )
        assertEquals(3, received.size)
        assertEquals(MediaControlAction.SEEK_TO, received.last().action)
        assertEquals(95_000L, received.last().seekPositionMs)
        assertEquals(MediaControlEventSource.SYSTEM_TRANSPORT, received.last().source)

        // Remove listener
        session.removeListener(listener)
        session.simulateMediaKey(MediaControlAction.PLAY)
        assertEquals(3, received.size)
    }

    @Test
    fun testMediaControlsRegistryFactory() {
        val customCapabilities = MediaControlsCapabilities(
            supportsSystemTransportControls = true,
            supportsTaskbarThumbnailButtons = true,
            supportsKeyboardMediaKeyHook = true
        )
        MediaControlsRegistry.factory = {
            DefaultNoOpMediaControlsNativeImpl(capabilities = customCapabilities)
        }
        val session = MediaControlsSession()
        assertNotNull(session.nativeImpl)
        assertTrue(session.capabilities.supportsSystemTransportControls)
        assertTrue(session.capabilities.supportsTaskbarThumbnailButtons)
        assertTrue(session.capabilities.supportsKeyboardMediaKeyHook)
        session.dispose()
    }
}
