package com.nexustools.genericui.ext.media.alien

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienFactory
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.res.DataUrlCodec
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaAlienExtensionTest {

    private class TestHostNativeFactory : NativeFactory {
        override val backendTarget: BackendTarget = BackendTarget.Web
        override val backendName: String = "test-alien-media-host"
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false

        private val delegate = AlienFactory(this)

        override fun createApplication(): ApplicationNativeImpl<*> = throw UnsupportedOperationException()
        override fun createWindow(): WindowNativeImpl<*> = throw UnsupportedOperationException()
        override fun createMenuBar() = delegate.createMenuBar()
        override fun createMenu() = delegate.createMenu()
        override fun createMenuItem(checkable: Boolean, radio: Boolean) = delegate.createMenuItem(checkable, radio)
        override fun createContextMenu() = delegate.createContextMenu()
        override fun createDockWorkspace() = delegate.createDockWorkspace()
        override fun createDockArea() = delegate.createDockArea()
        override fun createDockGroup() = delegate.createDockGroup()
        override fun createDockPanel() = delegate.createDockPanel()
        override fun createDockWindow() = delegate.createDockWindow()
        override fun createToolPalette() = delegate.createToolPalette()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
            ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = "div")
        override fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> =
            ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = tagName)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
            delegate.createStyledContainer(classPrefix, tagName)
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
            delegate.createStyledComponent(classPrefix, tagName)
        override fun createBookContainer() = delegate.createBookContainer()
        override fun createScrollView() = delegate.createScrollView()
        override fun createDivider() = delegate.createDivider()
        override fun createText(tagName: String, classPrefix: String) = delegate.createText(tagName, classPrefix)
        override fun createMarkdownView() = delegate.createMarkdownView()
        override fun createImage() = delegate.createImage()
        override fun createTable() = delegate.createTable()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> =
            object : ButtonNativeImpl<Any?>, AlienImpl(classPrefix) {
                private var txt = ""
                private var icn: String? = null
                override fun setText(text: String) { txt = text }
                override fun getText(): String = txt
                override fun setIcon(icon: String?) { icn = icon }
                override fun getIcon(): String? = icn
            }
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
            delegate.createStyledButton(classPrefix)
        override fun createDropdown() = delegate.createDropdown()
        override fun createTextInput(multiline: Boolean) = delegate.createTextInput(multiline)
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String) =
            delegate.createStyledTextInput(multiline, classPrefix)
        override fun createSelect() = delegate.createSelect()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean) =
            delegate.createCheckbox(switchStyle, radioStyle)
        override fun createRangeSlider() = delegate.createRangeSlider()
        override fun createTab(closable: Boolean) = delegate.createTab(closable)
        override fun createTabContainer() = delegate.createTabContainer()
        override fun createNavbar() = delegate.createNavbar()
        override fun createBottomNavigationBar() = delegate.createBottomNavigationBar()
        override fun createProgressBar() = delegate.createProgressBar()
        override fun createSpinner() = delegate.createSpinner()
        override fun createPopover() = delegate.createPopover()
        override fun createDialogWindow() = delegate.createDialogWindow()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = throw UnsupportedOperationException()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = throw UnsupportedOperationException()
        override fun createLazyList() = delegate.createLazyList()
        override fun createLazyGrid() = delegate.createLazyGrid()
        override fun createTreeView() = delegate.createTreeView()
        override fun createPermissionManager(): PermissionManagerNativeImpl = throw UnsupportedOperationException()
        override fun createSystemTray(): SystemTrayNativeImpl = throw UnsupportedOperationException()
        override fun createNotificationManager(): NotificationManagerNativeImpl = throw UnsupportedOperationException()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = throw UnsupportedOperationException()
    }

    private lateinit var hostFactory: TestHostNativeFactory

    @BeforeTest
    fun setUp() {
        AlienExtension.clearAlienExtensionImplsForTests()
        hostFactory = TestHostNativeFactory()
        GenericUI.initialize(hostFactory, "MediaAlienExtensionTestApp")
    }

    @AfterTest
    fun tearDown() {
        MediaAlienExtension.unregister()
        AlienExtension.clearAlienExtensionImplsForTests()
    }

    @Test
    fun testMediaAlienExtensionRegistrationAndFactoryResolution() {
        MediaAlienExtension.register()
        MediaAlienExtension.register()
        assertTrue(MediaAlienExtension.isRegistered)

        val session = AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = 400,
            initialHeight = 300,
            offscreenOnly = true
        )

        val player = object : VideoPlayer() {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        player.ensureNativeImpl()
        val impl = assertIs<VideoPlayerAlienImpl>(player.nativeImpl)
        assertEquals("video", impl.node.tagName)
        assertEquals("video-player", impl.node.componentClassPrefix)
        assertTrue(session.underlaySlots.isEmpty(), "VideoPlayer in Alien must not create an OS underlay slot")
        assertEquals(Size(300, 150), impl.node.customMeasure!!(1000, 1000, impl.node.resolveVisualStyle()))

        // Nested container propagation also creates VideoPlayerAlienImpl
        val container = object : Container("col") {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        val nestedPlayer = VideoPlayer()
        container.addChild(nestedPlayer)
        container.ensureNativeImpl()
        assertIs<VideoPlayerAlienImpl>(nestedPlayer.nativeImpl)

        // Unregister cleanly stops matching VideoPlayer
        MediaAlienExtension.unregister()
        assertFalse(MediaAlienExtension.isRegistered)
        assertNull(session.alienFactory.createExtensionNativeImpl(VideoPlayer()))

        // Re-register cleanly works again
        MediaAlienExtension.register()
        assertTrue(MediaAlienExtension.isRegistered)
        assertIs<VideoPlayerAlienImpl>(session.alienFactory.createExtensionNativeImpl(VideoPlayer()))

        player.dispose()
        container.dispose()
    }

    @Test
    fun testMp4DataUriPlaybackSeekFrameSteppingAndOffscreenRender() {
        MediaAlienExtension.register()

        val session = AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = 320,
            initialHeight = 200,
            offscreenOnly = true
        )
        GenericUI.initialize(session.alienFactory, "MediaAlienMp4Test")

        val mp4DataUrl = DataUrlCodec.encodeBytes(
            bytes = buildSyntheticIsoBmffMp4(width = 320, height = 180, durationMs = 12_000),
            mimeType = "video/mp4"
        )
        val vttDataUrl = DataUrlCodec.encodeUtf8Text(
            text = "WEBVTT\n\n00:00:01.000 --> 00:00:04.000\nAlien Video Subtitle\n",
            mimeType = "text/vtt"
        )

        val player = object : VideoPlayer() {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        player.ensureNativeImpl()
        val impl = assertIs<VideoPlayerAlienImpl>(player.nativeImpl)
        session.rootNode.addChild(impl.node)

        player.setSource(mp4DataUrl)
        player.addExternalSubtitleTrack("English", "en", vttDataUrl)

        assertTrue(player.durationSeconds in 11.5..12.5, "Expected MP4 duration ~12s, got ${player.durationSeconds}")
        assertEquals(320, player.naturalWidth)
        assertEquals(180, player.naturalHeight)
        assertTrue(player.isMediaReady)
        assertEquals(320f, impl.node.intrinsicWidth)
        assertEquals(180f, impl.node.intrinsicHeight)
        assertEquals("Alien Video Subtitle", player.getActiveSubtitleCueText(2.0))

        val initialBuffer = session.renderFrameToBuffer(16.67f)
        assertTrue(session.underlaySlots.isEmpty())
        assertTrue(impl.videoFrameVersion >= 1L)
        assertTrue(hasNonZeroOpaquePixels(initialBuffer, 320, 180), "Expected non-zero opaque rendered MP4 frame pixels")

        val initialSnapshot = initialBuffer.bytes.copyOf()

        player.playbackSpeed = 2.0f
        player.volume = 0.65f
        player.isMuted = true
        player.loop = true
        player.resizeMode = VideoResizeMode.STRETCH
        player.play()
        assertTrue(player.isPlaying)
        assertTrue(impl.node.hasContinuousAnimation)

        var latestBuffer = initialBuffer
        for (step in 0 until 8) {
            latestBuffer = session.renderFrameToBuffer(50f)
        }
        assertTrue(player.currentTimeSeconds > 0.0, "Expected stepped playback time > 0, got ${player.currentTimeSeconds}")
        assertTrue(player.videoFrameVersion > 1L)
        assertFalse(
            initialSnapshot.contentEquals(latestBuffer.bytes),
            "Expected offscreen framebuffer pixels to advance as video frames step"
        )

        player.pause()
        assertFalse(player.isPlaying)
        assertFalse(impl.node.hasContinuousAnimation)

        player.seekTo(6.0)
        assertTrue(player.currentTimeSeconds in 5.9..6.1, "Expected seekTo(6.0), got ${player.currentTimeSeconds}")
        val afterSeekBuf = session.renderFrameToBuffer(16.67f)
        assertTrue(hasNonZeroOpaquePixels(afterSeekBuf, 320, 180))
        assertEquals(0.65f, player.volume)
        assertTrue(player.isMuted)
        assertTrue(player.loop)

        player.isMuted = false
        assertFalse(player.isMuted)
        player.dispose()
    }

    @Test
    fun testWavDataUriDrivesAudioAnalyzerInsideAlienSession() {
        MediaAlienExtension.register()

        val session = AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = 300,
            initialHeight = 150,
            offscreenOnly = true
        )
        GenericUI.initialize(session.alienFactory, "MediaAlienWavTest")

        val wavBytes = buildSineWavPcm16(sampleRate = 44100, durationSeconds = 0.6, freqHz = 440.0)
        val wavDataUrl = DataUrlCodec.encodeBytes(wavBytes, "audio/wav")

        val analyzer = AudioAnalyzer(AudioAnalyzerConfig(fftSize = AudioFftSize.FFT_1024))
        val player = object : VideoPlayer() {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        player.ensureNativeImpl()
        val impl = assertIs<VideoPlayerAlienImpl>(player.nativeImpl)
        session.rootNode.addChild(impl.node)

        player.attachAudioAnalyzer(analyzer)
        player.setSource(wavDataUrl)

        assertTrue(player.durationSeconds in 0.58..0.62, "Expected WAV duration ~0.6s, got ${player.durationSeconds}")
        assertTrue(player.getAudioTracks().isNotEmpty(), "Expected WAV PCM audio track metadata")

        player.volume = 1.0f
        player.isMuted = false
        player.play()
        assertTrue(player.isPlaying)

        for (step in 0 until 6) {
            session.renderFrameToBuffer(20f)
        }

        val freqBins = ByteArray(analyzer.frequencyBinCount)
        analyzer.getByteFrequencyData(freqBins)
        val maxBin = freqBins.maxOfOrNull { it.toInt() and 0xFF } ?: 0
        val bands = analyzer.computeEnergyBands()
        assertTrue(maxBin > 0, "Expected non-zero AudioAnalyzer FFT magnitude from decoded WAV PCM samples")
        assertTrue(bands.lowMid > 0f || bands.bass > 0f || bands.mid > 0f)

        player.isMuted = true
        for (step in 0 until 8) {
            session.renderFrameToBuffer(20f)
        }
        player.pause()
        analyzer.dispose()
        player.dispose()
    }

    private fun hasNonZeroOpaquePixels(buffer: com.nexustools.genericui.image.ImageBuffer, scanW: Int, scanH: Int): Boolean {
        val maxX = minOf(scanW, buffer.width)
        val maxY = minOf(scanH, buffer.height)
        for (y in 4 until maxY step 8) {
            for (x in 4 until maxX step 8) {
                val px = buffer.getPixel(x, y)
                if (px.a == 255 && (px.r > 15 || px.g > 15 || px.b > 15)) {
                    return true
                }
            }
        }
        return false
    }

    private fun buildSineWavPcm16(sampleRate: Int, durationSeconds: Double, freqHz: Double): ByteArray {
        val sampleCount = (sampleRate * durationSeconds).roundToInt().coerceAtLeast(1)
        val dataBytes = sampleCount * 2
        val totalSize = 44 + dataBytes
        val out = ByteArray(totalSize)
        writeAscii(out, 0, "RIFF")
        writeInt32Le(out, 4, totalSize - 8)
        writeAscii(out, 8, "WAVE")
        writeAscii(out, 12, "fmt ")
        writeInt32Le(out, 16, 16)
        writeInt16Le(out, 20, 1) // PCM
        writeInt16Le(out, 22, 1) // mono
        writeInt32Le(out, 24, sampleRate)
        writeInt32Le(out, 28, sampleRate * 2)
        writeInt16Le(out, 32, 2)
        writeInt16Le(out, 34, 16)
        writeAscii(out, 36, "data")
        writeInt32Le(out, 40, dataBytes)
        for (i in 0 until sampleCount) {
            val angle = 2.0 * PI * freqHz * (i.toDouble() / sampleRate.toDouble())
            val sample = (sin(angle) * 24000.0).roundToInt().coerceIn(-32767, 32767)
            writeInt16Le(out, 44 + i * 2, sample)
        }
        return out
    }

    private fun buildSyntheticIsoBmffMp4(width: Int, height: Int, durationMs: Int): ByteArray {
        val ftyp = box("ftyp", ByteArray(16).apply {
            writeAscii(this, 0, "isom")
            writeInt32Be(this, 4, 512)
            writeAscii(this, 8, "isom")
            writeAscii(this, 12, "avc1")
        })
        val mvhd = box("mvhd", ByteArray(100).apply {
            writeInt32Be(this, 12, 1000) // timescale
            writeInt32Be(this, 16, durationMs)
            writeInt32Be(this, 20, 0x00010000) // rate 1.0
            writeInt16Be(this, 24, 0x0100) // volume 1.0
            writeInt32Be(this, 36, 0x00010000)
            writeInt32Be(this, 52, 0x00010000)
            writeInt32Be(this, 68, 0x40000000)
            writeInt32Be(this, 96, 2) // next track ID
        })
        val tkhd = box("tkhd", ByteArray(84).apply {
            this[3] = 3
            writeInt32Be(this, 12, 1) // track ID
            writeInt32Be(this, 20, durationMs)
            writeInt32Be(this, 40, 0x00010000)
            writeInt32Be(this, 56, 0x00010000)
            writeInt32Be(this, 72, 0x40000000)
            writeInt32Be(this, 76, width shl 16)
            writeInt32Be(this, 80, height shl 16)
        })
        val mdhd = box("mdhd", ByteArray(24).apply {
            writeInt32Be(this, 12, 1000)
            writeInt32Be(this, 16, durationMs)
            writeInt16Be(this, 20, 0x55C4) // und
        })
        val hdlr = box("hdlr", ByteArray(25).apply {
            writeAscii(this, 8, "vide")
        })
        val avc1 = box("avc1", ByteArray(78).apply {
            writeInt16Be(this, 6, 1)
            writeInt16Be(this, 24, width)
            writeInt16Be(this, 26, height)
            writeInt32Be(this, 28, 0x00480000)
            writeInt32Be(this, 32, 0x00480000)
            writeInt16Be(this, 40, 1)
            writeInt16Be(this, 74, 24)
            writeInt16Be(this, 76, 0xFFFF)
        })
        val stsd = box("stsd", ByteArray(8 + avc1.size).apply {
            writeInt32Be(this, 4, 1)
            avc1.copyInto(this, 8)
        })
        val stsz = box("stsz", ByteArray(12).apply {
            writeInt32Be(this, 8, 360)
        })
        val stbl = box("stbl", stsd + stsz)
        val minf = box("minf", stbl)
        val mdia = box("mdia", mdhd + hdlr + minf)
        val trak = box("trak", tkhd + mdia)
        val moov = box("moov", mvhd + trak)
        val mdat = box("mdat", ByteArray(16))
        return ftyp + moov + mdat
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        writeInt32Be(out, 0, out.size)
        writeAscii(out, 4, type)
        payload.copyInto(out, 8)
        return out
    }

    private fun writeAscii(dst: ByteArray, offset: Int, text: String) {
        for (i in text.indices) {
            dst[offset + i] = text[i].code.toByte()
        }
    }

    private fun writeInt16Le(dst: ByteArray, offset: Int, v: Int) {
        dst[offset] = (v and 0xFF).toByte()
        dst[offset + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun writeInt32Le(dst: ByteArray, offset: Int, v: Int) {
        dst[offset] = (v and 0xFF).toByte()
        dst[offset + 1] = ((v ushr 8) and 0xFF).toByte()
        dst[offset + 2] = ((v ushr 16) and 0xFF).toByte()
        dst[offset + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun writeInt16Be(dst: ByteArray, offset: Int, v: Int) {
        dst[offset] = ((v ushr 8) and 0xFF).toByte()
        dst[offset + 1] = (v and 0xFF).toByte()
    }

    private fun writeInt32Be(dst: ByteArray, offset: Int, v: Int) {
        dst[offset] = ((v ushr 24) and 0xFF).toByte()
        dst[offset + 1] = ((v ushr 16) and 0xFF).toByte()
        dst[offset + 2] = ((v ushr 8) and 0xFF).toByte()
        dst[offset + 3] = (v and 0xFF).toByte()
    }
}
