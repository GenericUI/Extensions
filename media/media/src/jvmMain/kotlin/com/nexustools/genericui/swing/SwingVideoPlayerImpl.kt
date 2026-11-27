package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.MediaHeadlessBufferPlayer
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.ext.media.swing.SwingMediaExtensionProvider
import com.nexustools.genericui.ext.media.system.SystemMediaFrameRingBuffer
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import com.nexustools.genericui.ext.media.vlc.VlcPlatformCapabilities
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.sun.jna.Pointer
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.DeinterlaceMode
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.MediaPlayerSpecs
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing [VideoPlayerNativeImpl] backed by `libvlcj` ([CallbackMediaPlayerComponent] / [MediaPlayerFactory]).
 */
class SwingVideoPlayerImpl : SwingBaseNativeImpl(
    object : JPanel(BorderLayout()) {
        override fun paint(g: Graphics?) {
            val g2d = g as? Graphics2D
            if (g2d != null) {
                val aa = getClientProperty("antialiasing") as? Boolean ?: true
                if (aa) {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                } else {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
                }
            }
            super.paint(g)
        }
    },
    "vlcj-video-player"
), VideoPlayerNativeImpl<JComponent> {
    override val nativeWidget: JPanel get() = super.nativeWidget as JPanel

    private var antialiasingEnabled: Boolean = true
    private var keepScreenOnState: Boolean = true
    private var screenWakeToken: Long = MediaScreenWakeController.allocateToken()
    @Volatile
    private var nativePlayerDisposed: Boolean = false
    override val isDisposed: Boolean
        get() = nativePlayerDisposed
    private var mediaReadyFlag: Boolean = false
    private var onMediaReadyListener: (() -> Unit)? = null
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null
    private var lastAppliedAudioOutputModule: String = ""
    private var lastAppliedAudioDeviceId: String = ""

    private val frameRingLock = Any()
    private var offscreenFrameCaptureEnabled: Boolean = false
    private var offscreenRgbaRing = SystemMediaFrameRingBuffer(width = 16, height = 16, capacity = 3)
    private val frameVersionCounter = AtomicLong(0L)
    private val presentedFrameCounter = AtomicLong(0L)
    @Volatile
    private var naturalVideoWidthCached: Int = 0
    @Volatile
    private var naturalVideoHeightCached: Int = 0
    @Volatile
    private var swingDisplayImage: BufferedImage? = null
    private var stagingIntPixels: IntArray = IntArray(0)
    private var loopEnabled: Boolean = false
    private var currentSourceUrl: String = ""
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var analyzerTapPlayer: MediaPlayer? = null
    private var analyzerLoadedUrl: String? = null
    private var headlessFallbackPlayer: MediaHeadlessBufferPlayer? = null
    private var useHeadlessFallbackForCurrentSource: Boolean = false

    private val swingVideoSurfaceComponent: JComponent = object : JComponent() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val img = swingDisplayImage ?: return
            val g2d = g as? Graphics2D ?: return
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return
            g2d.color = Color.BLACK
            g2d.fillRect(0, 0, w, h)
            val iw = img.width.coerceAtLeast(1)
            val ih = img.height.coerceAtLeast(1)
            when (resizeModeVal) {
                VideoResizeMode.STRETCH -> g2d.drawImage(img, 0, 0, w, h, null)
                VideoResizeMode.FILL -> {
                    val scale = maxOf(w.toFloat() / iw, h.toFloat() / ih)
                    val dw = (iw * scale).toInt()
                    val dh = (ih * scale).toInt()
                    g2d.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null)
                }
                VideoResizeMode.FIT -> {
                    val scale = minOf(w.toFloat() / iw, h.toFloat() / ih)
                    val dw = (iw * scale).toInt()
                    val dh = (ih * scale).toInt()
                    g2d.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null)
                }
            }
        }
    }

    private val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val w = sourceWidth.coerceAtLeast(1)
            val h = sourceHeight.coerceAtLeast(1)
            synchronized(frameRingLock) {
                naturalVideoWidthCached = w
                naturalVideoHeightCached = h
                val latest = offscreenRgbaRing.latestPresentedSlot()
                if (latest.width != w || latest.height != h) {
                    offscreenRgbaRing = SystemMediaFrameRingBuffer(width = w, height = h, capacity = 3)
                }
                if (stagingIntPixels.size != w * h) {
                    stagingIntPixels = IntArray(w * h)
                }
                val currentImg = swingDisplayImage
                if (currentImg == null || currentImg.width != w || currentImg.height != h) {
                    swingDisplayImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                }
            }
            return RV32BufferFormat(w, h)
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>?) {}
    }

    private val renderCallback = RenderCallback { mediaPlayer, nativeBuffers, bufferFormat ->
        val buf = nativeBuffers?.firstOrNull() ?: return@RenderCallback
        val w = bufferFormat.width.coerceAtLeast(1)
        val h = bufferFormat.height.coerceAtLeast(1)
        val pixelCount = w * h
        val requiredBytes = pixelCount * 4
        val dup = buf.duplicate()
        dup.rewind()
        val copyLen = minOf(requiredBytes, dup.remaining())
        if (copyLen < 4) return@RenderCallback
        val raw = ByteArray(copyLen)
        dup.get(raw)

        val frameIdx = presentedFrameCounter.incrementAndGet()
        synchronized(frameRingLock) {
            if (stagingIntPixels.size != pixelCount) {
                stagingIntPixels = IntArray(pixelCount)
            }
            val latest = offscreenRgbaRing.latestPresentedSlot()
            if (latest.width != w || latest.height != h) {
                offscreenRgbaRing = SystemMediaFrameRingBuffer(width = w, height = h, capacity = 3)
            }
            val dstSlot = offscreenRgbaRing.acquireWriteSlot()
            val dstBytes = dstSlot.bytes
            val pxLimit = minOf(pixelCount, minOf(raw.size / 4, dstBytes.size / 4))
            for (p in 0 until pxLimit) {
                val base = p shl 2
                val b = raw[base].toInt() and 0xFF
                val g = raw[base + 1].toInt() and 0xFF
                val r = raw[base + 2].toInt() and 0xFF
                dstBytes[base] = r.toByte()
                dstBytes[base + 1] = g.toByte()
                dstBytes[base + 2] = b.toByte()
                dstBytes[base + 3] = 0xFF.toByte()
                stagingIntPixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            swingDisplayImage?.let { img ->
                val dataBuffer = img.raster.dataBuffer as? DataBufferInt
                if (dataBuffer != null && dataBuffer.data.size >= pxLimit) {
                    System.arraycopy(stagingIntPixels, 0, dataBuffer.data, 0, pxLimit)
                }
            }
        }
        frameVersionCounter.incrementAndGet()
        val tsMs = (currentSecFallback * 1000.0).coerceAtLeast(0.0)
        onBeforeFramePresentListener?.invoke(frameIdx, tsMs)
        if (!offscreenFrameCaptureEnabled) {
            swingVideoSurfaceComponent.repaint()
        }
    }

    private val initialConfig: VlcConfiguration = MediaExtension.vlcConfiguration
    private val vlcjFactory: MediaPlayerFactory? = SwingMediaExtensionProvider.createMediaPlayerFactorySafely(
        VlcPlatformCapabilities.mergeLibVlcArgs(
            baseArgs = listOf(
                "--video-title=vlcj video output",
                "--no-snapshot-preview",
                "--quiet",
                "--intf=dummy"
            ),
            config = initialConfig,
            includeVideoOptions = true,
            allowWindowVoutFlag = false
        )
    )
    private val vlcjComponent: CallbackMediaPlayerComponent? = vlcjFactory?.let { factory ->
        runCatching {
            MediaPlayerSpecs.callbackMediaPlayerSpec()
                .withFactory(factory)
                .withBufferFormatCallback(bufferFormatCallback)
                .withRenderCallback(renderCallback)
                .withVideoSurfaceComponent(swingVideoSurfaceComponent)
                .callbackMediaPlayer()
        }.getOrNull()
    }

    private val vlcjPlayer: MediaPlayer?
        get() = vlcjComponent?.mediaPlayer()

    private var currentSecFallback: Double = 0.0
    private var durationSecOverride: Double? = null
    private var playingFallback: Boolean = false
    private var speedVal: Float = 1.0f
    private var volumeVal: Float = 1.0f
    private var mutedFlag: Boolean = false
    private var resizeModeVal: VideoResizeMode = VideoResizeMode.FIT
    private var bufferedRangesState: List<Pair<Double, Double>> = emptyList()
    private val subtitles = mutableListOf<SubtitleTrackInfo>()

    private var timeUpdateListener: ((Double) -> Unit)? = null
    private var durationChangedListener: ((Double) -> Unit)? = null
    private var endedListener: (() -> Unit)? = null
    private var bufferingListener: ((Boolean) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var playingStateChangedListener: ((Boolean) -> Unit)? = null
    private var bufferingStateChangedListener: ((Boolean) -> Unit)? = null
    private var bufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var tracksChangedListener: (() -> Unit)? = null

    init {
        nativeWidget.putClientProperty("antialiasing", true)
        nativeWidget.putClientProperty("keepScreenOn", true)
        SwingMediaExtensionProvider.registerVideoPlayer(this)
        if (vlcjComponent != null) {
            nativeWidget.add(vlcjComponent, BorderLayout.CENTER)
            applyVlcConfiguration(initialConfig)
            vlcjComponent.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    playingFallback = true
                    syncWakeLockState()
                    if (!mediaReadyFlag) {
                        mediaReadyFlag = true
                        onMediaReadyListener?.invoke()
                    }
                    playingStateChangedListener?.invoke(true)
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    syncWakeLockState()
                    playingStateChangedListener?.invoke(false)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    syncWakeLockState()
                    playingStateChangedListener?.invoke(false)
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    val sec = newTime / 1000.0
                    currentSecFallback = sec
                    timeUpdateListener?.invoke(sec)
                    updateBufferedHorizon(sec)
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    if (newLength > 0L && !mediaReadyFlag) {
                        mediaReadyFlag = true
                        onMediaReadyListener?.invoke()
                    }
                    if (durationSecOverride == null && newLength > 0L) {
                        durationChangedListener?.invoke(newLength / 1000.0)
                    }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    if (loopEnabled && currentSourceUrl.isNotBlank() && !nativePlayerDisposed) {
                        CompletableFuture.runAsync {
                            if (!nativePlayerDisposed && loopEnabled) {
                                vlcjPlayer?.controls()?.setTime(0L)
                                vlcjPlayer?.media()?.play(currentSourceUrl)
                            }
                        }
                        return
                    }
                    playingFallback = false
                    syncWakeLockState()
                    playingStateChangedListener?.invoke(false)
                    endedListener?.invoke()
                }

                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    val isBuf = newCache < 100f
                    bufferingListener?.invoke(isBuf)
                    bufferingStateChangedListener?.invoke(isBuf)
                    val dur = getDurationSeconds()
                    if (dur > 0.0) {
                        val endSec = dur * (newCache.coerceIn(0f, 100f) / 100.0)
                        bufferedRangesState = listOf(0.0 to endSec)
                        bufferedRangesChangedListener?.invoke(bufferedRangesState)
                    }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    playingFallback = false
                    syncWakeLockState()
                    playingStateChangedListener?.invoke(false)
                    errorListener?.invoke("vlcj playback error")
                }
            })
        }
    }

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && isPlaying()
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
    }

    internal fun applyVlcConfiguration(config: VlcConfiguration) {
        if (nativePlayerDisposed) return
        val player = vlcjPlayer ?: return
        val (nextModule, nextDeviceId) = SwingMediaExtensionProvider.applyAudioConfiguration(
            mediaPlayer = player,
            config = config,
            previousModule = lastAppliedAudioOutputModule,
            previousDeviceId = lastAppliedAudioDeviceId
        )
        lastAppliedAudioOutputModule = nextModule
        lastAppliedAudioDeviceId = nextDeviceId
        val deinterlace = config.deinterlaceMode.vlcMode?.let { mode ->
            runCatching { DeinterlaceMode.deinterlaceMode(mode) }.getOrNull()
        }
        runCatching { player.video().setDeinterlace(deinterlace) }
    }

    private fun ensureHeadlessFallbackPlayer(): MediaHeadlessBufferPlayer {
        headlessFallbackPlayer?.let { return it }
        val fallback = MediaHeadlessBufferPlayer().apply {
            setVolume(volumeVal)
            setMuted(mutedFlag)
            setPlaybackSpeed(speedVal)
            setResizeMode(resizeModeVal)
            setLoop(loopEnabled)
            setAntialiasing(antialiasingEnabled)
            setKeepScreenOn(keepScreenOnState)
            bindScreenWakeToken(screenWakeToken)
            enableOffscreenFrameCapture(offscreenFrameCaptureEnabled)
            attachAudioAnalyzer(attachedAnalyzer)
            setOnTimeUpdate { timeUpdateListener?.invoke(it) }
            setOnDurationChanged { durationChangedListener?.invoke(it) }
            setOnEnded {
                playingFallback = false
                syncWakeLockState()
                playingStateChangedListener?.invoke(false)
                endedListener?.invoke()
            }
            setOnBufferingChanged { bufferingListener?.invoke(it) }
            setOnError { errorListener?.invoke(it) }
            setOnPlayingStateChanged { playing ->
                playingFallback = playing
                syncWakeLockState()
                playingStateChangedListener?.invoke(playing)
            }
            setOnBufferingStateChanged { bufferingStateChangedListener?.invoke(it) }
            setOnBufferedRangesChanged { bufferedRangesChangedListener?.invoke(it) }
            setOnTracksChanged { tracksChangedListener?.invoke() }
            setOnMediaReady {
                mediaReadyFlag = true
                onMediaReadyListener?.invoke()
            }
            setOnBeforeFramePresent { idx, ts ->
                onBeforeFramePresentListener?.invoke(idx, ts)
            }
        }
        headlessFallbackPlayer = fallback
        return fallback
    }

    private fun ensureAnalyzerTapPlayer(): MediaPlayer? {
        if (attachedAnalyzer == null || useHeadlessFallbackForCurrentSource) return null
        analyzerTapPlayer?.let { return it }
        val factory = vlcjFactory ?: return null
        val mp = runCatching { factory.mediaPlayers().newMediaPlayer() }.getOrNull() ?: return null
        runCatching {
            mp.audio().callback("S16N", 44100, 2, object : AudioCallbackAdapter() {
                override fun play(mediaPlayer: MediaPlayer, samples: Pointer, sampleCount: Int, pts: Long) {
                    val analyzer = attachedAnalyzer ?: return
                    val byteCount = (sampleCount * 4).coerceIn(0, 65536)
                    if (byteCount > 0) {
                        val bytes = samples.getByteArray(0L, byteCount)
                        val gain = if (mutedFlag) 0f else volumeVal.coerceIn(0f, 2f)
                        analyzer.pushPcmS16Le(
                            bytes = bytes,
                            byteCount = byteCount,
                            channels = 2,
                            volumeScale = gain
                        )
                    }
                }
            })
        }
        analyzerTapPlayer = mp
        return mp
    }

    private fun syncAnalyzerTapMedia(url: String, seekOffsetSeconds: Double) {
        if (attachedAnalyzer == null || url.isBlank() || useHeadlessFallbackForCurrentSource) return
        val tapPlayer = ensureAnalyzerTapPlayer() ?: return
        val options = if (seekOffsetSeconds > 0.0) {
            arrayOf(":no-video", ":start-time=$seekOffsetSeconds")
        } else {
            arrayOf(":no-video")
        }
        runCatching { tapPlayer.media().prepare(url, *options) }
        analyzerLoadedUrl = url
    }

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        mediaReadyFlag = false
        currentSourceUrl = url
        naturalVideoWidthCached = 0
        naturalVideoHeightCached = 0
        nativeWidget.putClientProperty("videoUri", url)
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        durationSecOverride = durationOverrideSeconds
        useHeadlessFallbackForCurrentSource = url.isNotBlank() &&
            (vlcjPlayer == null || url.startsWith("data:", ignoreCase = true))

        if (useHeadlessFallbackForCurrentSource) {
            runCatching { vlcjPlayer?.controls()?.stop() }
            runCatching { analyzerTapPlayer?.controls()?.stop() }
            ensureHeadlessFallbackPlayer().setSource(url, seekOffsetSeconds, durationOverrideSeconds)
            updateBufferedHorizon(currentSecFallback)
            return
        }

        headlessFallbackPlayer?.pause()
        val config = MediaExtension.vlcConfiguration
        val options = VlcPlatformCapabilities.buildMediaOptions(
            config = config,
            seekOffsetSeconds = seekOffsetSeconds,
            includeVideoOptions = true,
            allowWindowVoutOption = false
        ).toTypedArray()
        applyVlcConfiguration(config)
        vlcjPlayer?.media()?.prepare(url, *options)
        if (attachedAnalyzer != null && url.isNotBlank()) {
            syncAnalyzerTapMedia(url, seekOffsetSeconds)
        }
        if ((vlcjPlayer == null || durationOverrideSeconds != null) && url.isNotBlank()) {
            mediaReadyFlag = true
            onMediaReadyListener?.invoke()
        }

        if (durationOverrideSeconds != null) {
            durationChangedListener?.invoke(durationOverrideSeconds)
        }
        updateBufferedHorizon(currentSecFallback)
    }

    override fun play() {
        playingFallback = true
        if (useHeadlessFallbackForCurrentSource) {
            ensureHeadlessFallbackPlayer().play()
            syncWakeLockState()
            return
        }
        vlcjPlayer?.controls()?.play()
        if (attachedAnalyzer != null && currentSourceUrl.isNotBlank()) {
            if (analyzerLoadedUrl != currentSourceUrl || analyzerTapPlayer == null) {
                syncAnalyzerTapMedia(currentSourceUrl, currentSecFallback)
            }
            runCatching { analyzerTapPlayer?.controls()?.play() }
        }
        syncWakeLockState()
        playingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        playingFallback = false
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.pause()
            syncWakeLockState()
            return
        }
        vlcjPlayer?.controls()?.setPause(true)
        runCatching { analyzerTapPlayer?.controls()?.setPause(true) }
        syncWakeLockState()
        playingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playingFallback = false
        currentSecFallback = 0.0
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.stop()
            syncWakeLockState()
            return
        }
        vlcjPlayer?.controls()?.stop()
        runCatching { analyzerTapPlayer?.controls()?.stop() }
        syncWakeLockState()
        playingStateChangedListener?.invoke(false)
        timeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        if (useHeadlessFallbackForCurrentSource) {
            ensureHeadlessFallbackPlayer().seekTo(seconds)
            return
        }
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        val targetMs = (clamped * 1000.0).toLong()
        vlcjPlayer?.controls()?.setTime(targetMs)
        runCatching { analyzerTapPlayer?.controls()?.setTime(targetMs) }
        timeUpdateListener?.invoke(clamped)
        updateBufferedHorizon(clamped)
    }

    override fun getCurrentTimeSeconds(): Double {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.getCurrentTimeSeconds() ?: currentSecFallback
        }
        val t = vlcjPlayer?.status()?.time() ?: -1L
        return if (t >= 0L) t / 1000.0 else currentSecFallback
    }

    override fun getDurationSeconds(): Double {
        durationSecOverride?.let { return it }
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.getDurationSeconds() ?: 0.0
        }
        val len = vlcjPlayer?.status()?.length() ?: -1L
        return if (len > 0L) len / 1000.0 else 0.0
    }

    override fun isPlaying(): Boolean =
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.isPlaying() ?: playingFallback
        } else {
            !nativePlayerDisposed && (playingFallback || vlcjPlayer?.status()?.isPlaying == true)
        }

    override fun setPlaybackSpeed(speed: Float) {
        speedVal = speed.coerceIn(0.1f, 8.0f)
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.setPlaybackSpeed(speedVal)
            return
        }
        vlcjPlayer?.controls()?.setRate(speedVal)
        runCatching { analyzerTapPlayer?.controls()?.setRate(speedVal) }
    }

    override fun getPlaybackSpeed(): Float =
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.getPlaybackSpeed() ?: speedVal
        } else {
            vlcjPlayer?.status()?.rate() ?: speedVal
        }

    override fun setVolume(volume: Float) {
        volumeVal = volume.coerceIn(0f, 2f)
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.setVolume(volumeVal)
            return
        }
        vlcjPlayer?.audio()?.setVolume((volumeVal * 100f).toInt())
    }

    override fun getVolume(): Float = volumeVal

    override fun setMuted(muted: Boolean) {
        mutedFlag = muted
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.setMuted(muted)
            return
        }
        vlcjPlayer?.audio()?.isMute = muted
    }

    override fun isMuted(): Boolean =
        if (useHeadlessFallbackForCurrentSource) mutedFlag else (vlcjPlayer?.audio()?.isMute ?: mutedFlag)

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeModeVal = mode
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.setResizeMode(mode)
            return
        }
        when (mode) {
            VideoResizeMode.FIT -> {
                vlcjPlayer?.video()?.setAspectRatio(null)
                vlcjPlayer?.video()?.setScale(0f)
            }
            VideoResizeMode.FILL -> {
                vlcjPlayer?.video()?.setScale(0f)
            }
            VideoResizeMode.STRETCH -> {
                val w = nativeWidget.width.coerceAtLeast(16)
                val h = nativeWidget.height.coerceAtLeast(9)
                vlcjPlayer?.video()?.setAspectRatio("$w:$h")
            }
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeModeVal

    override fun getAudioTracks(): List<MediaTrackInfo> {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.getAudioTracks().orEmpty()
        }
        val descriptions = vlcjPlayer?.audio()?.trackDescriptions()?.filter { it.id() != -1 } ?: return emptyList()
        val currentTrack = vlcjPlayer?.audio()?.track() ?: -1
        return descriptions.map { desc ->
            MediaTrackInfo(
                id = desc.id().toString(),
                label = desc.description() ?: "Track ${desc.id()}",
                language = "und",
                isSelected = desc.id() == currentTrack
            )
        }
    }

    override fun selectAudioTrack(trackId: String): Boolean {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.selectAudioTrack(trackId) ?: false
        }
        val descriptions = vlcjPlayer?.audio()?.trackDescriptions()?.filter { it.id() != -1 }.orEmpty()
        val resolvedId: Int? = if (trackId.startsWith("index:")) {
            val idx = trackId.removePrefix("index:").toIntOrNull()
            if (idx != null) descriptions.getOrNull(idx)?.id() else null
        } else {
            val id = trackId.toIntOrNull()
            if (id != null && descriptions.any { it.id() == id }) id
            else if (id != null) descriptions.getOrNull(id)?.id() ?: id
            else null
        }
        if (resolvedId != null) {
            vlcjPlayer?.audio()?.setTrack(resolvedId)
        }
        tracksChangedListener?.invoke()
        return true
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.getSubtitleTracks().orEmpty()
        }
        val currentSpu = vlcjPlayer?.subpictures()?.track() ?: -1
        val vlcSubs = vlcjPlayer?.subpictures()?.trackDescriptions()?.filter { it.id() != -1 }?.map { desc ->
            SubtitleTrackInfo(
                id = desc.id().toString(),
                label = desc.description() ?: "Subtitle ${desc.id()}",
                language = "und",
                isSelected = desc.id() == currentSpu
            )
        }.orEmpty()
        return if (vlcSubs.isNotEmpty()) vlcSubs else subtitles.toList()
    }

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        if (useHeadlessFallbackForCurrentSource) {
            ensureHeadlessFallbackPlayer().addExternalSubtitleTrack(label, language, vttOrSrtUrl)
            return
        }
        val id = "sub_${subtitles.size}"
        for (i in subtitles.indices) {
            subtitles[i] = subtitles[i].copy(isSelected = false)
        }
        subtitles.add(SubtitleTrackInfo(id, label, language, externalUrl = vttOrSrtUrl, isSelected = true))
        vlcjPlayer?.media()?.addSlave(MediaSlaveType.SUBTITLE, vttOrSrtUrl, true)
        tracksChangedListener?.invoke()
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.selectSubtitleTrack(trackId) ?: false
        }
        val indexPrefix = if (trackId != null && trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        for (i in subtitles.indices) {
            val item = subtitles[i]
            val selected = if (trackId == null) false else if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            subtitles[i] = item.copy(isSelected = selected)
        }
        val descriptions = vlcjPlayer?.subpictures()?.trackDescriptions()?.filter { it.id() != -1 }.orEmpty()
        val id = if (trackId == null) {
            -1
        } else if (indexPrefix != null) {
            descriptions.getOrNull(indexPrefix)?.id() ?: -1
        } else {
            trackId.toIntOrNull() ?: -1
        }
        vlcjPlayer?.subpictures()?.setTrack(id)
        tracksChangedListener?.invoke()
        return true
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        headlessFallbackPlayer?.attachAudioAnalyzer(analyzer)
        if (!useHeadlessFallbackForCurrentSource) {
            if (analyzer != null && currentSourceUrl.isNotBlank()) {
                syncAnalyzerTapMedia(currentSourceUrl, getCurrentTimeSeconds())
                if (isPlaying()) {
                    runCatching { analyzerTapPlayer?.controls()?.play() }
                }
            } else if (analyzer == null) {
                runCatching { analyzerTapPlayer?.controls()?.stop() }
            }
        }
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { timeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { durationChangedListener = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { endedListener = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { bufferingListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { errorListener = listener }

    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { playingStateChangedListener = listener }
    override fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) { bufferingStateChangedListener = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) { bufferedRangesChangedListener = listener }
    override fun setOnTracksChanged(listener: (() -> Unit)?) { tracksChangedListener = listener }
    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesState

    override fun setAntialiasing(antialiasing: Boolean) {
        antialiasingEnabled = antialiasing
        nativeWidget.putClientProperty("antialiasing", antialiasing)
        nativeWidget.repaint()
    }

    override fun isAntialiasing(): Boolean = antialiasingEnabled

    override fun setKeepScreenOn(keepScreenOn: Boolean) {
        keepScreenOnState = keepScreenOn
        nativeWidget.putClientProperty("keepScreenOn", keepScreenOn)
        syncWakeLockState()
    }

    override fun isKeepScreenOn(): Boolean = keepScreenOnState

    override fun bindScreenWakeToken(token: Long) {
        if (token > 0L && token != screenWakeToken) {
            val previous = screenWakeToken
            screenWakeToken = token
            MediaScreenWakeController.releaseToken(previous)
            syncWakeLockState()
        }
    }

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean =
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.isMediaReady() ?: false
        } else {
            mediaReadyFlag || frameVersionCounter.get() > 0L || getDurationSeconds() > 0.0
        }

    override fun getPresentedFrameCount(): Long =
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.getPresentedFrameCount() ?: 0L
        } else {
            presentedFrameCounter.get()
        }

    override fun setOnBeforeFramePresent(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun setLoop(loop: Boolean) {
        loopEnabled = loop
        headlessFallbackPlayer?.setLoop(loop)
    }

    override fun isLoop(): Boolean = loopEnabled

    override fun enableOffscreenFrameCapture(enabled: Boolean) {
        offscreenFrameCaptureEnabled = enabled
        headlessFallbackPlayer?.enableOffscreenFrameCapture(enabled)
    }

    override fun copyLatestVideoFrame(): ImageBuffer? {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.copyLatestVideoFrame()
        }
        if (frameVersionCounter.get() <= 0L) return null
        synchronized(frameRingLock) {
            val slot = offscreenRgbaRing.latestPresentedSlot()
            return ImageBuffer(
                width = slot.width,
                height = slot.height,
                pixelFormat = slot.pixelFormat,
                colorSpace = slot.colorSpace,
                bytes = slot.bytes.copyOf()
            )
        }
    }

    override val videoFrameVersion: Long
        get() = if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.videoFrameVersion ?: 0L
        } else {
            frameVersionCounter.get()
        }

    override fun getNaturalVideoWidth(): Int {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.getNaturalVideoWidth() ?: 0
        }
        if (naturalVideoWidthCached <= 0) {
            val dim = runCatching { vlcjPlayer?.video()?.videoDimension() }.getOrNull()
            if (dim != null && dim.width > 0 && dim.height > 0) {
                naturalVideoWidthCached = dim.width
                naturalVideoHeightCached = dim.height
            }
        }
        return naturalVideoWidthCached
    }

    override fun getNaturalVideoHeight(): Int {
        if (useHeadlessFallbackForCurrentSource) {
            return headlessFallbackPlayer?.getNaturalVideoHeight() ?: 0
        }
        if (naturalVideoHeightCached <= 0) {
            val dim = runCatching { vlcjPlayer?.video()?.videoDimension() }.getOrNull()
            if (dim != null && dim.width > 0 && dim.height > 0) {
                naturalVideoWidthCached = dim.width
                naturalVideoHeightCached = dim.height
            }
        }
        return naturalVideoHeightCached
    }

    override fun stepHeadlessPlaybackIfNeeded(deltaSeconds: Double) {
        if (useHeadlessFallbackForCurrentSource) {
            headlessFallbackPlayer?.stepHeadlessPlaybackIfNeeded(deltaSeconds)
        }
    }

    private fun updateBufferedHorizon(currentSec: Double) {
        val dur = getDurationSeconds()
        val endSec = if (dur > 0.0) minOf(dur, currentSec + 15.0) else currentSec + 15.0
        if (endSec > 0.0) {
            bufferedRangesState = listOf(0.0 to endSec)
            bufferedRangesChangedListener?.invoke(bufferedRangesState)
        }
    }

    override fun dispose() {
        nativePlayerDisposed = true
        SwingMediaExtensionProvider.unregisterVideoPlayer(this)
        playingFallback = false
        MediaScreenWakeController.releaseToken(screenWakeToken)
        headlessFallbackPlayer?.dispose()
        headlessFallbackPlayer = null
        runCatching { analyzerTapPlayer?.controls()?.stop() }
        runCatching { analyzerTapPlayer?.release() }
        analyzerTapPlayer = null
        runCatching { vlcjComponent?.release() }
        runCatching { vlcjFactory?.release() }
        super.dispose()
    }
}
