package com.nexustools.genericui.ext.media.vlc

import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.swing.SwingMediaExtensionProvider
import com.sun.jna.Platform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwingVlcConfigurationTest {

    @BeforeTest
    fun setUp() {
        MediaExtension.resetProvidersForTest()
    }

    @AfterTest
    fun tearDown() {
        MediaExtension.resetProvidersForTest()
    }

    @Test
    fun testSwingInstallEnablesVlcConfigurationAndEnumeratesCapabilitiesSafely() {
        assertFalse(MediaExtension.supportsVlcConfiguration)

        SwingMediaExtensionProvider.install()
        assertTrue(MediaExtension.supportsVlcConfiguration)

        val hwModes = MediaExtension.listSupportedVlcHardwareDecodeModes()
        assertTrue(hwModes.isNotEmpty())
        assertTrue(VlcHardwareDecodeMode.ANY in hwModes)
        assertTrue(VlcHardwareDecodeMode.NONE in hwModes)
        when {
            Platform.isWindows() -> {
                assertTrue(VlcHardwareDecodeMode.D3D11VA in hwModes)
                assertTrue(VlcHardwareDecodeMode.DXVA2 in hwModes)
            }
            Platform.isMac() -> {
                assertTrue(VlcHardwareDecodeMode.VIDEOTOOLBOX in hwModes)
            }
            Platform.isLinux() -> {
                assertTrue(VlcHardwareDecodeMode.VAAPI in hwModes)
                assertTrue(VlcHardwareDecodeMode.VDPAU in hwModes)
            }
        }

        val renderers = MediaExtension.listSupportedVlcVideoRenderers()
        assertTrue(renderers.isNotEmpty())
        assertTrue(VlcVideoRendererMode.AUTO in renderers)
        assertTrue(VlcVideoRendererMode.RV32 in renderers)
        assertTrue(VlcVideoRendererMode.RV16 in renderers)
        when {
            Platform.isWindows() -> assertTrue(VlcVideoRendererMode.DIRECT3D11 in renderers)
            Platform.isMac() -> assertTrue(VlcVideoRendererMode.MACOSX in renderers)
            Platform.isLinux() -> {
                assertTrue(VlcVideoRendererMode.XCB_X11 in renderers)
                assertTrue(VlcVideoRendererMode.WL_SHM in renderers)
            }
        }

        // Must never throw even when native LibVLC binaries are absent on a headless runner
        val modules = MediaExtension.listVlcAudioOutputModules()
        val defaultDevices = MediaExtension.listVlcAudioOutputDevices()
        val missingModuleDevices = MediaExtension.listVlcAudioOutputDevices("nonexistent_aout_module")
        assertNotNull(modules)
        assertNotNull(defaultDevices)
        assertTrue(missingModuleDevices.isEmpty())
    }

    @Test
    fun testSwingPlayersAcceptLiveVlcConfigurationUpdatesWithoutNativeCrash() {
        SwingMediaExtensionProvider.install()

        MediaExtension.vlcConfiguration = VlcConfiguration(
            audioOutputModule = "wasapi",
            audioOutputDeviceId = "default-device",
            videoRenderer = VlcVideoRendererMode.RV32,
            hardwareDecode = VlcHardwareDecodeMode.NONE,
            deinterlaceMode = VlcDeinterlaceMode.YADIF,
            spdifPassthrough = true,
            networkCachingMs = -50,
            fileCachingMs = 250
        )
        assertEquals(0, MediaExtension.vlcConfiguration.networkCachingMs)
        assertEquals(250, MediaExtension.vlcConfiguration.fileCachingMs)

        val videoPlayer = MediaExtension.createVideoPlayerNative()
        val audioPlayer = MediaExtension.createAudioPlayerNative()
        assertNotNull(videoPlayer)
        assertNotNull(audioPlayer)

        try {
            videoPlayer.setSource("https://example.com/sample.mp4", seekOffsetSeconds = 1.0)
            audioPlayer.setSource("https://example.com/sample.mp3", seekOffsetSeconds = 0.5)

            MediaExtension.vlcConfiguration = VlcConfiguration(
                audioOutputModule = "",
                audioOutputDeviceId = "",
                videoRenderer = VlcVideoRendererMode.AUTO,
                hardwareDecode = VlcHardwareDecodeMode.ANY,
                deinterlaceMode = VlcDeinterlaceMode.OFF,
                spdifPassthrough = false,
                networkCachingMs = 1200,
                fileCachingMs = 400
            )
            assertEquals(1200, MediaExtension.vlcConfiguration.networkCachingMs)
            assertEquals(VlcDeinterlaceMode.OFF, MediaExtension.vlcConfiguration.deinterlaceMode)
        } finally {
            videoPlayer.dispose()
            audioPlayer.dispose()
        }

        MediaExtension.resetProvidersForTest()
        assertFalse(MediaExtension.supportsVlcConfiguration)
        assertEquals(VlcConfiguration(), MediaExtension.vlcConfiguration)
    }
}
