package com.nexustools.genericui.ext.media

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.media.ExpandingVolumeSlider
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaVolumeIcons
import com.nexustools.genericui.component.media.SeekBar
import com.nexustools.genericui.component.media.res.R
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.overlay.YoutubeMediaOverlayHelper
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputDevice
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputModule
import com.nexustools.genericui.ext.media.vlc.VlcBackendConfigHandler
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcConfigurationSchemaDescriptor
import com.nexustools.genericui.ext.media.vlc.VlcDeinterlaceMode
import com.nexustools.genericui.ext.media.vlc.VlcHardwareDecodeMode
import com.nexustools.genericui.ext.media.vlc.VlcVideoRendererMode
import com.nexustools.genericui.ext.overlay.OverlayActions
import com.nexustools.genericui.ext.overlay.OverlayBufferedRange
import com.nexustools.genericui.ext.overlay.OverlayState
import com.nexustools.genericui.ext.overlay.YoutubeOverlayConfig
import com.nexustools.genericui.ext.overlay.YoutubePlayerOverlay
import com.nexustools.genericui.ext.schema.ObjectSchema
import com.nexustools.genericui.ext.schema.SchemaForm
import com.nexustools.genericui.ext.schema.SchemaRegistry
import com.nexustools.genericui.ext.schema.SelectFieldSpec
import com.nexustools.genericui.ext.schema.SelectSchemaFieldController
import com.nexustools.genericui.ext.schema.StringFieldSpec
import com.nexustools.genericui.res.Resources
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaExtensionCodecAndSettingsSchemaTest {

    @BeforeTest
    @AfterTest
    fun resetSchedulers() {
        Animator.cancelAllAnimations()
        UiTimerScheduler.clearAllTimers()
    }

    private class CountingCachingCodecSupportProvider : CachingMediaCodecSupportProvider() {
        var capabilitiesProbeCount: Int = 0
        val videoProbeCounts: MutableMap<VideoCodec, Int> = linkedMapOf()
        val audioProbeCounts: MutableMap<AudioCodec, Int> = linkedMapOf()

        override fun probeVideoCodecSupported(codec: VideoCodec): Boolean {
            videoProbeCounts[codec] = (videoProbeCounts[codec] ?: 0) + 1
            return codec == VideoCodec.H264 || codec == VideoCodec.VP9
        }

        override fun probeAudioCodecSupported(codec: AudioCodec): Boolean {
            audioProbeCounts[codec] = (audioProbeCounts[codec] ?: 0) + 1
            return codec == AudioCodec.AAC || codec == AudioCodec.OPUS
        }

        override fun probeMediaCapabilities(): MediaCapabilitiesReport {
            capabilitiesProbeCount++
            return MediaCapabilitiesReport(
                videoCodecs = mapOf(
                    VideoCodec.H264 to CodecSupportLevel.HARDWARE_ACCELERATED,
                    VideoCodec.VP9 to CodecSupportLevel.SOFTWARE_ONLY
                ),
                audioCodecs = mapOf(
                    AudioCodec.AAC to CodecSupportLevel.HARDWARE_ACCELERATED,
                    AudioCodec.OPUS to CodecSupportLevel.SUPPORTED
                ),
                containers = setOf(MediaContainer.MP4, MediaContainer.WEBM),
                supportsAudioTrackSelection = false,
                supportsSubtitleTrackSelection = true,
                hardwareAccelerationBackend = "TestCachingBackend"
            )
        }
    }

    @BeforeTest
    fun setUp() {
        Animator.cancelAllAnimations()
        MediaExtension.resetProvidersForTest()
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        MediaExtension.resetProvidersForTest()
    }

    @Test
    fun testLibVlcCodecSupportProviderReturnsFlatTrueWithoutPlayerInstantiation() {
        MediaExtension.registerCodecSupportProvider(LibVlcCodecSupportProvider("libvlc [test]"))

        for (codec in VideoCodec.entries) {
            assertTrue(MediaExtension.isVideoCodecSupported(codec))
        }
        for (codec in AudioCodec.entries) {
            assertTrue(MediaExtension.isAudioCodecSupported(codec))
        }
        assertEquals(LibVlcCodecSupportProvider.ALL_VIDEO_CODECS, MediaExtension.getSupportedVideoCodecs())
        assertEquals(LibVlcCodecSupportProvider.ALL_AUDIO_CODECS, MediaExtension.getSupportedAudioCodecs())
        assertTrue(MediaExtension.supportsAudioTrackSelection())
        assertTrue(MediaExtension.supportsSubtitleTrackSelection())

        val report = MediaExtension.getMediaCapabilities()
        assertEquals("libvlc [test]", report.hardwareAccelerationBackend)
        assertEquals(LibVlcCodecSupportProvider.ALL_CONTAINERS, report.containers)
        assertTrue(report.supportsHdr10)
        assertTrue(report.supportsHlg)
        assertTrue(report.supportsDolbyVision)
        assertTrue(report.supportsSurround51)
        assertTrue(report.supportsSurround71)
        assertTrue(report.supportsAudioTrackSelection)
        assertTrue(report.supportsSubtitleTrackSelection)
    }

    @Test
    fun testCachingMediaCodecSupportProviderProbesAtMostOnceWithoutPlayerProviders() {
        val provider = CountingCachingCodecSupportProvider()
        MediaExtension.registerCodecSupportProvider(provider)

        assertTrue(MediaExtension.isVideoCodecSupported(VideoCodec.H264))
        assertTrue(MediaExtension.isVideoCodecSupported(VideoCodec.H264))
        assertFalse(MediaExtension.isVideoCodecSupported(VideoCodec.AV1))
        assertFalse(MediaExtension.isVideoCodecSupported(VideoCodec.AV1))
        assertEquals(1, provider.videoProbeCounts[VideoCodec.H264])
        assertEquals(1, provider.videoProbeCounts[VideoCodec.AV1])

        assertTrue(MediaExtension.isAudioCodecSupported(AudioCodec.OPUS))
        assertTrue(MediaExtension.isAudioCodecSupported(AudioCodec.OPUS))
        assertFalse(MediaExtension.isAudioCodecSupported(AudioCodec.DTS))
        assertFalse(MediaExtension.isAudioCodecSupported(AudioCodec.DTS))
        assertEquals(1, provider.audioProbeCounts[AudioCodec.OPUS])
        assertEquals(1, provider.audioProbeCounts[AudioCodec.DTS])

        val report1 = MediaExtension.getMediaCapabilities()
        val report2 = MediaExtension.getMediaCapabilities()
        assertSame(report1, report2)
        assertEquals(1, provider.capabilitiesProbeCount)
        assertEquals("TestCachingBackend", report1.hardwareAccelerationBackend)
        assertFalse(MediaExtension.supportsAudioTrackSelection())
        assertTrue(MediaExtension.supportsSubtitleTrackSelection())

        val videoSet1 = MediaExtension.getSupportedVideoCodecs()
        val videoSet2 = MediaExtension.getSupportedVideoCodecs()
        assertEquals(setOf(VideoCodec.H264, VideoCodec.VP9), videoSet1)
        assertEquals(videoSet1, videoSet2)
        for (codec in VideoCodec.entries) {
            assertEquals(1, provider.videoProbeCounts[codec])
        }

        val audioSet1 = MediaExtension.getSupportedAudioCodecs()
        val audioSet2 = MediaExtension.getSupportedAudioCodecs()
        assertEquals(setOf(AudioCodec.AAC, AudioCodec.OPUS), audioSet1)
        assertEquals(audioSet1, audioSet2)
        for (codec in AudioCodec.entries) {
            assertEquals(1, provider.audioProbeCounts[codec])
        }
    }

    @Test
    fun testPlayerSettingsSchemaReactiveDeviceLookupAndValuesRoundtrip() {
        assertFalse(MediaExtension.hasConfigurablePlayerSettings())
        assertTrue(MediaExtension.getPlayerSettingsSchema().nodes.isEmpty())
        assertTrue(MediaExtension.getPlayerSettingsValues().isEmpty())

        val recordedConfigs = mutableListOf<VlcConfiguration>()
        val handler = object : VlcBackendConfigHandler {
            override fun onVlcConfigurationChanged(configuration: VlcConfiguration) {
                recordedConfigs.add(configuration)
            }

            override fun listAudioOutputModules(): List<VlcAudioOutputModule> = listOf(
                VlcAudioOutputModule("mmdevice", "Windows Multimedia Device"),
                VlcAudioOutputModule("directsound", "DirectX DirectSound")
            )

            override fun listAudioOutputDevices(audioOutputModule: String): List<VlcAudioOutputDevice> =
                when (audioOutputModule) {
                    "directsound" -> listOf(
                        VlcAudioOutputDevice("ds-primary", "Primary Sound Driver"),
                        VlcAudioOutputDevice("ds-hdmi", "HDMI Output")
                    )
                    else -> listOf(
                        VlcAudioOutputDevice("mm-speakers", "Desktop Speakers")
                    )
                }

            override fun listSupportedHardwareDecodeModes(): List<VlcHardwareDecodeMode> =
                listOf(VlcHardwareDecodeMode.ANY, VlcHardwareDecodeMode.D3D11VA, VlcHardwareDecodeMode.NONE)

            override fun listSupportedVideoRenderers(): List<VlcVideoRendererMode> =
                listOf(VlcVideoRendererMode.AUTO, VlcVideoRendererMode.DIRECT3D11, VlcVideoRendererMode.RV32)
        }

        MediaExtension.registerVlcConfigHandler(handler)
        assertTrue(MediaExtension.hasConfigurablePlayerSettings(MediaPlayerSettingsTarget.VIDEO))
        assertTrue(MediaExtension.hasConfigurablePlayerSettings(MediaPlayerSettingsTarget.AUDIO))
        assertTrue(MediaExtension.hasConfigurablePlayerSettings(MediaPlayerSettingsTarget.BOTH))

        val audioSchema = MediaExtension.getPlayerSettingsSchema(MediaPlayerSettingsTarget.AUDIO)
        assertEquals(
            setOf("audioOutputModule", "audioOutputDeviceId", "spdifPassthrough", "networkCachingMs", "fileCachingMs"),
            audioSchema.fieldsByKey.keys
        )

        val bothSchema = MediaExtension.getPlayerSettingsSchema(MediaPlayerSettingsTarget.BOTH)
        assertEquals(
            setOf(
                "audioOutputModule",
                "audioOutputDeviceId",
                "videoRenderer",
                "hardwareDecode",
                "deinterlaceMode",
                "spdifPassthrough",
                "networkCachingMs",
                "fileCachingMs"
            ),
            bothSchema.fieldsByKey.keys
        )

        val moduleField = bothSchema.fieldsByKey["audioOutputModule"] as SelectFieldSpec
        assertTrue(moduleField.allowUnlistedSavedValue)
        val deviceField = bothSchema.fieldsByKey["audioOutputDeviceId"] as SelectFieldSpec
        assertTrue(deviceField.allowUnlistedSavedValue)

        val form = SchemaForm(
            schema = bothSchema,
            initialData = MediaExtension.getPlayerSettingsValues()
        )
        val moduleCtrl = form.findField("audioOutputModule") as SelectSchemaFieldController
        val deviceCtrl = form.findField("audioOutputDeviceId") as SelectSchemaFieldController
        assertEquals(listOf("", "mmdevice", "directsound"), moduleCtrl.selectWidget.options.map { it.value })
        assertEquals(listOf("", "mm-speakers"), deviceCtrl.selectWidget.options.map { it.value })

        moduleCtrl.value = "directsound"
        assertEquals(listOf("", "ds-primary", "ds-hdmi"), deviceCtrl.selectWidget.options.map { it.value })

        MediaExtension.applyPlayerSettingsValues(
            mapOf(
                "audioOutputModule" to "directsound",
                "audioOutputDeviceId" to "ds-hdmi",
                "videoRenderer" to "direct3d11",
                "hardwareDecode" to "d3d11va",
                "deinterlaceMode" to "yadif",
                "spdifPassthrough" to true,
                "networkCachingMs" to 2500,
                "fileCachingMs" to 600
            )
        )
        val updated = MediaExtension.vlcConfiguration
        assertEquals("directsound", updated.audioOutputModule)
        assertEquals("ds-hdmi", updated.audioOutputDeviceId)
        assertEquals(VlcVideoRendererMode.DIRECT3D11, updated.videoRenderer)
        assertEquals(VlcHardwareDecodeMode.D3D11VA, updated.hardwareDecode)
        assertEquals(VlcDeinterlaceMode.YADIF, updated.deinterlaceMode)
        assertTrue(updated.spdifPassthrough)
        assertEquals(2500, updated.networkCachingMs)
        assertEquals(600, updated.fileCachingMs)
        assertEquals(updated, recordedConfigs.last())

        val jsonRoundtrip = VlcConfigurationSchemaDescriptor.fromJson(VlcConfigurationSchemaDescriptor.toJson(updated))
        assertEquals(updated, jsonRoundtrip)
        assertNotNull(SchemaRegistry.getById("vlc_configuration"))
        assertNotNull(SchemaRegistry.getForClass<VlcConfiguration>())

        MediaExtension.resetProvidersForTest()
        val customValues = linkedMapOf<String, Any?>("decoderMode" to "low-latency")
        MediaExtension.registerPlayerSettingsProvider(object : MediaPlayerSettingsProvider {
            override fun buildSettingsSchema(target: MediaPlayerSettingsTarget): ObjectSchema = ObjectSchema(
                id = "custom_settings",
                nodes = listOf(StringFieldSpec(key = "decoderMode", label = "Decoder Mode", defaultValue = "auto"))
            )

            override fun getSettingsValues(): Map<String, Any?> = customValues
            override fun applySettingsValues(values: Map<String, Any?>) {
                customValues.putAll(values)
            }
        })
        assertTrue(MediaExtension.hasConfigurablePlayerSettings())
        assertEquals(setOf("decoderMode"), MediaExtension.getPlayerSettingsSchema().fieldsByKey.keys)
        MediaExtension.applyPlayerSettingsValues(mapOf("decoderMode" to "hardware"))
        assertEquals("hardware", MediaExtension.getPlayerSettingsValues()["decoderMode"])
    }

    @Test
    fun testVolumeSvgResourcesArePureBezierInComponentMediaRClass() {
        MediaExtension.rawDefaultStyleScss()
        val resources = listOf(
            R.drawable.ic_volume_speaker to MediaVolumeIcons.speaker,
            R.drawable.ic_volume_mute_cross to MediaVolumeIcons.muteCross,
            R.drawable.ic_volume_wave_1 to MediaVolumeIcons.wave1,
            R.drawable.ic_volume_wave_2 to MediaVolumeIcons.wave2,
            R.drawable.ic_volume_wave_3 to MediaVolumeIcons.wave3
        )
        val allowedCommands = setOf('M', 'L', 'H', 'V', 'C', 'Q', 'Z')
        val pathAttrRegex = Regex("""d\s*=\s*"([^"]+)"""")

        for ((generatedRes, exposedRes) in resources) {
            assertEquals(generatedRes, exposedRes)
            assertEquals("gui/ext/media/media/drawable/${generatedRes.name}.svg", generatedRes.canonicalRelativePath)
            assertEquals("gui_ext_media_media_drawable_${generatedRes.name}", generatedRes.flatName)
            assertEquals("genericui-ext-media", generatedRes.nativeLibraryGroupId)

            val drawable = Resources.getDrawable(generatedRes)
            assertNotNull(drawable, "Missing DrawableResource for ${generatedRes.name}")
            assertEquals("svg", drawable.format)
            assertEquals(24f, drawable.viewBoxWidth)
            assertEquals(24f, drawable.viewBoxHeight)

            val svg = drawable.svgSource
            assertNotNull(svg)
            assertFalse(svg.contains("<circle", ignoreCase = true))
            assertFalse(svg.contains("<rect", ignoreCase = true))
            assertFalse(svg.contains("<ellipse", ignoreCase = true))
            assertFalse(svg.contains("<polygon", ignoreCase = true))
            assertFalse(svg.contains("<polyline", ignoreCase = true))
            assertFalse(svg.contains("<line", ignoreCase = true))

            val paths = pathAttrRegex.findAll(svg).toList()
            assertTrue(paths.isNotEmpty())
            for (match in paths) {
                for (ch in match.groupValues[1]) {
                    if (ch.isLetter()) {
                        assertTrue(
                            ch in allowedCommands,
                            "Disallowed SVG path command '$ch' in ${generatedRes.name}"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testExpandingVolumeSliderThreeWaveAndMuteCrossTransitionsAndNoDefaultTooltip() {
        val slider = ExpandingVolumeSlider(value = 100.0, muted = false)
        assertIs<Composite>(slider)
        assertFalse((slider as Any) is AbstractContainer)
        val internalChildren = slider.mediaCompositeChildren()
        assertEquals(3, internalChildren.size)
        val muteButton = slider.internalMuteButton()
        val innerSlider = slider.internalVolumeSlider()
        val iconStack = slider.internalIconStack()
        val layers = slider.internalIconLayers()
        assertSame(muteButton, internalChildren[0])
        assertSame(innerSlider, internalChildren[1])
        assertSame(iconStack, internalChildren[2])
        assertIs<StyledSlider>(innerSlider)
        assertFalse((innerSlider as Any) is SeekBar)
        assertNull(slider.tooltipText)
        assertNull(muteButton.tooltipText)
        assertNull(innerSlider.tooltipText)

        val mediaCss = MediaExtension.defaultStyleSheet()
        assertTrue(mediaCss.rules.isNotEmpty())
        slider.applyStyleSheet(mediaCss)

        val speaker = layers[0]
        val wave1 = layers[1]
        val wave2 = layers[2]
        val wave3 = layers[3]
        val muteCross = layers[4]

        fun assertLayerTargetsAndFlushOpacity(
            speakerExpected: Float,
            wave1Expected: Float,
            wave2Expected: Float,
            wave3Expected: Float,
            muteExpected: Float
        ) {
            assertEquals(speakerExpected, speaker.computedStyle?.opacity ?: -1f, 0.001f)
            assertEquals(wave1Expected, wave1.computedStyle?.opacity ?: -1f, 0.001f)
            assertEquals(wave2Expected, wave2.computedStyle?.opacity ?: -1f, 0.001f)
            assertEquals(wave3Expected, wave3.computedStyle?.opacity ?: -1f, 0.001f)
            assertEquals(muteExpected, muteCross.computedStyle?.opacity ?: -1f, 0.001f)
            Animator.flushAll()
            assertEquals(speakerExpected, speaker.opacity, 0.001f)
            assertEquals(wave1Expected, wave1.opacity, 0.001f)
            assertEquals(wave2Expected, wave2.opacity, 0.001f)
            assertEquals(wave3Expected, wave3.opacity, 0.001f)
            assertEquals(muteExpected, muteCross.opacity, 0.001f)
        }

        assertTrue(slider.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_HIGH))
        assertLayerTargetsAndFlushOpacity(1f, 1f, 1f, 1f, 0f)

        slider.updateVolumeState(0.0, muted = false)
        assertTrue(slider.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_ZERO))
        assertFalse(slider.hasClass(ExpandingVolumeSlider.CLASS_MUTED))
        assertLayerTargetsAndFlushOpacity(1f, 0f, 0f, 0f, 0f)

        slider.updateVolumeState(20.0, muted = false)
        assertTrue(slider.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_LOW))
        assertLayerTargetsAndFlushOpacity(1f, 1f, 0f, 0f, 0f)

        slider.updateVolumeState(50.0, muted = false)
        assertTrue(slider.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_MEDIUM))
        assertLayerTargetsAndFlushOpacity(1f, 1f, 1f, 0f, 0f)

        slider.updateVolumeState(85.0, muted = false)
        assertTrue(slider.hasClass(ExpandingVolumeSlider.CLASS_VOLUME_HIGH))
        assertLayerTargetsAndFlushOpacity(1f, 1f, 1f, 1f, 0f)

        slider.isMuted = true
        assertTrue(slider.hasClass(ExpandingVolumeSlider.CLASS_MUTED))
        assertLayerTargetsAndFlushOpacity(1f, 0f, 0f, 0f, 1f)

        slider.setVolumeIcons(MediaVolumeIcons.wave3, MediaVolumeIcons.muteCross)
        assertFalse(iconStack.isVisible)
        assertEquals(MediaVolumeIcons.muteCross, muteButton.iconRes)
        slider.dispose()
    }

    @Test
    fun testYoutubeMediaOverlayHelperMountsSeekBarHoverPreviewAndVolumeSlider() {
        val overlay = YoutubePlayerOverlay(YoutubeOverlayConfig())
        var lastSeekSec = -1.0
        var lastVolumePct = -1
        var muteToggleCount = 0
        overlay.updateActions(
            OverlayActions(
                onSeekToSec = { lastSeekSec = it },
                onVolumeChange = { lastVolumePct = it },
                onMuteToggle = { muteToggleCount++ }
            )
        )

        val helper = YoutubeMediaOverlayHelper(overlay, includeVolumeSlider = true)
        assertTrue(helper.seekBar in overlay.timelineContainer.children)
        assertNotNull(helper.volumeSlider)
        assertTrue(helper.volumeSlider in overlay.leftAccessoryContainer.children)
        assertNull(helper.seekBar.tooltipText)
        assertNull(helper.volumeSlider.tooltipText)
        assertNull(helper.volumeSlider.internalMuteButton().tooltipText)

        overlay.updateState(
            OverlayState(
                positionSec = 30.0,
                durationSec = 120.0,
                bufferedRanges = listOf(OverlayBufferedRange(0.0, 60.0)),
                volumePercent = 40,
                isMuted = false
            )
        )
        assertEquals(120.0, helper.seekBar.max, 0.001)
        assertEquals(30.0, helper.seekBar.value, 0.001)
        assertEquals(60.0, helper.seekBar.bufferedValue, 0.001)
        assertEquals(40.0, helper.volumeSlider.value, 0.001)

        helper.seekBar.notifyTrackHover(0.5, isOverKnob = false)
        assertTrue(overlay.seekHoverBubble.isVisible)
        assertEquals("1:00", overlay.seekHoverLabel.text)
        assertEquals(50.0, overlay.seekHoverBubble.getAttribute("layerLeftPercent")?.toDoubleOrNull() ?: -1.0, 0.001)

        helper.seekBar.notifyTrackHover(0.0, isOverKnob = false)
        assertEquals(2.0, overlay.seekHoverBubble.getAttribute("layerLeftPercent")?.toDoubleOrNull() ?: -1.0, 0.001)
        helper.seekBar.notifyTrackHover(1.0, isOverKnob = false)
        assertEquals(98.0, overlay.seekHoverBubble.getAttribute("layerLeftPercent")?.toDoubleOrNull() ?: -1.0, 0.001)

        helper.seekBar.notifyTrackHoverExit()
        assertFalse(overlay.seekHoverBubble.isVisible)

        helper.volumeSlider.toggleMute()
        assertEquals(1, muteToggleCount)

        helper.seekBar.beginKnobDrag(0.625)
        assertEquals(75.0, lastSeekSec, 0.001)
        assertTrue(overlay.seekHoverBubble.isVisible)
        assertEquals("1:15", overlay.seekHoverLabel.text)
        helper.seekBar.endKnobDrag(commit = true)
        assertFalse(overlay.seekHoverBubble.isVisible)

        helper.dispose()
        assertFalse(helper.seekBar in overlay.timelineContainer.children)
        assertFalse(helper.volumeSlider in overlay.leftAccessoryContainer.children)

        val mobileOverlay = YoutubePlayerOverlay(YoutubeOverlayConfig())
        val mobileHelper = YoutubeMediaOverlayHelper(mobileOverlay, includeVolumeSlider = false)
        assertTrue(mobileHelper.seekBar in mobileOverlay.timelineContainer.children)
        assertNull(mobileHelper.volumeSlider)
        mobileHelper.dispose()
        mobileOverlay.dispose()
        overlay.dispose()
    }
}
