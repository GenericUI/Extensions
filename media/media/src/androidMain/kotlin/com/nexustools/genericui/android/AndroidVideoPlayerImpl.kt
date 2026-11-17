package com.nexustools.genericui.android

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.VideoView
import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl

/**
 * Android `android.widget.VideoView` + `android.media.MediaPlayer` + `Visualizer` + PiP
 * implementation of [VideoPlayerNativeImpl].
 */
class AndroidVideoPlayerImpl : AndroidBaseNativeImpl("android.widget.VideoView", "video-player"), VideoPlayerNativeImpl<AndroidViewHandle> {
    private var sourceUrl: String = ""
    private var currentPositionSeconds: Double = 0.0
    private var durationSeconds: Double = 0.0
    private var playing: Boolean = false
    private var volume: Float = 1f
    private var muted: Boolean = false
    private var playbackSpeed: Float = 1f
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private var antialiasingEnabled: Boolean = true
    private var keepScreenOnState: Boolean = true
    private var screenWakeToken: Long = MediaScreenWakeController.allocateToken()

    @Volatile
    private var nativePlayerDisposed: Boolean = false
    private var mediaReadyFlag: Boolean = false
    private var pipActive: Boolean = false
    private var bufferedRangesState: List<Pair<Double, Double>> = emptyList()
    private val audioTracks = mutableListOf<MediaTrackInfo>()
    private val subtitles = mutableListOf<SubtitleTrackInfo>()
    private var activeSubtitleTrackId: String? = null
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null

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

    override fun createDefaultAndroidView(context: Context): View? = VideoView(context)

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        nativeWidget.attributes["keepScreenOn"] = "true"
    }

    private fun unwrapActivity(context: Context?): Activity? {
        var current: Context? = context
        var depth = 0
        while (current != null && depth < 16) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return null
    }

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && playing
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
        AndroidViewHandle.runOnMainThread {
            runCatching {
                nativeWidget.androidView?.keepScreenOn = active
            }
        }
    }

    private fun applyVolumeAndSpeedToMediaPlayer(mp: MediaPlayer?) {
        val player = mp ?: return
        val effectiveVol = if (muted) 0f else volume
        runCatching { player.setVolume(effectiveVol, effectiveVol) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val params = player.playbackParams ?: PlaybackParams()
                params.speed = playbackSpeed
                player.playbackParams = params
            }
        }
    }

    private fun attachVisualizerIfPossible() {
        val activeAnalyzer = attachedAnalyzer ?: run {
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

    private fun queryAndroidDecoderSupportLevel(mimeTypes: Set<String>): CodecSupportLevel {
        return runCatching {
            val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos ?: return CodecSupportLevel.UNSUPPORTED
            var foundSoftware = false
            for (info in codecInfos) {
                if (info.isEncoder) continue
                val supported = info.supportedTypes ?: continue
                val matches = supported.any { supportedMime ->
                    mimeTypes.any { targetMime -> supportedMime.equals(targetMime, ignoreCase = true) }
                }
                if (matches) {
                    val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        val lowerName = info.name.lowercase()
                        !lowerName.startsWith("omx.google.") &&
                            !lowerName.startsWith("c2.android.") &&
                            !lowerName.contains(".sw.")
                    }
                    if (isHardware) {
                        return CodecSupportLevel.HARDWARE_ACCELERATED
                    }
                    foundSoftware = true
                }
            }
            if (foundSoftware) CodecSupportLevel.SOFTWARE_ONLY else CodecSupportLevel.UNSUPPORTED
        }.getOrDefault(CodecSupportLevel.UNSUPPORTED)
    }

    override fun isVideoCodecSupported(codec: VideoCodec): Boolean =
        queryAndroidDecoderSupportLevel(codec.mimeTypes) != CodecSupportLevel.UNSUPPORTED

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean =
        queryAndroidDecoderSupportLevel(codec.mimeTypes) != CodecSupportLevel.UNSUPPORTED

    override fun getSupportedVideoCodecs(): Set<VideoCodec> =
        VideoCodec.entries.filterTo(linkedSetOf()) { isVideoCodecSupported(it) }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        AudioCodec.entries.filterTo(linkedSetOf()) { isAudioCodecSupported(it) }

    override fun getMediaCapabilities(): MediaCapabilitiesReport {
        val videoMap = linkedMapOf<VideoCodec, CodecSupportLevel>()
        for (codec in VideoCodec.entries) {
            val level = queryAndroidDecoderSupportLevel(codec.mimeTypes)
            if (level != CodecSupportLevel.UNSUPPORTED) {
                videoMap[codec] = level
            }
        }
        val audioMap = linkedMapOf<AudioCodec, CodecSupportLevel>()
        for (codec in AudioCodec.entries) {
            val level = queryAndroidDecoderSupportLevel(codec.mimeTypes)
            if (level != CodecSupportLevel.UNSUPPORTED) {
                audioMap[codec] = level
            }
        }
        return MediaCapabilitiesReport(
            videoCodecs = videoMap,
            audioCodecs = audioMap,
            containers = setOf(MediaContainer.MP4, MediaContainer.MKV, MediaContainer.WEBM, MediaContainer.HLS, MediaContainer.DASH),
            supportsHdr10 = VideoCodec.HEVC in videoMap || VideoCodec.VP9 in videoMap || VideoCodec.AV1 in videoMap,
            supportsHlg = VideoCodec.HEVC in videoMap || VideoCodec.VP9 in videoMap || VideoCodec.AV1 in videoMap,
            supportsDolbyVision = queryAndroidDecoderSupportLevel(setOf("video/dolby-vision")) != CodecSupportLevel.UNSUPPORTED,
            supportsSurround51 = AudioCodec.AC3 in audioMap || AudioCodec.EAC3 in audioMap || AudioCodec.AAC in audioMap,
            supportsSurround71 = AudioCodec.EAC3 in audioMap || AudioCodec.TRUEHD in audioMap || AudioCodec.DTS_HD in audioMap,
            supportsAudioTrackSelection = true,
            supportsSubtitleTrackSelection = true,
            hardwareAccelerationBackend = "Android MediaCodecList / VideoView"
        )
    }

    override fun supportsAudioTrackSelection(): Boolean = true
    override fun supportsSubtitleTrackSelection(): Boolean = true

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        sourceUrl = url
        mediaReadyFlag = url.isNotBlank()
        currentPositionSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        nativeWidget.attributes["src"] = url
        releaseVisualizer()
        activeMediaPlayer = null

        if (durationOverrideSeconds != null) {
            durationSeconds = durationOverrideSeconds
            onDurationChanged?.invoke(durationSeconds)
        }

        val view = ensureAndroidView() as? VideoView
        if (view != null && url.isNotBlank() && !url.startsWith("synth://")) {
            AndroidViewHandle.runOnMainThread {
                runCatching {
                    view.setOnPreparedListener { mp ->
                        activeMediaPlayer = mp
                        val durMs = runCatching { mp.duration }.getOrDefault(0)
                        if (durMs > 0 && durationOverrideSeconds == null) {
                            durationSeconds = durMs / 1000.0
                            onDurationChanged?.invoke(durationSeconds)
                        }
                        if (currentPositionSeconds > 0.0) {
                            view.seekTo((currentPositionSeconds * 1000.0).toInt())
                        }
                        applyVolumeAndSpeedToMediaPlayer(mp)
                        extractTrackInfo(mp)
                        attachVisualizerIfPossible()
                        mediaReadyFlag = true
                        onMediaReadyListener?.invoke()
                        if (playing) {
                            view.start()
                        }
                    }
                    view.setOnCompletionListener {
                        playing = false
                        syncWakeLockState()
                        onPlayingStateChanged?.invoke(false)
                        onEnded?.invoke()
                    }
                    view.setOnErrorListener { _, what, extra ->
                        playing = false
                        syncWakeLockState()
                        onPlayingStateChanged?.invoke(false)
                        onError?.invoke("Android VideoView error ($what, $extra)")
                        true
                    }
                    view.setOnInfoListener { _, what, _ ->
                        when (what) {
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                                onBufferingChanged?.invoke(true)
                                onBufferingStateChanged?.invoke(true)
                            }
                            MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                                onBufferingChanged?.invoke(false)
                                onBufferingStateChanged?.invoke(false)
                            }
                        }
                        false
                    }
                    if (url.startsWith("/")) {
                        view.setVideoPath(url)
                    } else {
                        view.setVideoURI(Uri.parse(url))
                    }
                }
            }
        }

        if (mediaReadyFlag) {
            onMediaReadyListener?.invoke()
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
                onTracksChanged?.invoke()
            }
        }
    }

    override fun play() {
        playing = true
        AndroidViewHandle.runOnMainThread {
            runCatching {
                (nativeWidget.androidView as? VideoView)?.start()
                attachVisualizerIfPossible()
            }
        }
        syncWakeLockState()
        onPlayingStateChanged?.invoke(true)
        updateBufferedHorizon(currentPositionSeconds)
    }

    override fun pause() {
        playing = false
        AndroidViewHandle.runOnMainThread {
            runCatching {
                (nativeWidget.androidView as? VideoView)?.pause()
            }
        }
        syncWakeLockState()
        onPlayingStateChanged?.invoke(false)
    }

    override fun stop() {
        playing = false
        currentPositionSeconds = 0.0
        AndroidViewHandle.runOnMainThread {
            runCatching {
                (nativeWidget.androidView as? VideoView)?.apply {
                    pause()
                    seekTo(0)
                }
            }
        }
        syncWakeLockState()
        onPlayingStateChanged?.invoke(false)
        onTimeUpdate?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        currentPositionSeconds = seconds.coerceAtLeast(0.0)
        AndroidViewHandle.runOnMainThread {
            runCatching {
                (nativeWidget.androidView as? VideoView)?.seekTo((currentPositionSeconds * 1000.0).toInt())
            }
        }
        onTimeUpdate?.invoke(currentPositionSeconds)
        updateBufferedHorizon(currentPositionSeconds)
    }

    override fun getCurrentTimeSeconds(): Double {
        val nativePosMs = runCatching { (nativeWidget.androidView as? VideoView)?.currentPosition }.getOrNull()
        if (nativePosMs != null && nativePosMs > 0) {
            currentPositionSeconds = nativePosMs / 1000.0
        }
        return currentPositionSeconds
    }

    override fun getDurationSeconds(): Double {
        val nativeDurMs = runCatching { (nativeWidget.androidView as? VideoView)?.duration }.getOrNull()
        if (nativeDurMs != null && nativeDurMs > 0) {
            durationSeconds = nativeDurMs / 1000.0
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

    override fun supportsPictureInPicture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val ctx = AndroidViewHandle.activeContext ?: return true
        return runCatching {
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }.getOrDefault(true)
    }

    override fun enterPictureInPicture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        pipActive = true
        val activity = unwrapActivity(AndroidViewHandle.activeContext) ?: return true
        return runCatching {
            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }.getOrDefault(false)
    }

    override fun exitPictureInPicture() {
        pipActive = false
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
        playing = false
        releaseVisualizer()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                (nativeWidget.androidView as? VideoView)?.stopPlayback()
            }
        }
        activeMediaPlayer = null
        syncWakeLockState()
        MediaScreenWakeController.releaseToken(screenWakeToken)
        super.dispose()
    }

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
        if (mediaReadyFlag) listener?.invoke()
    }

    override fun isMediaReady(): Boolean = mediaReadyFlag

    private fun updateBufferedHorizon(currentSec: Double) {
        val bufEnd = if (durationSeconds > 0.0) minOf(durationSeconds, currentSec + 15.0) else currentSec + 15.0
        if (bufEnd > 0.0) {
            bufferedRangesState = listOf(0.0 to bufEnd)
            onBufferedRangesChanged?.invoke(bufferedRangesState)
        }
    }
}
