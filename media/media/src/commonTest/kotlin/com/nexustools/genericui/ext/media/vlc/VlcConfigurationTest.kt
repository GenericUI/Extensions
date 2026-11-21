package com.nexustools.genericui.ext.media.vlc

import com.nexustools.genericui.ext.media.MediaExtension
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VlcConfigurationTest {

    private class RecordingVlcBackendConfigHandler : VlcBackendConfigHandler {
        val observedConfigs = mutableListOf<VlcConfiguration>()
        val requestedDeviceModules = mutableListOf<String>()
        var detachedCount: Int = 0

        var modulesToReturn: List<VlcAudioOutputModule> = listOf(
            VlcAudioOutputModule("wasapi", "Windows Audio Session API"),
            VlcAudioOutputModule("directsound", "DirectX Audio")
        )
        var devicesByModule: Map<String, List<VlcAudioOutputDevice>> = mapOf(
            "wasapi" to listOf(VlcAudioOutputDevice("dev-1", "Speakers")),
            "directsound" to listOf(VlcAudioOutputDevice("dev-2", "Headphones"))
        )
        var hwModesToReturn: List<VlcHardwareDecodeMode> = listOf(
            VlcHardwareDecodeMode.ANY,
            VlcHardwareDecodeMode.D3D11VA,
            VlcHardwareDecodeMode.NONE
        )
        var renderersToReturn: List<VlcVideoRendererMode> = listOf(
            VlcVideoRendererMode.AUTO,
            VlcVideoRendererMode.DIRECT3D11,
            VlcVideoRendererMode.RV32
        )

        override fun onVlcConfigurationChanged(configuration: VlcConfiguration) {
            observedConfigs.add(configuration)
        }

        override fun listAudioOutputModules(): List<VlcAudioOutputModule> = modulesToReturn

        override fun listAudioOutputDevices(audioOutputModule: String): List<VlcAudioOutputDevice> {
            requestedDeviceModules.add(audioOutputModule)
            return devicesByModule[audioOutputModule] ?: emptyList()
        }

        override fun listSupportedHardwareDecodeModes(): List<VlcHardwareDecodeMode> = hwModesToReturn

        override fun listSupportedVideoRenderers(): List<VlcVideoRendererMode> = renderersToReturn

        override fun onDetached() {
            detachedCount++
        }
    }

    @BeforeTest
    fun setUp() {
        MediaExtension.resetProvidersForTest()
    }

    @AfterTest
    fun tearDown() {
        MediaExtension.resetProvidersForTest()
    }

    @Test
    fun testDefaultConfigurationValues() {
        val defaults = VlcConfiguration()
        assertEquals("", defaults.audioOutputModule)
        assertEquals("", defaults.audioOutputDeviceId)
        assertEquals(VlcVideoRendererMode.AUTO, defaults.videoRenderer)
        assertEquals(VlcHardwareDecodeMode.ANY, defaults.hardwareDecode)
        assertEquals(VlcDeinterlaceMode.OFF, defaults.deinterlaceMode)
        assertFalse(defaults.spdifPassthrough)
        assertEquals(1000, defaults.networkCachingMs)
        assertEquals(300, defaults.fileCachingMs)
    }

    @Test
    fun testEnumParsingHelpers() {
        for (mode in VlcHardwareDecodeMode.entries) {
            assertEquals(mode, VlcHardwareDecodeMode.fromCliValue(mode.cliValue))
            assertEquals(mode, VlcHardwareDecodeMode.fromCliValue("  ${mode.cliValue.uppercase()}  "))
        }
        assertEquals(VlcHardwareDecodeMode.ANY, VlcHardwareDecodeMode.fromCliValue("unknown-hw"))

        for (renderer in VlcVideoRendererMode.entries) {
            assertEquals(renderer, VlcVideoRendererMode.fromId(renderer.id))
            assertEquals(renderer, VlcVideoRendererMode.fromId("  ${renderer.id.lowercase()}  "))
        }
        assertEquals(VlcVideoRendererMode.AUTO, VlcVideoRendererMode.fromId("unknown-vout"))

        for (deinterlace in VlcDeinterlaceMode.entries) {
            assertEquals(deinterlace, VlcDeinterlaceMode.fromId(deinterlace.id))
            assertEquals(deinterlace, VlcDeinterlaceMode.fromId("  ${deinterlace.id.uppercase()}  "))
        }
        assertNull(VlcDeinterlaceMode.OFF.vlcMode)
        assertEquals("yadif2x", VlcDeinterlaceMode.YADIF2X.vlcMode)
        assertEquals(VlcDeinterlaceMode.OFF, VlcDeinterlaceMode.fromId("unknown-deinterlace"))
    }

    @Test
    fun testMediaExtensionDelegationCoercionAndReset() {
        assertFalse(MediaExtension.supportsVlcConfiguration)
        assertTrue(MediaExtension.listVlcAudioOutputModules().isEmpty())
        assertTrue(MediaExtension.listVlcAudioOutputDevices().isEmpty())
        assertTrue(MediaExtension.listSupportedVlcHardwareDecodeModes().isEmpty())
        assertTrue(MediaExtension.listSupportedVlcVideoRenderers().isEmpty())

        MediaExtension.vlcConfiguration = VlcConfiguration(
            audioOutputModule = "wasapi",
            audioOutputDeviceId = "dev-1",
            videoRenderer = VlcVideoRendererMode.DIRECT3D11,
            hardwareDecode = VlcHardwareDecodeMode.D3D11VA,
            deinterlaceMode = VlcDeinterlaceMode.YADIF,
            spdifPassthrough = true,
            networkCachingMs = -250,
            fileCachingMs = -10
        )
        assertEquals(0, MediaExtension.vlcConfiguration.networkCachingMs)
        assertEquals(0, MediaExtension.vlcConfiguration.fileCachingMs)
        assertEquals("wasapi", MediaExtension.vlcConfiguration.audioOutputModule)

        val handler = RecordingVlcBackendConfigHandler()
        MediaExtension.registerProviders(vlcConfigHandler = handler)

        assertTrue(MediaExtension.supportsVlcConfiguration)
        assertEquals(1, handler.observedConfigs.size)
        assertEquals(VlcHardwareDecodeMode.D3D11VA, handler.observedConfigs.first().hardwareDecode)

        val modules = MediaExtension.listVlcAudioOutputModules()
        assertEquals(2, modules.size)
        assertEquals("wasapi", modules.first().name)

        val defaultDevices = MediaExtension.listVlcAudioOutputDevices()
        assertEquals(listOf("wasapi"), handler.requestedDeviceModules)
        assertEquals(listOf(VlcAudioOutputDevice("dev-1", "Speakers")), defaultDevices)

        val directSoundDevices = MediaExtension.listVlcAudioOutputDevices("directsound")
        assertEquals(listOf(VlcAudioOutputDevice("dev-2", "Headphones")), directSoundDevices)

        assertEquals(handler.hwModesToReturn, MediaExtension.listSupportedVlcHardwareDecodeModes())
        assertEquals(handler.renderersToReturn, MediaExtension.listSupportedVlcVideoRenderers())

        val updated = VlcConfiguration(
            audioOutputModule = "directsound",
            audioOutputDeviceId = "dev-2",
            videoRenderer = VlcVideoRendererMode.OPENGL,
            hardwareDecode = VlcHardwareDecodeMode.NONE,
            deinterlaceMode = VlcDeinterlaceMode.BLEND,
            spdifPassthrough = false,
            networkCachingMs = 2500,
            fileCachingMs = 450
        )
        MediaExtension.vlcConfiguration = updated
        assertEquals(updated, MediaExtension.vlcConfiguration)
        assertEquals(updated, handler.observedConfigs.last())

        MediaExtension.resetProvidersForTest()
        assertFalse(MediaExtension.supportsVlcConfiguration)
        assertEquals(1, handler.detachedCount)
        assertEquals(VlcConfiguration(), MediaExtension.vlcConfiguration)
        assertTrue(MediaExtension.listVlcAudioOutputModules().isEmpty())
    }

    @Test
    fun testOptionMergingAndMediaOptions() {
        val config = VlcConfiguration(
            audioOutputModule = "auhal",
            audioOutputDeviceId = "builtin",
            videoRenderer = VlcVideoRendererMode.OPENGL,
            hardwareDecode = VlcHardwareDecodeMode.VIDEOTOOLBOX,
            deinterlaceMode = VlcDeinterlaceMode.YADIF2X,
            spdifPassthrough = true,
            networkCachingMs = 2200,
            fileCachingMs = 150
        )
        val merged = VlcPlatformCapabilities.mergeLibVlcArgs(
            baseArgs = listOf("--no-video-title-show", "--no-spdif", "--avcodec-hw=any", "--network-caching=1500", "--quiet"),
            config = config,
            includeVideoOptions = true,
            allowWindowVoutFlag = true
        )
        assertTrue("--no-video-title-show" in merged)
        assertTrue("--quiet" in merged)
        assertFalse("--no-spdif" in merged)
        assertFalse("--avcodec-hw=any" in merged)
        assertFalse("--network-caching=1500" in merged)
        assertTrue("--spdif" in merged)
        assertTrue("--avcodec-hw=videotoolbox" in merged)
        assertTrue("--network-caching=2200" in merged)
        assertTrue("--file-caching=150" in merged)
        assertTrue("--aout=auhal" in merged)
        assertTrue("--vout=gl" in merged)
        assertTrue("--deinterlace=1" in merged)
        assertTrue("--deinterlace-mode=yadif2x" in merged)

        val mediaOptions = VlcPlatformCapabilities.buildMediaOptions(
            config = config,
            seekOffsetSeconds = 12.5,
            includeVideoOptions = true,
            allowWindowVoutOption = true,
            includeHttpReconnect = true
        )
        assertTrue(":start-time=12.5" in mediaOptions)
        assertTrue(":http-reconnect" in mediaOptions)
        assertTrue(":network-caching=2200" in mediaOptions)
        assertTrue(":file-caching=150" in mediaOptions)
        assertTrue(":spdif" in mediaOptions)
        assertTrue(":aout=auhal" in mediaOptions)
        assertTrue(":avcodec-hw=videotoolbox" in mediaOptions)
        assertTrue(":vout=gl" in mediaOptions)
        assertTrue(":deinterlace=1" in mediaOptions)
        assertTrue(":deinterlace-mode=yadif2x" in mediaOptions)
    }
}
