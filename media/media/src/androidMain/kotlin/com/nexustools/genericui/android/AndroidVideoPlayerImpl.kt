package com.nexustools.genericui.android

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.ext.media.system.SystemMediaFrameRingBuffer
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Android `android.view.TextureView` + `android.media.MediaPlayer` + `Visualizer`
 * implementation of [VideoPlayerNativeImpl].
 */
class AndroidVideoPlayerImpl : AndroidBaseNativeImpl("android.view.TextureView", "video-player"), VideoPlayerNativeImpl<AndroidViewHandle> {
    private var sourceUrl: String = ""
    private var currentPositionSeconds: Double = 0.0
    private var durationSeconds: Double = 0.0
    private var playing: Boolean = false
    private var volume: Float = 1f
    private var muted: Boolean = false
    private var playbackSpeed: Float = 1f
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private var loopEnabled: Boolean = false
    private var antialiasingEnabled: Boolean = true
    private var keepScreenOnState: Boolean = true
    private var screenWakeToken: Long = MediaScreenWakeController.allocateToken()

    private val frameRingLock = Any()
    @Volatile
    private var offscreenFrameCaptureEnabled: Boolean = false
    private var offscreenRgbaRing = SystemMediaFrameRingBuffer(width = 16, height = 16, capacity = 3)
    private val frameVersionCounter = AtomicLong(0L)
    private val presentedFrameCounter = AtomicLong(0L)
    private var captureBitmap: Bitmap? = null
    private var captureArgbPixels: IntArray = IntArray(0)
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null

    @Volatile
    private var nativePlayerDisposed: Boolean = false
    private var mediaReadyFlag: Boolean = false
    private var isPreparedFlag: Boolean = false
    private var isPreparingFlag: Boolean = false
    private var hasDurationOverride: Boolean = false
    private var sourceGeneration: Long = 0L
    private val positionPollHandler: Handler? by lazy {
        runCatching { Looper.getMainLooper()?.let { Handler(it) } }.getOrNull()
    }
    private val positionPollRunnable = object : Runnable {
        override fun run() {
            if (nativePlayerDisposed || !playing) return
            val mp = activeMediaPlayer ?: return
            if (isPreparedFlag) {
                val durMs = runCatching { mp.duration }.getOrDefault(0)
                if (durMs > 0) {
                    val newDur = durMs / 1000.0
                    if (durationSeconds <= 0.0 || kotlin.math.abs(newDur - durationSeconds) >= 0.5) {
                        durationSeconds = newDur
                        dispatchPlayerCallback { onDurationChanged?.invoke(newDur) }
                    }
                }
                val posMs = runCatching { mp.currentPosition }.getOrDefault(-1)
                if (posMs >= 0) {
                    val posSec = posMs / 1000.0
                    currentPositionSeconds = posSec
                    dispatchPlayerCallback { onTimeUpdate?.invoke(posSec) }
                }
            }
            if (!nativePlayerDisposed && playing) {
                positionPollHandler?.postDelayed(this, 250L)
            }
        }
    }

    private fun startPositionPolling() {
        val h = positionPollHandler ?: return
        h.removeCallbacks(positionPollRunnable)
        if (!nativePlayerDisposed && playing) {
            h.postDelayed(positionPollRunnable, 250L)
        }
    }

    private fun stopPositionPolling() {
        positionPollHandler?.removeCallbacks(positionPollRunnable)
    }

    private fun dispatchPlayerCallback(block: () -> Unit) {
        if (GenericUI.activeFactory != null &&
            GenericUiThreading.isSplitThreadingEnabled &&
            !GenericUiThreading.isOnLogicThread()
        ) {
            GenericUiThreading.runOnLogic(block)
        } else {
            block()
        }
    }
    private var bufferedRangesState: List<Pair<Double, Double>> = emptyList()
    private val audioTracks = mutableListOf<MediaTrackInfo>()
    private val subtitles = mutableListOf<SubtitleTrackInfo>()
    private var activeSubtitleTrackId: String? = null
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeProgressiveDataSource: AndroidProgressiveHttpMediaDataSource? = null
    private var visualizer: Visualizer? = null
    private var textureView: TextureView? = null
    private var retainedSurfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null
    private var pendingSourceUrl: String = ""
    private var videoWidthPx: Int = 0
    private var videoHeightPx: Int = 0

    fun hasRetainedSurfaceTexture(): Boolean =
        retainedSurfaceTexture != null &&
            !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && retainedSurfaceTexture?.isReleased == true)

    fun getTextureView(): TextureView? = textureView

    private fun isSurfaceTextureUsable(st: SurfaceTexture?): Boolean =
        st != null && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && st.isReleased)

    private var onTimeUpdate: ((Double) -> Unit)? = null
    private var onDurationChanged: ((Double) -> Unit)? = null
    private var onEnded: (() -> Unit)? = null
    private var onBufferingChanged: ((Boolean) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onPlayingStateChanged: ((Boolean) -> Unit)? = null
    private var onBufferingStateChanged: ((Boolean) -> Unit)? = null
    private var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onTracksChanged: (() -> Unit)? = null
    private var onMediaReadyListener: (() -> Unit)? = null

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    private fun computeVideoBounds(innerW: Int, innerH: Int): Pair<Int, Int> {
        if (innerW <= 0 || innerH <= 0) return 0 to 0
        val vw = videoWidthPx
        val vh = videoHeightPx
        if (vw <= 0 || vh <= 0) {
            return if (sourceUrl.isBlank()) (0 to 0) else (innerW to innerH)
        }
        return when (resizeMode) {
            VideoResizeMode.STRETCH -> innerW to innerH
            VideoResizeMode.FIT -> {
                val scale = minOf(innerW.toFloat() / vw.toFloat(), innerH.toFloat() / vh.toFloat())
                (vw * scale).roundToInt().coerceIn(0, innerW) to
                    (vh * scale).roundToInt().coerceIn(0, innerH)
            }
            VideoResizeMode.FILL -> {
                val scale = maxOf(innerW.toFloat() / vw.toFloat(), innerH.toFloat() / vh.toFloat())
                (vw * scale).roundToInt().coerceAtLeast(innerW) to
                    (vh * scale).roundToInt().coerceAtLeast(innerH)
            }
        }
    }

    override fun createDefaultAndroidView(context: Context): View? {
        val tv = TextureView(context).apply {
            isOpaque = false
            visibility = if (sourceUrl.isBlank()) View.INVISIBLE else View.VISIBLE
        }
        val saved = retainedSurfaceTexture
        if (isSurfaceTextureUsable(saved) && saved != null) {
            runCatching {
                if (tv.surfaceTexture !== saved) {
                    tv.setSurfaceTexture(saved)
                }
            }
        }
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                val prev = retainedSurfaceTexture
                if (isSurfaceTextureUsable(prev) && prev != null && prev !== st) {
                    val restored = runCatching {
                        tv.setSurfaceTexture(prev)
                        true
                    }.getOrDefault(false)
                    if (restored) {
                        if (videoSurface == null || videoSurface?.isValid != true) {
                            runCatching { videoSurface?.release() }
                            videoSurface = Surface(prev)
                        }
                        activeMediaPlayer?.let { mp ->
                            runCatching { mp.setSurface(videoSurface) }
                        }
                    } else {
                        retainedSurfaceTexture = st
                        runCatching { videoSurface?.release() }
                        videoSurface = Surface(st)
                        activeMediaPlayer?.let { mp ->
                            runCatching { mp.setSurface(videoSurface) }
                        }
                    }
                } else {
                    retainedSurfaceTexture = st
                    if (videoSurface == null || videoSurface?.isValid != true) {
                        runCatching { videoSurface?.release() }
                        videoSurface = Surface(st)
                    }
                    activeMediaPlayer?.let { mp ->
                        runCatching { mp.setSurface(videoSurface) }
                    }
                }
                val urlToOpen = pendingSourceUrl.ifBlank { sourceUrl }
                if (!nativePlayerDisposed &&
                    urlToOpen.isNotBlank() &&
                    !urlToOpen.startsWith("synth://") &&
                    activeMediaPlayer == null &&
                    !isPreparingFlag
                ) {
                    openMediaPlayerOnMainThread(tv.context, urlToOpen, sourceGeneration)
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                if (nativePlayerDisposed || sourceUrl.isBlank()) {
                    releaseMediaPlayerOnly()
                    runCatching { videoSurface?.release() }
                    videoSurface = null
                    retainedSurfaceTexture = null
                    return true
                }
                retainedSurfaceTexture = st
                return false
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                textureView?.let { captureTextureViewFrame(it) }
            }
        }
        textureView = tv
        val container = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val wMode = MeasureSpec.getMode(widthMeasureSpec)
                val wSize = MeasureSpec.getSize(widthMeasureSpec)
                val hMode = MeasureSpec.getMode(heightMeasureSpec)
                val hSize = MeasureSpec.getSize(heightMeasureSpec)
                val padH = paddingLeft + paddingRight
                val padV = paddingTop + paddingBottom

                val resolvedW = when (wMode) {
                    MeasureSpec.EXACTLY -> wSize
                    MeasureSpec.AT_MOST -> wSize
                    else -> if (videoWidthPx > 0) videoWidthPx + padH else padH
                }
                val aspectH = if (videoWidthPx > 0 && videoHeightPx > 0) {
                    ((resolvedW - padH).coerceAtLeast(0) * (videoHeightPx.toFloat() / videoWidthPx.toFloat())).roundToInt() + padV
                } else {
                    ((resolvedW - padH).coerceAtLeast(0) * (9f / 16f)).roundToInt() + padV
                }
                val resolvedH = when (hMode) {
                    MeasureSpec.EXACTLY -> hSize
                    MeasureSpec.AT_MOST -> aspectH.coerceAtMost(hSize)
                    else -> aspectH
                }

                val innerW = (resolvedW - padH).coerceAtLeast(0)
                val innerH = (resolvedH - padV).coerceAtLeast(0)
                val (targetW, targetH) = computeVideoBounds(innerW, innerH)
                tv.measure(
                    MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY)
                )
                setMeasuredDimension(
                    resolveSize(resolvedW, widthMeasureSpec),
                    resolveSize(resolvedH, heightMeasureSpec)
                )
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val innerW = ((right - left) - paddingLeft - paddingRight).coerceAtLeast(0)
                val innerH = ((bottom - top) - paddingTop - paddingBottom).coerceAtLeast(0)
                val (targetW, targetH) = computeVideoBounds(innerW, innerH)
                val childLeft = paddingLeft + ((innerW - targetW) / 2)
                val childTop = paddingTop + ((innerH - targetH) / 2)
                tv.layout(childLeft, childTop, childLeft + targetW, childTop + targetH)
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            clipToPadding = true
            clipToOutline = true
        }
        container.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        return container
    }

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        nativeWidget.attributes["keepScreenOn"] = "true"
    }

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && playing
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
        AndroidViewHandle.runOnMainThread {
            runCatching {
                nativeWidget.androidView?.keepScreenOn = active
                textureView?.keepScreenOn = active
                activeMediaPlayer?.setScreenOnWhilePlaying(active)
            }
        }
    }

    private fun applyVolumeAndSpeedToMediaPlayer(mp: MediaPlayer?) {
        val player = mp ?: return
        val effectiveVol = if (muted) 0f else volume
        runCatching { player.setVolume(effectiveVol, effectiveVol) }
        if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isAlreadyPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            if (isAlreadyPlaying || playbackSpeed != 1f) {
                runCatching {
                    val params = player.playbackParams
                    params.speed = playbackSpeed
                    player.playbackParams = params
                }
            }
        }
    }

    private fun attachVisualizerIfPossible() {
        val activeAnalyzer = attachedAnalyzer ?: run {
            releaseVisualizer()
            return
        }
        val ctx = textureView?.context ?: AndroidViewHandle.activeContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ctx != null &&
            ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            releaseVisualizer()
            return
        }
        val mp = activeMediaPlayer ?: return
        runCatching {
            val sessionId = mp.audioSessionId
            if (sessionId <= 0) return@runCatching
            releaseVisualizer()
            val viz = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            viz.captureSize = activeAnalyzer.fftSize.coerceIn(range[0], range[1])
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        val wf = waveform ?: return
                        if (wf.isEmpty()) return
                        val floats = FloatArray(wf.size) { i ->
                            val u = wf[i].toInt() and 0xFF
                            ((u - 128) / 128f).coerceIn(-1f, 1f)
                        }
                        attachedAnalyzer?.pushPcmSamples(floats, channels = 1)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {}
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            viz.enabled = true
            visualizer = viz
        }
    }

    private fun releaseVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }

    private fun releaseMediaPlayerOnly() {
        stopPositionPolling()
        releaseVisualizer()
        val oldDs = activeProgressiveDataSource
        activeProgressiveDataSource = null
        oldDs?.signalAbort()
        val old = activeMediaPlayer
        activeMediaPlayer = null
        isPreparedFlag = false
        isPreparingFlag = false
        if (old != null) {
            runCatching { old.setOnPreparedListener(null) }
            runCatching { old.setOnCompletionListener(null) }
            runCatching { old.setOnErrorListener(null) }
            runCatching { old.setOnInfoListener(null) }
            runCatching { old.setOnVideoSizeChangedListener(null) }
            runCatching { old.reset() }
            runCatching { old.release() }
        }
        if (oldDs != null) {
            Thread { runCatching { oldDs.close() } }.apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun seekMediaPlayerMs(mp: MediaPlayer, targetMs: Long) {
        val clampedMs = targetMs.coerceAtLeast(0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                mp.seekTo(clampedMs, MediaPlayer.SEEK_CLOSEST_SYNC)
            }.onFailure {
                runCatching { mp.seekTo(clampedMs.toInt()) }
            }
        } else {
            runCatching { mp.seekTo(clampedMs.toInt()) }
        }
    }

    private fun openMediaPlayerOnMainThread(
        context: Context,
        url: String,
        generation: Long,
        forceProgressiveHttp: Boolean = false
    ) {
        if (nativePlayerDisposed || url.isBlank() || url.startsWith("synth://") || generation != sourceGeneration) {
            return
        }
        val tv = textureView
        if (videoSurface == null || videoSurface?.isValid != true) {
            val st = retainedSurfaceTexture?.takeIf { isSurfaceTextureUsable(it) }
                ?: tv?.surfaceTexture?.takeIf { isSurfaceTextureUsable(it) }
            if (st == null) {
                pendingSourceUrl = url
                return
            }
            retainedSurfaceTexture = st
            runCatching { videoSurface?.release() }
            videoSurface = Surface(st)
        }

        releaseMediaPlayerOnly()
        val mp = MediaPlayer()
        activeMediaPlayer = mp
        isPreparingFlag = true
        pendingSourceUrl = ""
        try {
            val isHttp = url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
            if (isHttp && (forceProgressiveHttp || AndroidProgressiveHttpMediaDataSource.isLiveOrTranscodeHttpUrl(url))) {
                val ds = AndroidProgressiveHttpMediaDataSource(context.cacheDir, url)
                activeProgressiveDataSource = ds
                mp.setDataSource(ds)
            } else if (isHttp ||
                url.startsWith("rtsp://", ignoreCase = true) ||
                url.startsWith("/")
            ) {
                mp.setDataSource(url)
            } else {
                mp.setDataSource(context, Uri.parse(url))
            }
            mp.setSurface(videoSurface)
            mp.isLooping = loopEnabled
            mp.setScreenOnWhilePlaying(keepScreenOnState && playing)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0 && (w != videoWidthPx || h != videoHeightPx)) {
                    videoWidthPx = w
                    videoHeightPx = h
                    AndroidViewHandle.runOnMainThread {
                        nativeWidget.androidView?.requestLayout()
                    }
                }
            }
            mp.setOnPreparedListener { preparedMp ->
                if (nativePlayerDisposed || generation != sourceGeneration || activeMediaPlayer !== preparedMp) {
                    return@setOnPreparedListener
                }
                isPreparingFlag = false
                isPreparedFlag = true
                videoWidthPx = runCatching { preparedMp.videoWidth }.getOrDefault(0)
                videoHeightPx = runCatching { preparedMp.videoHeight }.getOrDefault(0)
                nativeWidget.androidView?.requestLayout()
                val durMs = runCatching { preparedMp.duration }.getOrDefault(0)
                if (durMs > 0 && !hasDurationOverride) {
                    durationSeconds = durMs / 1000.0
                    val reportedDur = durationSeconds
                    dispatchPlayerCallback { onDurationChanged?.invoke(reportedDur) }
                }
                val targetMs = (currentPositionSeconds * 1000.0).roundToLong().coerceAtLeast(0L)
                if (targetMs > 0L && activeProgressiveDataSource == null) {
                    seekMediaPlayerMs(preparedMp, targetMs)
                }
                extractTrackInfo(preparedMp)
                attachVisualizerIfPossible()
                mediaReadyFlag = true
                dispatchPlayerCallback { onMediaReadyListener?.invoke() }
                if (playing) {
                    runCatching { preparedMp.start() }
                    applyVolumeAndSpeedToMediaPlayer(preparedMp)
                    startPositionPolling()
                } else {
                    applyVolumeAndSpeedToMediaPlayer(preparedMp)
                    if (runCatching { preparedMp.isPlaying }.getOrDefault(false)) {
                        runCatching { preparedMp.pause() }
                    }
                    stopPositionPolling()
                }
            }
            mp.setOnCompletionListener { completedMp ->
                if (activeMediaPlayer !== completedMp) return@setOnCompletionListener
                if (loopEnabled) return@setOnCompletionListener
                playing = false
                stopPositionPolling()
                syncWakeLockState()
                dispatchPlayerCallback {
                    onPlayingStateChanged?.invoke(false)
                    onEnded?.invoke()
                }
            }
            mp.setOnErrorListener { erroredMp, what, extra ->
                if (activeMediaPlayer !== erroredMp) return@setOnErrorListener true
                if (!forceProgressiveHttp &&
                    activeProgressiveDataSource == null &&
                    isHttp &&
                    !nativePlayerDisposed &&
                    generation == sourceGeneration
                ) {
                    openMediaPlayerOnMainThread(context, url, generation, forceProgressiveHttp = true)
                    return@setOnErrorListener true
                }
                playing = false
                isPreparedFlag = false
                isPreparingFlag = false
                stopPositionPolling()
                syncWakeLockState()
                dispatchPlayerCallback {
                    onPlayingStateChanged?.invoke(false)
                    onError?.invoke("Android TextureView MediaPlayer error ($what, $extra)")
                }
                true
            }
            mp.setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        dispatchPlayerCallback {
                            onBufferingChanged?.invoke(true)
                            onBufferingStateChanged?.invoke(true)
                        }
                    }
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        dispatchPlayerCallback {
                            onBufferingChanged?.invoke(false)
                            onBufferingStateChanged?.invoke(false)
                        }
                    }
                }
                false
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            isPreparingFlag = false
            isPreparedFlag = false
            playing = false
            activeProgressiveDataSource?.let { ds ->
                activeProgressiveDataSource = null
                ds.signalAbort()
                Thread { runCatching { ds.close() } }.apply { isDaemon = true }.start()
            }
            if (activeMediaPlayer === mp) {
                activeMediaPlayer = null
            }
            runCatching { mp.release() }
            syncWakeLockState()
            dispatchPlayerCallback {
                onPlayingStateChanged?.invoke(false)
                onError?.invoke("Android MediaPlayer failed to open source: ${t.message ?: t::class.simpleName}")
            }
        }
    }

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        val gen = ++sourceGeneration
        sourceUrl = url
        pendingSourceUrl = url
        mediaReadyFlag = url.isNotBlank()
        isPreparedFlag = false
        isPreparingFlag = false
        stopPositionPolling()
        currentPositionSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        nativeWidget.attributes["src"] = url
        videoWidthPx = 0
        videoHeightPx = 0
        hasDurationOverride = durationOverrideSeconds != null

        if (durationOverrideSeconds != null) {
            durationSeconds = durationOverrideSeconds
            dispatchPlayerCallback { onDurationChanged?.invoke(durationSeconds) }
        }

        AndroidViewHandle.runOnMainThread {
            ensureAndroidView()
            releaseMediaPlayerOnly()
            val tv = textureView ?: return@runOnMainThread
            runCatching {
                tv.visibility = if (url.isBlank()) View.INVISIBLE else View.VISIBLE
                nativeWidget.androidView?.requestLayout()
            }
            if (url.isNotBlank() && !url.startsWith("synth://")) {
                openMediaPlayerOnMainThread(tv.context, url, gen)
            } else {
                pendingSourceUrl = ""
            }
        }

        if (mediaReadyFlag) {
            dispatchPlayerCallback { onMediaReadyListener?.invoke() }
        }
        updateBufferedHorizon(currentPositionSeconds)
    }

    private fun extractTrackInfo(mp: MediaPlayer) {
        runCatching {
            val infos = mp.trackInfo ?: return@runCatching
            audioTracks.clear()
            subtitles.clear()
            var audioIdx = 0
            var subIdx = 0
            infos.forEachIndexed { index, track ->
                when (track.trackType) {
                    MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO -> {
                        audioTracks.add(
                            MediaTrackInfo(
                                id = "index:$index",
                                label = "Audio ${++audioIdx}",
                                language = track.language ?: "und",
                                isDefault = audioIdx == 1,
                                isSelected = audioIdx == 1
                            )
                        )
                    }
                    MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT,
                    MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE -> {
                        subtitles.add(
                            SubtitleTrackInfo(
                                id = "index:$index",
                                label = "Subtitle ${++subIdx}",
                                language = track.language ?: "en"
                            )
                        )
                    }
                }
            }
            if (audioTracks.isNotEmpty() || subtitles.isNotEmpty()) {
                dispatchPlayerCallback { onTracksChanged?.invoke() }
            }
        }
    }

    override fun play() {
        playing = true
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val tv = textureView
                tv?.visibility = View.VISIBLE
                val mp = activeMediaPlayer
                if (isPreparedFlag && mp != null) {
                    mp.start()
                    applyVolumeAndSpeedToMediaPlayer(mp)
                    attachVisualizerIfPossible()
                    startPositionPolling()
                } else if (sourceUrl.isNotBlank() && !sourceUrl.startsWith("synth://") && mp == null && !isPreparingFlag && tv != null) {
                    openMediaPlayerOnMainThread(tv.context, sourceUrl, sourceGeneration)
                }
            }
        }
        syncWakeLockState()
        dispatchPlayerCallback { onPlayingStateChanged?.invoke(true) }
        updateBufferedHorizon(currentPositionSeconds)
    }

    override fun pause() {
        playing = false
        AndroidViewHandle.runOnMainThread {
            runCatching {
                stopPositionPolling()
                val mp = activeMediaPlayer
                if (mp != null && isPreparedFlag) {
                    val posMs = runCatching { mp.currentPosition }.getOrDefault(-1)
                    if (posMs >= 0) {
                        currentPositionSeconds = posMs / 1000.0
                    }
                    if (runCatching { mp.isPlaying }.getOrDefault(false)) {
                        mp.pause()
                    }
                }
            }
        }
        syncWakeLockState()
        dispatchPlayerCallback { onPlayingStateChanged?.invoke(false) }
    }

    override fun stop() {
        playing = false
        currentPositionSeconds = 0.0
        AndroidViewHandle.runOnMainThread {
            runCatching {
                stopPositionPolling()
                val mp = activeMediaPlayer
                if (mp != null && isPreparedFlag) {
                    if (runCatching { mp.isPlaying }.getOrDefault(false)) {
                        mp.pause()
                    }
                    seekMediaPlayerMs(mp, 0L)
                }
            }
        }
        syncWakeLockState()
        dispatchPlayerCallback {
            onPlayingStateChanged?.invoke(false)
            onTimeUpdate?.invoke(0.0)
        }
    }

    override fun seekTo(seconds: Double) {
        currentPositionSeconds = seconds.coerceAtLeast(0.0)
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val mp = activeMediaPlayer
                if (mp != null && isPreparedFlag) {
                    seekMediaPlayerMs(mp, (currentPositionSeconds * 1000.0).roundToLong())
                }
            }
        }
        onTimeUpdate?.invoke(currentPositionSeconds)
        updateBufferedHorizon(currentPositionSeconds)
    }

    override fun getCurrentTimeSeconds(): Double {
        if (isPreparedFlag) {
            val nativePosMs = runCatching { activeMediaPlayer?.currentPosition }.getOrNull()
            if (nativePosMs != null && nativePosMs >= 0) {
                currentPositionSeconds = nativePosMs / 1000.0
            }
        }
        return currentPositionSeconds
    }

    override fun getDurationSeconds(): Double {
        if (isPreparedFlag) {
            val nativeDurMs = runCatching { activeMediaPlayer?.duration }.getOrNull()
            if (nativeDurMs != null && nativeDurMs > 0) {
                durationSeconds = nativeDurMs / 1000.0
            }
        }
        return durationSeconds
    }

    override fun isPlaying(): Boolean = playing

    override fun setPlaybackSpeed(speed: Float) {
        this.playbackSpeed = speed.coerceIn(0.25f, 4f)
        applyVolumeAndSpeedToMediaPlayer(activeMediaPlayer)
    }

    override fun getPlaybackSpeed(): Float = playbackSpeed

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolumeAndSpeedToMediaPlayer(activeMediaPlayer)
    }

    override fun getVolume(): Float = volume

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        applyVolumeAndSpeedToMediaPlayer(activeMediaPlayer)
    }

    override fun isMuted(): Boolean = muted

    override fun setResizeMode(mode: VideoResizeMode) {
        this.resizeMode = mode
        nativeWidget.attributes["resizeMode"] = mode.name
        AndroidViewHandle.runOnMainThread {
            runCatching {
                nativeWidget.androidView?.requestLayout()
            }
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeMode

    override fun getAudioTracks(): List<MediaTrackInfo> = audioTracks.toList()

    override fun selectAudioTrack(trackId: String): Boolean {
        val indexPrefix = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        for (i in audioTracks.indices) {
            val item = audioTracks[i]
            val selected = if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            audioTracks[i] = item.copy(isSelected = selected)
        }
        if (indexPrefix != null) {
            runCatching { activeMediaPlayer?.selectTrack(indexPrefix) }
        }
        onTracksChanged?.invoke()
        return true
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitles.toList()

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val id = "sub_${subtitles.size}"
        subtitles.add(SubtitleTrackInfo(id, label, language, externalUrl = vttOrSrtUrl, isSelected = activeSubtitleTrackId == id))
        onTracksChanged?.invoke()
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean {
        val indexPrefix = if (trackId != null && trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        activeSubtitleTrackId = if (indexPrefix != null) subtitles.getOrNull(indexPrefix)?.id else trackId
        for (i in subtitles.indices) {
            val item = subtitles[i]
            val selected = if (trackId == null) false else if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            subtitles[i] = item.copy(isSelected = selected)
        }
        if (indexPrefix != null) {
            runCatching { activeMediaPlayer?.selectTrack(indexPrefix) }
        }
        onTracksChanged?.invoke()
        return true
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        attachVisualizerIfPossible()
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdate = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChanged = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEnded = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { onBufferingChanged = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onError = listener }
    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { onPlayingStateChanged = listener }
    override fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) { onBufferingStateChanged = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        onBufferedRangesChanged = listener
    }
    override fun setOnTracksChanged(listener: (() -> Unit)?) { onTracksChanged = listener }
    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesState

    override fun setAntialiasing(antialiasing: Boolean) {
        antialiasingEnabled = antialiasing
        nativeWidget.attributes["antialiasing"] = antialiasing.toString()
    }

    override fun isAntialiasing(): Boolean = antialiasingEnabled

    override fun setKeepScreenOn(keepScreenOn: Boolean) {
        keepScreenOnState = keepScreenOn
        nativeWidget.attributes["keepScreenOn"] = keepScreenOn.toString()
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

    override fun dispose() {
        if (nativePlayerDisposed) return
        nativePlayerDisposed = true
        sourceGeneration++
        playing = false
        pendingSourceUrl = ""
        isPreparedFlag = false
        isPreparingFlag = false
        stopPositionPolling()
        releaseVisualizer()
        AndroidViewHandle.runOnMainThread {
            releaseMediaPlayerOnly()
            val tv = textureView
            val savedSt = retainedSurfaceTexture
            textureView = null
            runCatching { tv?.surfaceTextureListener = null }
            runCatching { videoSurface?.release() }
            videoSurface = null
            if (savedSt != null && tv?.surfaceTexture !== savedSt) {
                runCatching { savedSt.release() }
            }
            retainedSurfaceTexture = null
            captureBitmap?.let { bmp ->
                runCatching { bmp.recycle() }
            }
            captureBitmap = null
        }
        syncWakeLockState()
        MediaScreenWakeController.releaseToken(screenWakeToken)
        super.dispose()
    }

    override fun setLoop(loop: Boolean) {
        loopEnabled = loop
        activeMediaPlayer?.let { mp ->
            runCatching { mp.isLooping = loop }
        }
    }

    override fun isLoop(): Boolean = loopEnabled

    override fun enableOffscreenFrameCapture(enabled: Boolean) {
        offscreenFrameCaptureEnabled = enabled
        if (enabled) {
            AndroidViewHandle.runOnMainThread {
                textureView?.let { captureTextureViewFrame(it) }
            }
        }
    }

    override fun copyLatestVideoFrame(): ImageBuffer? {
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
        get() = frameVersionCounter.get()

    override fun getNaturalVideoWidth(): Int = videoWidthPx

    override fun getNaturalVideoHeight(): Int = videoHeightPx

    override fun getPresentedFrameCount(): Long = presentedFrameCounter.get()

    override fun setOnBeforeFramePresent(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    private fun captureTextureViewFrame(tv: TextureView) {
        if (!offscreenFrameCaptureEnabled || nativePlayerDisposed || !tv.isAvailable) return
        val w = (if (videoWidthPx > 0) videoWidthPx else tv.width).coerceAtLeast(1)
        val h = (if (videoHeightPx > 0) videoHeightPx else tv.height).coerceAtLeast(1)
        synchronized(frameRingLock) {
            val existingBmp = captureBitmap
            val bmp = if (existingBmp != null && existingBmp.width == w && existingBmp.height == h && !existingBmp.isRecycled) {
                existingBmp
            } else {
                existingBmp?.let { runCatching { it.recycle() } }
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { captureBitmap = it }
            }
            val resultBmp = tv.getBitmap(bmp) ?: return
            val pixelCount = w * h
            if (captureArgbPixels.size != pixelCount) {
                captureArgbPixels = IntArray(pixelCount)
            }
            resultBmp.getPixels(captureArgbPixels, 0, w, 0, 0, w, h)
            val latest = offscreenRgbaRing.latestPresentedSlot()
            if (latest.width != w || latest.height != h) {
                offscreenRgbaRing = SystemMediaFrameRingBuffer(width = w, height = h, capacity = 3)
            }
            val dstBytes = offscreenRgbaRing.acquireWriteSlot().bytes
            for (p in 0 until pixelCount) {
                val argb = captureArgbPixels[p]
                val base = p shl 2
                dstBytes[base] = ((argb ushr 16) and 0xFF).toByte()
                dstBytes[base + 1] = ((argb ushr 8) and 0xFF).toByte()
                dstBytes[base + 2] = (argb and 0xFF).toByte()
                dstBytes[base + 3] = ((argb ushr 24) and 0xFF).toByte()
            }
        }
        val frameIdx = presentedFrameCounter.incrementAndGet()
        frameVersionCounter.incrementAndGet()
        onBeforeFramePresentListener?.invoke(frameIdx, (currentPositionSeconds * 1000.0).coerceAtLeast(0.0))
    }

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
        if (mediaReadyFlag) dispatchPlayerCallback { listener?.invoke() }
    }

    override fun isMediaReady(): Boolean = mediaReadyFlag

    private fun updateBufferedHorizon(currentSec: Double) {
        val bufEnd = if (durationSeconds > 0.0) minOf(durationSeconds, currentSec + 15.0) else currentSec + 15.0
        if (bufEnd > 0.0) {
            bufferedRangesState = listOf(0.0 to bufEnd)
            val ranges = bufferedRangesState
            dispatchPlayerCallback { onBufferedRangesChanged?.invoke(ranges) }
        }
    }
}
